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
package com.squareup.moshi.kotlin.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Execute kotlinc to confirm that either files are generated or errors are printed. */
@UseExperimental(KotlinPoetMetadataPreview::class)
class JsonClassCodegenProcessorTest {
  @Rule @JvmField var temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test fun privateConstructor() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          @JsonClass(generateAdapter = true)
          class PrivateConstructor private constructor(var a: Int, var b: Int) {
            fun a() = a
            fun b() = b
            companion object {
              fun newInstance(a: Int, b: Int) = PrivateConstructor(a, b)
            }
          }
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("constructor is not internal or public")
  }

  @Test fun privateConstructorParameter() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateConstructorParameter(private var a: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
  }

  @Test fun privateProperties() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateProperties {
            private var a: Int = -1
            private var b: Int = -1
          }
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
  }

  @Test fun interfacesNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          interface Interface
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass can't be applied to Interface: must be a Kotlin class")
  }

  @Test fun interfacesDoNotErrorWhenGeneratorNotSet() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true, generator="customGenerator")
          interface Interface
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
  }

  @Test fun abstractClassesNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          @JsonClass(generateAdapter = true)
          abstract class AbstractClass(val a: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass can't be applied to AbstractClass: must not be abstract")
  }

  @Test fun sealedClassesNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
  
          @JsonClass(generateAdapter = true)
          sealed class SealedClass(val a: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass can't be applied to SealedClass: must not be sealed")
  }

  @Test fun innerClassesNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          class Outer {
            @JsonClass(generateAdapter = true)
            inner class InnerClass(val a: Int)
          }
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass can't be applied to Outer.InnerClass: must not be an inner class")
  }

  @Test fun enumClassesNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          @JsonClass(generateAdapter = true)
          enum class KotlinEnum {
            A, B
          }
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass with 'generateAdapter = \"true\"' can't be applied to KotlinEnum: code gen for enums is not supported or necessary")
  }

  // Annotation processors don't get called for local classes, so we don't have the opportunity to
  // print an error message. Instead local classes will fail at runtime.
  @Ignore @Test fun localClassesNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          fun outer() {
            @JsonClass(generateAdapter = true)
            class LocalClass(val a: Int)
          }
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass can't be applied to LocalClass: must not be local")
  }

  @Test fun objectDeclarationsNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          @JsonClass(generateAdapter = true)
          object ObjectDeclaration {
            var a = 5
          }
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass can't be applied to ObjectDeclaration: must be a Kotlin class")
  }

  @Test fun objectExpressionsNotSupported() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          @JsonClass(generateAdapter = true)
          val expression = object : Any() {
            var a = 5
          }
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: @JsonClass can't be applied to expression\$annotations(): must be a Kotlin class")
  }

  @Test fun requiredTransientConstructorParameterFails() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          @JsonClass(generateAdapter = true)
          class RequiredTransientConstructorParameter(@Transient var a: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: No default value for transient property a")
  }

  @Test fun nonPropertyConstructorParameter() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          import com.squareup.moshi.JsonClass
          @JsonClass(generateAdapter = true)
          class NonPropertyConstructorParameter(a: Int, val b: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
        "error: No property for required constructor parameter a")
  }

  @Test fun badGeneratedAnnotation() {
    val result = prepareCompilation(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          
          @JsonClass(generateAdapter = true)
          data class Foo(val a: Int)
          """
    )).apply {
      kaptArgs[JsonClassCodegenProcessor.OPTION_GENERATED] = "javax.annotation.GeneratedBlerg"
    }.compile()
    assertThat(result.messages).contains(
        "Invalid option value for ${JsonClassCodegenProcessor.OPTION_GENERATED}")
  }

  @Test fun multipleErrors() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class Class1(private var a: Int, private var b: Int)

          @JsonClass(generateAdapter = true)
          class Class2(private var c: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
    assertThat(result.messages).contains("property c is not visible")
  }

  @Test fun extendPlatformType() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          import java.util.Date

          @JsonClass(generateAdapter = true)
          class ExtendsPlatformClass(var a: Int) : Date()
          """
    ))
    assertThat(result.messages).contains("supertype java.util.Date is not a Kotlin type")
  }

  @Test fun extendJavaType() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.kotlin.codegen.JavaSuperclass
          
          @JsonClass(generateAdapter = true)
          class ExtendsJavaType(var b: Int) : JavaSuperclass()
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages)
        .contains("supertype com.squareup.moshi.kotlin.codegen.JavaSuperclass is not a Kotlin type")
  }

  @Test fun nonFieldApplicableQualifier() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.JsonQualifier
          import kotlin.annotation.AnnotationRetention.RUNTIME
          import kotlin.annotation.AnnotationTarget.PROPERTY
          import kotlin.annotation.Retention
          import kotlin.annotation.Target

          @Retention(RUNTIME)
          @Target(PROPERTY)
          @JsonQualifier
          annotation class UpperCase

          @JsonClass(generateAdapter = true)
          class ClassWithQualifier(@UpperCase val a: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("JsonQualifier @UpperCase must support FIELD target")
  }

  @Test fun nonRuntimeQualifier() {
    val result = compile(kotlin("source.kt",
        """
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.JsonQualifier
          import kotlin.annotation.AnnotationRetention.BINARY
          import kotlin.annotation.AnnotationTarget.FIELD
          import kotlin.annotation.AnnotationTarget.PROPERTY
          import kotlin.annotation.Retention
          import kotlin.annotation.Target

          @Retention(BINARY)
          @Target(PROPERTY, FIELD)
          @JsonQualifier
          annotation class UpperCase

          @JsonClass(generateAdapter = true)
          class ClassWithQualifier(@UpperCase val a: Int)
          """
    ))
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("JsonQualifier @UpperCase must have RUNTIME retention")
  }

  private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
    return KotlinCompilation()
        .apply {
          workingDir = temporaryFolder.root
          annotationProcessors = listOf(JsonClassCodegenProcessor())
          inheritClassPath = true
          sources = sourceFiles.asList()
          verbose = false
        }
  }

  private fun compile(vararg sourceFiles: SourceFile): KotlinCompilation.Result {
    return prepareCompilation(*sourceFiles).compile()
  }

}
