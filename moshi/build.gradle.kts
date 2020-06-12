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

plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
}

tasks.named<Jar>("jar") {
  manifest {
    attributes("Automatic-Module-Name" to "com.squareup.kotlinpoet")
  }
}

dependencies {
  compileOnly("com.google.code.findbugs:jsr305:3.0.2")
  api("com.squareup.okio:okio:1.16.0")

  testCompileOnly("com.google.code.findbugs:jsr305:3.0.2")
  testImplementation("junit:junit:4.12")
  testImplementation("org.assertj:assertj-core:3.11.1")
}
