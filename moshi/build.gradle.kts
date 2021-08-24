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
}

val mainSourceSet by sourceSets.named("main")
val java16 by sourceSets.creating {
  java {
    srcDir("src/main/java16")
  }
}

tasks.named<JavaCompile>("compileJava16Java") {
  javaCompiler.set(
    javaToolchains.compilerFor {
      languageVersion.set(JavaLanguageVersion.of(16))
    }
  )
  options.release.set(16)
}

// Package our actual RecordJsonAdapter from java16 sources in and denote it as an MRJAR
tasks.named<Jar>("jar") {
  from(java16.output) {
    into("META-INF/versions/16")
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

tasks.withType<Test>().configureEach {
  // ExtendsPlatformClassWithProtectedField tests a case where we set a protected ByteArrayOutputStream.buf field
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
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
  // So the j16 source set can "see" main Moshi sources
  "java16Implementation"(mainSourceSet.output)
  compileOnly(libs.jsr305)
  api(libs.okio)

  testCompileOnly(libs.jsr305)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
