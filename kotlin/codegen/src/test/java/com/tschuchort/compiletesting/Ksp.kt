/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tschuchort.compiletesting

import com.google.devtools.ksp.AbstractKotlinSymbolProcessingExtension
import com.google.devtools.ksp.KspOptions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.com.intellij.core.CoreApplicationEnvironment
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.psi.PsiTreeChangeAdapter
import org.jetbrains.kotlin.com.intellij.psi.PsiTreeChangeListener
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.File

/**
 * The list of symbol processors for the kotlin compilation.
 * https://goo.gle/ksp
 */
var KotlinCompilation.symbolProcessorProviders: List<SymbolProcessorProvider>
  get() = getKspRegistrar().providers
  set(value) {
    val registrar = getKspRegistrar()
    registrar.providers = value
  }

/**
 * The directory where generated KSP sources are written
 */
val KotlinCompilation.kspSourcesDir: File
  get() = kspWorkingDir.resolve("sources")

/**
 * Arbitrary arguments to be passed to ksp
 */
var KotlinCompilation.kspArgs: MutableMap<String, String>
  get() = getKspRegistrar().options
  set(value) {
    val registrar = getKspRegistrar()
    registrar.options = value
  }

/**
 * Controls for enabling incremental processing in KSP.
 */
var KotlinCompilation.kspIncremental: Boolean
  get() = getKspRegistrar().incremental
  set(value) {
    val registrar = getKspRegistrar()
    registrar.incremental = value
  }

/**
 * Controls for enabling incremental processing logs in KSP.
 */
var KotlinCompilation.kspIncrementalLog: Boolean
  get() = getKspRegistrar().incrementalLog
  set(value) {
    val registrar = getKspRegistrar()
    registrar.incrementalLog = value
  }

/**
 * Controls for enabling all warnings as errors in KSP.
 */
var KotlinCompilation.kspAllWarningsAsErrors: Boolean
  get() = getKspRegistrar().allWarningsAsErrors
  set(value) {
    val registrar = getKspRegistrar()
    registrar.allWarningsAsErrors = value
  }

private val KotlinCompilation.kspJavaSourceDir: File
  get() = kspSourcesDir.resolve("java")

private val KotlinCompilation.kspKotlinSourceDir: File
  get() = kspSourcesDir.resolve("kotlin")

private val KotlinCompilation.kspResources: File
  get() = kspSourcesDir.resolve("resources")

/**
 * The working directory for KSP
 */
private val KotlinCompilation.kspWorkingDir: File
  get() = workingDir.resolve("ksp")

/**
 * The directory where compiled KSP classes are written
 */
// TODO this seems to be ignored by KSP and it is putting classes into regular classes directory
//  but we still need to provide it in the KSP options builder as it is required
//  once it works, we should make the property public.
private val KotlinCompilation.kspClassesDir: File
  get() = kspWorkingDir.resolve("classes")

/**
 * The directory where compiled KSP caches are written
 */
private val KotlinCompilation.kspCachesDir: File
  get() = kspWorkingDir.resolve("caches")

/**
 * Custom subclass of [AbstractKotlinSymbolProcessingExtension] where processors are pre-defined instead of being
 * loaded via ServiceLocator.
 */
private class KspTestExtension(
  options: KspOptions,
  processorProviders: List<SymbolProcessorProvider>,
  logger: KSPLogger
) : AbstractKotlinSymbolProcessingExtension(
  options = options,
  logger = logger,
  testMode = false
) {
  private val loadedProviders = processorProviders

  override fun loadProviders() = loadedProviders
}

/**
 * Registers the [KspTestExtension] to load the given list of processors.
 */
private class KspCompileTestingComponentRegistrar(
  private val compilation: KotlinCompilation
) : ComponentRegistrar {
  var providers = emptyList<SymbolProcessorProvider>()

  var options: MutableMap<String, String> = mutableMapOf()

  var incremental: Boolean = false
  var incrementalLog: Boolean = false
  var allWarningsAsErrors: Boolean = false

  override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
    if (providers.isEmpty()) {
      return
    }
    val options = KspOptions.Builder().apply {
      this.projectBaseDir = compilation.kspWorkingDir

      this.processingOptions.putAll(compilation.kspArgs)

      this.incremental = this@KspCompileTestingComponentRegistrar.incremental
      this.incrementalLog = this@KspCompileTestingComponentRegistrar.incrementalLog
      this.allWarningsAsErrors = this@KspCompileTestingComponentRegistrar.allWarningsAsErrors

      this.cachesDir = compilation.kspCachesDir.also {
        it.deleteRecursively()
        it.mkdirs()
      }
      this.kspOutputDir = compilation.kspSourcesDir.also {
        it.deleteRecursively()
        it.mkdirs()
      }
      this.classOutputDir = compilation.kspClassesDir.also {
        it.deleteRecursively()
        it.mkdirs()
      }
      this.javaOutputDir = compilation.kspJavaSourceDir.also {
        it.deleteRecursively()
        it.mkdirs()
      }
      this.kotlinOutputDir = compilation.kspKotlinSourceDir.also {
        it.deleteRecursively()
        it.mkdirs()
      }
      this.resourceOutputDir = compilation.kspResources.also {
        it.deleteRecursively()
        it.mkdirs()
      }
      configuration[CLIConfigurationKeys.CONTENT_ROOTS]
        ?.filterIsInstance<JavaSourceRoot>()
        ?.forEach {
          this.javaSourceRoots.add(it.file)
        }
    }.build()

    // Temporary until friend-paths is fully supported https://youtrack.jetbrains.com/issue/KT-34102
    @Suppress("invisible_member")
    val messageCollectorBasedKSPLogger = MessageCollectorBasedKSPLogger(
      PrintingMessageCollector(
        compilation.internalMessageStreamAccess,
        MessageRenderer.GRADLE_STYLE,
        compilation.verbose
      ),
      allWarningsAsErrors
    )
    val registrar = KspTestExtension(options, providers, messageCollectorBasedKSPLogger)
    AnalysisHandlerExtension.registerExtension(project, registrar)
    // Dummy extension point; Required by dropPsiCaches().
    CoreApplicationEnvironment.registerExtensionPoint(project.extensionArea, PsiTreeChangeListener.EP.name, PsiTreeChangeAdapter::class.java)
  }
}

/**
 * Gets the test registrar from the plugin list or adds if it does not exist.
 */
private fun KotlinCompilation.getKspRegistrar(): KspCompileTestingComponentRegistrar {
  compilerPlugins.firstIsInstanceOrNull<KspCompileTestingComponentRegistrar>()?.let {
    return it
  }
  val kspRegistrar = KspCompileTestingComponentRegistrar(this)
  compilerPlugins = compilerPlugins + kspRegistrar
  return kspRegistrar
}
