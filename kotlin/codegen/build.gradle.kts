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

import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.vanniktech.maven.publish")
  alias(libs.plugins.mavenShadow)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.RequiresOptIn",
      "-Xopt-in=com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview",
    )
  }
}

tasks.withType<Test>().configureEach {
  // For kapt to work with kotlin-compile-testing
  jvmArgs(
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
  )
}

val shade: Configuration = configurations.maybeCreate("compileShaded")
configurations.getByName("compileOnly").extendsFrom(shade)
dependencies {
  // Use `api` because kapt will not resolve `runtime` dependencies without it, only `compile`
  // https://youtrack.jetbrains.com/issue/KT-41702
  api(project(":moshi"))
  api(kotlin("reflect"))
  shade(libs.kotlinxMetadata) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }
  api(libs.kotlinpoet)
  shade(libs.kotlinpoet.metadata.core) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
  }
  shade(libs.kotlinpoet.metadata.specs) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
  }
  api(libs.kotlinpoet.elementsClassInspector)
  shade(libs.kotlinpoet.elementsClassInspector) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
    exclude(group = "com.google.guava")
  }
  api(libs.asm)

  api(libs.autoService)
  kapt(libs.autoService.processor)
  api(libs.incap)
  kapt(libs.incap.processor)

  // Copy these again as they're not automatically included since they're shaded
  testImplementation(libs.kotlinpoet.metadata.core)
  testImplementation(libs.kotlinpoet.metadata.specs)
  testImplementation(libs.kotlinpoet.elementsClassInspector)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinCompileTesting)
}

val relocateShadowJar = tasks.register<ConfigureShadowRelocation>("relocateShadowJar") {
  target = tasks.shadowJar.get()
}

val shadowJar = tasks.shadowJar.apply {
  configure {
    dependsOn(relocateShadowJar)
    archiveClassifier.set("")
    configurations = listOf(shade)
    relocate("com.squareup.kotlinpoet.metadata", "com.squareup.moshi.kotlinpoet.metadata")
    relocate(
      "com.squareup.kotlinpoet.classinspector",
      "com.squareup.moshi.kotlinpoet.classinspector"
    )
    relocate("kotlinx.metadata", "com.squareup.moshi.kotlinx.metadata")
    transformers.add(ServiceFileTransformer())
  }
}

artifacts {
  runtimeOnly(shadowJar)
  archives(shadowJar)
}
