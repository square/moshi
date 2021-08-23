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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
}

tasks.withType<Test>().configureEach {
  // ExtendsPlatformClassWithProtectedField tests a case where we set a protected ByteArrayOutputStream.buf field
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-Werror",
      "-Xopt-in=kotlin.ExperimentalStdlibApi"
    )
  }
}

dependencies {
  kaptTest(project(":kotlin:codegen"))
  testImplementation(project(":moshi"))
  testImplementation(project(":kotlin:reflect"))
  testImplementation(kotlin("reflect"))
  testImplementation(libs.junit)
  testImplementation(libs.assertj)
  testImplementation(libs.truth)
}
