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
  id("com.vanniktech.maven.publish")
  id("ru.vyarus.animalsniffer")
}

val mainSourceSet by sourceSets.named("main")
val java16 by sourceSets.creating {
  java {
    srcDir("src/main/java16")
  }
}

tasks.named<JavaCompile>("compileJava16Java") {
  javaCompiler.set(javaToolchains.compilerFor {
    languageVersion.set(JavaLanguageVersion.of(16))
  })
  options.release.set(16)
}

tasks.named<Jar>("jar") {
  from(java16.output) {
//    into("META-INF/versions/9")
//    include("module-info.class")
    duplicatesStrategy = DuplicatesStrategy.WARN
  }
  manifest {
    attributes("Multi-Release" to "true")
  }
}

configurations {
  "java16Implementation" {
    extendsFrom(api.get())
    extendsFrom(implementation.get())
  }
}

tasks.withType<KotlinCompile>()
  .configureEach {
    kotlinOptions {
      if (name.contains("test", true)) {
        @Suppress("SuspiciousCollectionReassignment") // It's not suspicious
        freeCompilerArgs += listOf("-Xopt-in=kotlin.ExperimentalStdlibApi")
      }
    }
  }

dependencies {
  "java16Implementation"(mainSourceSet.output)
  compileOnly(Dependencies.jsr305)
  api(Dependencies.okio)

  testCompileOnly(Dependencies.jsr305)
  testImplementation(Dependencies.Testing.junit)
  testImplementation(Dependencies.Testing.truth)
}
