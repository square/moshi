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
  id("com.github.johnrengelman.shadow") version "5.2.0"
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

val shade: Configuration = configurations.maybeCreate("compileShaded")
configurations.getByName("compileOnly").extendsFrom(shade)
dependencies {
  implementation(project(":moshi"))
  implementation(kotlin("reflect"))
  shade(Dependencies.Kotlin.metadata) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }
  implementation(Dependencies.KotlinPoet.kotlinPoet)
  shade(Dependencies.KotlinPoet.metadata) {
    exclude(group = "org.jetbrains.kotlin")
  }
  shade(Dependencies.KotlinPoet.metadataSpecs) {
    exclude(group = "org.jetbrains.kotlin")
  }
  shade(Dependencies.KotlinPoet.elementsClassInspector) {
    exclude(group = "org.jetbrains.kotlin")
  }
  implementation(Dependencies.asm)

  implementation(Dependencies.AutoService.annotations)
  kapt(Dependencies.AutoService.processor)
  implementation(Dependencies.Incap.annotations)
  kapt(Dependencies.Incap.processor)

  // Copy these again as they're not automatically included since they're shaded
  testImplementation(Dependencies.KotlinPoet.metadata)
  testImplementation(Dependencies.KotlinPoet.metadataSpecs)
  testImplementation(Dependencies.KotlinPoet.elementsClassInspector)
  testImplementation(Dependencies.Testing.junit)
  testImplementation(Dependencies.Testing.assertj)
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

// Shadow plugin doesn't natively support gradle metadata, so we have to tell the maven plugin where
// to get a jar now.
afterEvaluate {
  configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
      if (name == "pluginMaven") {
        // This is to properly wire the shadow jar's gradle metadata and pom information
        setArtifacts(artifacts.matching { it.classifier != "" })
        // Ugly but artifact() doesn't support TaskProviders
        artifact(shadowJar.get())
      }
    }
  }
}
