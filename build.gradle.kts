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
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

buildscript {
  dependencies {
    val kotlinVersion = System.getenv("MOSHI_KOTLIN_VERSION")
      ?: libs.versions.kotlin.get()
    val kspVersion = System.getenv("MOSHI_KSP_VERSION")
      ?: libs.versions.ksp.get()
    classpath(kotlin("gradle-plugin", version = kotlinVersion))
    classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspVersion")
    // https://github.com/melix/japicmp-gradle-plugin/issues/36
    classpath("com.google.guava:guava:28.2-jre")
  }
}

plugins {
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.japicmp) apply false
}

allprojects {
  group = "com.squareup.moshi"
  version = "1.14.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}

spotless {
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    indentWithSpaces(2)
    endWithNewline()
  }
  val configureCommonJavaFormat: JavaExtension.() -> Unit = {
    googleJavaFormat(libs.versions.gjf.get())
  }
  java {
    configureCommonJavaFormat()
    target("**/*.java")
    targetExclude("**/build/**",)
  }
  kotlin {
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2"))
    target("**/*.kt")
    trimTrailingWhitespace()
    endWithNewline()
    targetExclude("**/Dependencies.kt", "**/build/**")
  }
  kotlinGradle {
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2"))
    target("**/*.gradle.kts")
    trimTrailingWhitespace()
    endWithNewline()
  }
}

subprojects {
  // Apply with "java" instead of just "java-library" so kotlin projects get it too
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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
        // TODO re-enable when no longer supporting multiple kotlin versions
//        @Suppress("SuspiciousCollectionReassignment")
//        freeCompilerArgs += listOf("-progressive")
        jvmTarget = libs.versions.jvmTarget.get()
      }
    }

    configure<KotlinProjectExtension> {
      if (project.name != "examples") {
        explicitApi()
      }
    }
  }
}

allprojects {
  tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(8)
      perPackageOption {
        matchingRegex.set("com\\.squareup.moshi\\.internal.*")
        suppress.set(true)
      }
    }
    if (name == "dokkaHtml") {
      outputDirectory.set(rootDir.resolve("docs/1.x"))
      dokkaSourceSets.configureEach {
        skipDeprecated.set(true)
        externalDocumentationLink {
          url.set(URL("https://square.github.io/okio/2.x/okio/"))
        }
      }
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.DEFAULT)
      signAllPublications()
      pom {
        description.set("A modern JSON API for Android and Java")
        name.set(project.name)
        url.set("https://github.com/square/moshi/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          url.set("https://github.com/square/moshi/")
          connection.set("scm:git:git://github.com/square/moshi.git")
          developerConnection.set("scm:git:ssh://git@github.com/square/moshi.git")
        }
        developers {
          developer {
            id.set("square")
            name.set("Square, Inc.")
          }
        }
      }
    }
  }
}
