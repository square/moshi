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
  id("com.github.johnrengelman.shadow") version "6.0.0"
}

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs = listOf(
      "-progressive",
      "-Xopt-in=com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview"
    )
  }
}

// To make Gradle happy
java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val shade: Configuration = configurations.maybeCreate("compileShaded")
configurations.getByName("compileOnly").extendsFrom(shade)
dependencies {
  // Use `api` because kapt will not resolve `runtime` dependencies without it, only `compile`
  // https://youtrack.jetbrains.com/issue/KT-41702
  api(project(":moshi"))
  api(kotlin("reflect"))
  shade(Dependencies.Kotlin.metadata) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }
  api(Dependencies.KotlinPoet.kotlinPoet)
  shade(Dependencies.KotlinPoet.metadata) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
  }
  shade(Dependencies.KotlinPoet.metadataSpecs) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
  }
  shade(Dependencies.KotlinPoet.elementsClassInspector) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
  }
  api(Dependencies.asm)

  api(Dependencies.AutoService.annotations)
  kapt(Dependencies.AutoService.processor)
  api(Dependencies.Incap.annotations)
  kapt(Dependencies.Incap.processor)

  // Copy these again as they're not automatically included since they're shaded
  testImplementation(Dependencies.KotlinPoet.metadata)
  testImplementation(Dependencies.KotlinPoet.metadataSpecs)
  testImplementation(Dependencies.KotlinPoet.elementsClassInspector)
  testImplementation(Dependencies.Testing.junit)
  testImplementation(Dependencies.Testing.truth)
  testImplementation(Dependencies.Testing.compileTesting)
  testImplementation(Dependencies.okio2)
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
  runtime(shadowJar)
  archives(shadowJar)
}
