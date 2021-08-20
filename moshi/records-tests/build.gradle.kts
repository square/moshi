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

plugins {
  `java-library`
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(16))
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(16)
}

dependencies {
  testImplementation(project(":moshi"))
  testCompileOnly(Dependencies.jsr305)
  testImplementation(Dependencies.Testing.junit)
  testImplementation(Dependencies.Testing.truth)
}
