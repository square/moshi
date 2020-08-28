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

object Dependencies {

  const val asm = "org.ow2.asm:asm:7.1"
  const val jsr305 = "com.google.code.findbugs:jsr305:3.0.2"
  const val ktlintVersion = "0.38.0"
  const val okio = "com.squareup.okio:okio:1.16.0"
  const val okio2 = "com.squareup.okio:okio:2.1.0"

  object AutoService {
    private const val version = "1.0-rc7"
    const val annotations = "com.google.auto.service:auto-service-annotations:$version"
    const val processor = "com.google.auto.service:auto-service:$version"
  }

  object Incap {
    private const val version = "0.3"
    const val annotations = "net.ltgt.gradle.incap:incap:$version"
    const val processor = "net.ltgt.gradle.incap:incap-processor:$version"
  }

  object Kotlin {
    const val version = "1.4.0"
    const val metadata = "org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0"
  }

  object KotlinPoet {
    private const val version = "1.6.0"
    const val kotlinPoet = "com.squareup:kotlinpoet:$version"
    const val metadata = "com.squareup:kotlinpoet-metadata-specs:$version"
    const val metadataSpecs = "com.squareup:kotlinpoet-metadata-specs:$version"
    const val elementsClassInspector = "com.squareup:kotlinpoet-classinspector-elements:$version"
  }

  object Testing {
    const val assertj = "org.assertj:assertj-core:3.11.1"
    const val compileTesting = "com.github.tschuchortdev:kotlin-compile-testing:1.2.8"
    const val junit = "junit:junit:4.12"
    const val truth = "com.google.truth:truth:1.0"
  }
}
