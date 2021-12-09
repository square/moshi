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

import Build_gradle.TestMode.KAPT
import Build_gradle.TestMode.KSP
import Build_gradle.TestMode.REFLECT
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt") apply false
  id("com.google.devtools.ksp") apply false
}

enum class TestMode {
  REFLECT, KAPT, KSP
}

val testMode = findProperty("kotlinTestMode")?.toString()
  ?.let(TestMode::valueOf)
  ?: KSP

when (testMode) {
  REFLECT -> {
    // Default to KSP. This is a CI-only thing
    apply(plugin = "com.google.devtools.ksp")
  }
  KAPT -> {
    apply(plugin = "org.jetbrains.kotlin.kapt")
  }
  KSP -> {
    apply(plugin = "com.google.devtools.ksp")
  }
}

tasks.withType<Test>().configureEach {
  // ExtendsPlatformClassWithProtectedField tests a case where we set a protected ByteArrayOutputStream.buf field
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
}

val useWError = findProperty("kotlinLanguageVersion")?.toString()
  ?.startsWith("1.5")
  ?: false
tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    allWarningsAsErrors = useWError
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.ExperimentalStdlibApi"
    )
  }
}

dependencies {
  when (testMode) {
    REFLECT -> {
      // Default to KSP in this case, this is a CI-only thing
      "kspTest"(project(":kotlin:moshi-kotlin-codegen"))
    }
    KAPT -> {
      "kaptTest"(project(":kotlin:moshi-kotlin-codegen"))
    }
    KSP -> {
      "kspTest"(project(":kotlin:moshi-kotlin-codegen"))
    }
  }
  testImplementation(project(":moshi"))
  testImplementation(project(":kotlin:moshi-kotlin"))
  testImplementation(project(":kotlin:tests:extra-moshi-test-module"))
  testImplementation(kotlin("reflect"))
  testImplementation(libs.junit)
  testImplementation(libs.assertj)
  testImplementation(libs.truth)
}
