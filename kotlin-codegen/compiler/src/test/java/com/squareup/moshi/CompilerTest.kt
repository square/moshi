/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.annotation.processing.Processor

/** Execute kotlinc to confirm that either files are generated or errors are printed. */
class CompilerTest {
  @Rule @JvmField var temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test fun privateProperty() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |class PrivateConstructorParameter(private var a: Int)
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains("property a is not visible")
  }

  @Test fun interfacesNotSupported() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |interface Interface
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: @JsonClass can't be applied to Interface: must be a Kotlin class")
  }

  @Test fun abstractClassesNotSupported() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |abstract class AbstractClass(val a: Int)
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: @JsonClass can't be applied to AbstractClass: must not be abstract")
  }

  @Test fun innerClassesNotSupported() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |class Outer {
        |  @JsonClass(generateAdapter = true)
        |  inner class InnerClass(val a: Int)
        |}
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: @JsonClass can't be applied to Outer.InnerClass: must not be an inner class")
  }

  // Annotation processors don't get called for local classes, so we don't have the opportunity to
  // print an error message. Instead local classes will fail at runtime.
  @Ignore
  @Test fun localClassesNotSupported() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |fun outer() {
        |  @JsonClass(generateAdapter = true)
        |  class LocalClass(val a: Int)
        |}
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: @JsonClass can't be applied to LocalClass: must not be local")
  }

  @Test fun objectDeclarationsNotSupported() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |object ObjectDeclaration {
        |  var a = 5
        |}
        |""".trimMargin())
    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: @JsonClass can't be applied to ObjectDeclaration: must be a Kotlin class")
  }

  @Test fun objectExpressionsNotSupported() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |val expression = object : Any() {
        |  var a = 5
        |}
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: @JsonClass can't be applied to expression\$annotations(): must be a Kotlin class")
  }

  @Test fun requiredTransientConstructorParameterFails() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |class RequiredTransientConstructorParameter(@Transient var a: Int)
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: No default value for transient property a")
  }

  @Test fun nonPropertyConstructorParameter() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |class NonPropertyConstructorParameter(a: Int, val b: Int)
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains(
        "error: No property for required constructor parameter a")
  }
}