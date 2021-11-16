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
package com.squareup.moshi.kotlin.codegen.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.codegen.api.AdapterGenerator
import com.squareup.moshi.kotlin.codegen.api.Options.OPTION_GENERATED
import com.squareup.moshi.kotlin.codegen.api.Options.OPTION_GENERATE_PROGUARD_RULES
import com.squareup.moshi.kotlin.codegen.api.Options.POSSIBLE_GENERATED_NAMES
import com.squareup.moshi.kotlin.codegen.api.ProguardConfig
import com.squareup.moshi.kotlin.codegen.api.PropertyGenerator
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@AutoService(SymbolProcessorProvider::class)
public class JsonClassSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return JsonClassSymbolProcessor(environment)
  }
}

private class JsonClassSymbolProcessor(
  environment: SymbolProcessorEnvironment
) : SymbolProcessor {

  private companion object {
    val JSON_CLASS_NAME = JsonClass::class.qualifiedName!!
  }

  private val codeGenerator = environment.codeGenerator
  private val logger = environment.logger
  private val generatedOption = environment.options[OPTION_GENERATED]?.also {
    logger.check(it in POSSIBLE_GENERATED_NAMES) {
      "Invalid option value for $OPTION_GENERATED. Found $it, allowable values are ${POSSIBLE_GENERATED_NAMES.keys}."
    }
  }
  private val generateProguardRules = environment.options[OPTION_GENERATE_PROGUARD_RULES]?.toBooleanStrictOrNull() ?: true

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val generatedAnnotation = generatedOption?.let {
      val annotationType = resolver.getClassDeclarationByName(resolver.getKSNameFromString(it))
        ?: run {
          logger.error("Generated annotation type doesn't exist: $it")
          return emptyList()
        }
      AnnotationSpec.builder(annotationType.toClassName())
        .addMember("value = [%S]", JsonClassSymbolProcessor::class.java.canonicalName)
        .addMember("comments = %S", "https://github.com/square/moshi")
        .build()
    }

    resolver.getSymbolsWithAnnotation(JSON_CLASS_NAME)
      .asSequence()
      .forEach { type ->
        // For the smart cast
        if (type !is KSDeclaration) {
          logger.error("@JsonClass can't be applied to $type: must be a Kotlin class", type)
          return@forEach
        }

        val jsonClassAnnotation = type.findAnnotationWithType<JsonClass>() ?: return@forEach

        val generator = jsonClassAnnotation.generator

        if (generator.isNotEmpty()) return@forEach

        if (!jsonClassAnnotation.generateAdapter) return@forEach

        val originatingFile = type.containingFile!!
        val adapterGenerator = adapterGenerator(logger, resolver, type) ?: return emptyList()
        try {
          val preparedAdapter = adapterGenerator
            .prepare(generateProguardRules) { spec ->
              spec.toBuilder()
                .apply {
                  generatedAnnotation?.let(::addAnnotation)
                }
                .addOriginatingKSFile(originatingFile)
                .build()
            }
          preparedAdapter.spec.writeTo(codeGenerator, aggregating = false)
          preparedAdapter.proguardConfig?.writeTo(codeGenerator, originatingFile)
        } catch (e: Exception) {
          logger.error(
            "Error preparing ${type.simpleName.asString()}: ${e.stackTrace.joinToString("\n")}"
          )
        }
      }
    return emptyList()
  }

  private fun adapterGenerator(
    logger: KSPLogger,
    resolver: Resolver,
    originalType: KSDeclaration,
  ): AdapterGenerator? {
    val type = targetType(originalType, resolver, logger) ?: return null

    val properties = mutableMapOf<String, PropertyGenerator>()
    for (property in type.properties.values) {
      val generator = property.generator(logger, resolver, originalType)
      if (generator != null) {
        properties[property.name] = generator
      }
    }

    for ((name, parameter) in type.constructor.parameters) {
      if (type.properties[parameter.name] == null && !parameter.hasDefault) {
        // TODO would be nice if we could pass the parameter node directly?
        logger.error("No property for required constructor parameter $name", originalType)
        return null
      }
    }

    // Sort properties so that those with constructor parameters come first.
    val sortedProperties = properties.values.sortedBy {
      if (it.hasConstructorParameter) {
        it.target.parameterIndex
      } else {
        Integer.MAX_VALUE
      }
    }

    return AdapterGenerator(type, sortedProperties)
  }
}

/** Writes this config to a [codeGenerator]. */
private fun ProguardConfig.writeTo(codeGenerator: CodeGenerator, originatingKSFile: KSFile) {
  val file = codeGenerator.createNewFile(
    dependencies = Dependencies(aggregating = false, originatingKSFile),
    packageName = "",
    fileName = outputFilePathWithoutExtension(targetClass.canonicalName),
    extensionName = "pro"
  )
  // Don't use writeTo(file) because that tries to handle directories under the hood
  OutputStreamWriter(file, StandardCharsets.UTF_8)
    .use(::writeTo)
}
