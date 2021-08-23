/*
 * Copyright (C) 2020 Square, Inc.
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

import com.diffplug.gradle.spotless.JavaExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

buildscript {
  dependencies {
    classpath(kotlin("gradle-plugin", version = Dependencies.Kotlin.version))
    // https://github.com/melix/japicmp-gradle-plugin/issues/36
    classpath("com.google.guava:guava:28.2-jre")
  }
}

plugins {
  id("com.vanniktech.maven.publish") version "0.14.2" apply false
  id("org.jetbrains.dokka") version "1.4.32" apply false
  id("com.diffplug.spotless") version "5.12.4"
  id("me.champeau.gradle.japicmp") version "0.2.9" apply false
}

spotless {
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    indentWithSpaces(2)
    endWithNewline()
  }
  val externalJavaFiles = arrayOf(
    "**/ClassFactory.java",
    "**/Iso8601Utils.java",
    "**/JsonReader.java",
    "**/JsonReaderPathTest.java",
    "**/JsonReaderTest.java",
    "**/JsonScope.java",
    "**/JsonUtf8Reader.java",
    "**/JsonUtf8ReaderPathTest.java",
    "**/JsonUtf8ReaderTest.java",
    "**/JsonUtf8ReaderTest.java",
    "**/JsonUtf8Writer.java",
    "**/JsonUtf8WriterTest.java",
    "**/JsonWriter.java",
    "**/JsonWriterPathTest.java",
    "**/JsonWriterTest.java",
    "**/LinkedHashTreeMap.java",
    "**/LinkedHashTreeMapTest.java",
    "**/PolymorphicJsonAdapterFactory.java",
    "**/RecursiveTypesResolveTest.java",
    "**/Types.java",
    "**/TypesTest.java"
  )
  val configureCommonJavaFormat: JavaExtension.() -> Unit = {
    googleJavaFormat("1.11.0")
  }
  java {
    configureCommonJavaFormat()
    target("**/*.java")
    targetExclude(
      "**/spotless.java",
      "**/build/**",
      *externalJavaFiles
    )
    licenseHeaderFile("spotless/spotless.java")
  }
  format("externalJava", JavaExtension::class.java) {
    // These don't use our spotless config for header files since we don't want to overwrite the
    // existing copyright headers.
    configureCommonJavaFormat()
    target(*externalJavaFiles)
  }
  kotlin {
    ktlint(Dependencies.ktlintVersion).userData(mapOf("indent_size" to "2"))
    target("**/*.kt")
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kt")
      .updateYearWithLatest(false)
    targetExclude("**/Dependencies.kt", "**/spotless.kt", "**/build/**")
  }
  kotlinGradle {
    ktlint(Dependencies.ktlintVersion).userData(mapOf("indent_size" to "2"))
    target("**/*.gradle.kts")
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kts", "(import|plugins|buildscript|dependencies|pluginManagement)")
  }
}

subprojects {
  repositories {
    mavenCentral()
    // Required for Dokka
    exclusiveContent {
      forRepository {
        maven {
          name = "JCenter"
          setUrl("https://jcenter.bintray.com/")
        }
      }
      filter {
        includeModule("org.jetbrains.kotlinx", "kotlinx-html-jvm")
        includeGroup("org.jetbrains.dokka")
        includeModule("org.jetbrains", "markdown")
      }
    }
  }

  // Apply with "java" instead of just "java-library" so kotlin projects get it too
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
      }
    }
    if (project.name != "records-tests") {
      tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
      }
    }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions {
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-progressive")
        jvmTarget = "1.8"
      }
    }

    configure<KotlinProjectExtension> {
      if (project.name != "examples") {
        explicitApi()
      }
    }
  }

  // Configure publishing
  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    // Configure automatic-module-name, but only for published modules
    @Suppress("UnstableApiUsage")
    val automaticModuleName = providers.gradleProperty("AUTOMATIC_MODULE_NAME")
      .forUseAtConfigurationTime()
    if (automaticModuleName.isPresent) {
      val name = automaticModuleName.get()
      tasks.withType<Jar>().configureEach {
        manifest {
          attributes("Automatic-Module-Name" to name)
        }
      }
    }

    if (name != "codegen" && pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
      apply(plugin = "org.jetbrains.dokka")
      tasks.named<DokkaTask>("dokkaHtml") {
        outputDirectory.set(rootDir.resolve("docs/1.x"))
        dokkaSourceSets.configureEach {
          skipDeprecated.set(true)
          externalDocumentationLink {
            url.set(URL("https://square.github.io/okio/2.x/okio/"))
          }
        }
      }
    }
  }
}
