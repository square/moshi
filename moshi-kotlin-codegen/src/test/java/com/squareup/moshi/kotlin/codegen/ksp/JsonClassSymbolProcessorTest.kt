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
package com.squareup.moshi.kotlin.codegen.ksp

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.kotlin.codegen.api.Options.OPTION_GENERATED
import com.squareup.moshi.kotlin.codegen.api.Options.OPTION_GENERATE_PROGUARD_RULES
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.java
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.typeOf

/** Execute kotlinc to confirm that either files are generated or errors are printed. */
@RunWith(Parameterized::class)
class JsonClassSymbolProcessorTest(private val useKSP2: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useKSP2={0}")
    fun data(): Collection<Array<Any>> = listOf(
      arrayOf(false),
      arrayOf(true),
    )
  }

  @Rule
  @JvmField
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun privateConstructor() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateConstructor private constructor(var a: Int, var b: Int) {
            fun a() = a
            fun b() = b
            companion object {
              fun newInstance(a: Int, b: Int) = PrivateConstructor(a, b)
            }
          }
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("constructor is not internal or public")
  }

  @Test
  fun privateConstructorParameter() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateConstructorParameter(private var a: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
  }

  @Test
  fun privateProperties() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class PrivateProperties {
            private var a: Int = -1
            private var b: Int = -1
          }
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
  }

  @Test
  fun interfacesNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          interface Interface
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to test.Interface: must be a Kotlin class",
    )
  }

  @Test
  fun interfacesDoNotErrorWhenGeneratorNotSet() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true, generator="customGenerator")
          interface Interface
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
  }

  @Test
  fun abstractClassesNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          abstract class AbstractClass(val a: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to test.AbstractClass: must not be abstract",
    )
  }

  @Test
  fun sealedClassesNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          sealed class SealedClass(val a: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to test.SealedClass: must not be sealed",
    )
  }

  @Test
  fun innerClassesNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          class Outer {
            @JsonClass(generateAdapter = true)
            inner class InnerClass(val a: Int)
          }
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to test.Outer.InnerClass: must not be an inner class",
    )
  }

  @Test
  fun enumClassesNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          enum class KotlinEnum {
            A, B
          }
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass with 'generateAdapter = \"true\"' can't be applied to test.KotlinEnum: code gen for enums is not supported or necessary",
    )
  }

  // Annotation processors don't get called for local classes, so we don't have the opportunity to
  @Ignore
  @Test
  fun localClassesNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          fun outer() {
            @JsonClass(generateAdapter = true)
            class LocalClass(val a: Int)
          }
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to LocalClass: must not be local",
    )
  }

  @Test
  fun privateClassesNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          private class PrivateClass(val a: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to test.PrivateClass: must be internal or public",
    )
  }

  @Test
  fun objectDeclarationsNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          object ObjectDeclaration {
            var a = 5
          }
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to test.ObjectDeclaration: must be a Kotlin class",
    )
  }

  @Test
  fun objectExpressionsNotSupported() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          val expression = object : Any() {
            var a = 5
          }
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "@JsonClass can't be applied to test.expression: must be a Kotlin class",
    )
  }

  @Test
  fun requiredTransientConstructorParameterFails() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class RequiredTransientConstructorParameter(@Transient var a: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "No default value for transient/ignored property a",
    )
  }

  @Test
  fun requiredIgnoredConstructorParameterFails() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.Json
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class RequiredTransientConstructorParameter(@Json(ignore = true) var a: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "No default value for transient/ignored property a",
    )
  }

  @Test
  fun nonPropertyConstructorParameter() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class NonPropertyConstructorParameter(a: Int, val b: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains(
      "No property for required constructor parameter a",
    )
  }

  @Test
  fun badGeneratedAnnotation() {
    val result = prepareCompilation(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          data class Foo(val a: Int)
          """,
      ),
    ).apply {
      kspProcessorOptions[OPTION_GENERATED] = "javax.annotation.GeneratedBlerg"
    }.compile()
    assertThat(result.messages).contains(
      "Invalid option value for $OPTION_GENERATED",
    )
  }

  @Test
  fun disableProguardGeneration() {
    val compilation = prepareCompilation(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          data class Foo(val a: Int)
          """,
      ),
    ).apply {
      kspProcessorOptions[OPTION_GENERATE_PROGUARD_RULES] = "false"
    }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    assertThat(compilation.kspSourcesDir.walkTopDown().filter { it.extension == "pro" }.toList()).isEmpty()
  }

  @Test
  fun multipleErrors() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          class Class1(private var a: Int, private var b: Int)

          @JsonClass(generateAdapter = true)
          class Class2(private var c: Int)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("property a is not visible")
    assertThat(result.messages).contains("property c is not visible")
  }

  @Test
  fun extendPlatformType() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass
          import java.util.Date

          @JsonClass(generateAdapter = true)
          class ExtendsPlatformClass(var a: Int) : Date()
          """,
      ),
    )
    assertThat(result.messages).contains("supertype java.util.Date is not a Kotlin type")
  }

  @Test
  fun extendJavaType() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.kotlin.codegen.JavaSuperclass

          @JsonClass(generateAdapter = true)
          class ExtendsJavaType(var b: Int) : JavaSuperclass()
          """,
      ),
      java(
        "JavaSuperclass.java",
        """
        package com.squareup.moshi.kotlin.codegen;
        public class JavaSuperclass {
          public int a = 1;
        }
        """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages)
      .contains("supertype com.squareup.moshi.kotlin.codegen.JavaSuperclass is not a Kotlin type")
  }

  @Test
  fun nonFieldApplicableQualifier() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
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
          """,
      ),
    )
    // We instantiate directly, no FIELD site target necessary
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
  }

  @Test
  fun nonRuntimeQualifier() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
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
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("JsonQualifier @UpperCase must have RUNTIME retention")
  }

  @Test
  fun invalidGenericSyntaxErrorMessaging() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          @JsonClass(generateAdapter = true)
          data class ElementEnvelope(val elements: List)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Error preparing ElementEnvelope")
  }

  @Test
  fun `TypeAliases with the same backing type should share the same adapter`() {
    val result = compile(
      kotlin(
        "source.kt",
        """
          package test
          import com.squareup.moshi.JsonClass

          typealias FirstName = String
          typealias LastName = String

          @JsonClass(generateAdapter = true)
          data class Person(val firstName: FirstName, val lastName: LastName, val hairColor: String)
          """,
      ),
    )
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    // We're checking here that we only generate one `stringAdapter` that's used for both the
    // regular string properties as well as the aliased ones.
    val adapterClass = result.classLoader.loadClass("test.PersonJsonAdapter").kotlin
    assertThat(adapterClass.declaredMemberProperties.map { it.returnType }).containsExactly(
      JsonReader.Options::class.createType(),
      JsonAdapter::class.createType(listOf(KTypeProjection.invariant(typeOf<String>()))),
    )
  }

  @Test
  fun `Processor should generate comprehensive proguard rules`() {
    val compilation = prepareCompilation(
      kotlin(
        "source.kt",
        """
          package testPackage
          import com.squareup.moshi.JsonClass
          import com.squareup.moshi.JsonQualifier

          typealias FirstName = String
          typealias LastName = String

          @JsonClass(generateAdapter = true)
          data class Aliases(val firstName: FirstName, val lastName: LastName, val hairColor: String)

          @JsonClass(generateAdapter = true)
          data class Simple(val firstName: String)

          @JsonClass(generateAdapter = true)
          data class Generic<T>(val firstName: T, val lastName: String)

          @JsonQualifier
          annotation class MyQualifier

          @JsonClass(generateAdapter = true)
          data class UsingQualifiers(val firstName: String, @MyQualifier val lastName: String)

          @JsonClass(generateAdapter = true)
          data class MixedTypes(val firstName: String, val otherNames: MutableList<String>)

          @JsonClass(generateAdapter = true)
          data class DefaultParams(val firstName: String = "")

          @JsonClass(generateAdapter = true)
          data class Complex<T>(val firstName: FirstName = "", @MyQualifier val names: MutableList<String>, val genericProp: T)

          object NestedType {
            @JsonQualifier
            annotation class NestedQualifier

            @JsonClass(generateAdapter = true)
            data class NestedSimple(@NestedQualifier val firstName: String)
          }

          @JsonClass(generateAdapter = true)
          class MultipleMasks(
              val arg0: Long = 0,
              val arg1: Long = 1,
              val arg2: Long = 2,
              val arg3: Long = 3,
              val arg4: Long = 4,
              val arg5: Long = 5,
              val arg6: Long = 6,
              val arg7: Long = 7,
              val arg8: Long = 8,
              val arg9: Long = 9,
              val arg10: Long = 10,
              val arg11: Long,
              val arg12: Long = 12,
              val arg13: Long = 13,
              val arg14: Long = 14,
              val arg15: Long = 15,
              val arg16: Long = 16,
              val arg17: Long = 17,
              val arg18: Long = 18,
              val arg19: Long = 19,
              @Suppress("UNUSED_PARAMETER") arg20: Long = 20,
              val arg21: Long = 21,
              val arg22: Long = 22,
              val arg23: Long = 23,
              val arg24: Long = 24,
              val arg25: Long = 25,
              val arg26: Long = 26,
              val arg27: Long = 27,
              val arg28: Long = 28,
              val arg29: Long = 29,
              val arg30: Long = 30,
              val arg31: Long = 31,
              val arg32: Long = 32,
              val arg33: Long = 33,
              val arg34: Long = 34,
              val arg35: Long = 35,
              val arg36: Long = 36,
              val arg37: Long = 37,
              val arg38: Long = 38,
              @Transient val arg39: Long = 39,
              val arg40: Long = 40,
              val arg41: Long = 41,
              val arg42: Long = 42,
              val arg43: Long = 43,
              val arg44: Long = 44,
              val arg45: Long = 45,
              val arg46: Long = 46,
              val arg47: Long = 47,
              val arg48: Long = 48,
              val arg49: Long = 49,
              val arg50: Long = 50,
              val arg51: Long = 51,
              val arg52: Long = 52,
              @Transient val arg53: Long = 53,
              val arg54: Long = 54,
              val arg55: Long = 55,
              val arg56: Long = 56,
              val arg57: Long = 57,
              val arg58: Long = 58,
              val arg59: Long = 59,
              val arg60: Long = 60,
              val arg61: Long = 61,
              val arg62: Long = 62,
              val arg63: Long = 63,
              val arg64: Long = 64,
              val arg65: Long = 65
          )
          """,
      ),
    )
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    compilation.kspSourcesDir.walkTopDown().filter { it.extension == "pro" }.forEach { generatedFile ->
      when (generatedFile.nameWithoutExtension) {
        "moshi-testPackage.Aliases" -> assertThat(generatedFile.readText()).contains(
          """
          -keepnames class testPackage.Aliases
          -if class testPackage.Aliases
          -keep class testPackage.AliasesJsonAdapter {
              public <init>(com.squareup.moshi.Moshi);
          }
          """.trimIndent(),
        )

        "moshi-testPackage.Simple" -> assertThat(generatedFile.readText()).contains(
          """
          -keepnames class testPackage.Simple
          -if class testPackage.Simple
          -keep class testPackage.SimpleJsonAdapter {
              public <init>(com.squareup.moshi.Moshi);
          }
          """.trimIndent(),
        )

        "moshi-testPackage.Generic" -> assertThat(generatedFile.readText()).contains(
          """
          -keepnames class testPackage.Generic
          -if class testPackage.Generic
          -keep class testPackage.GenericJsonAdapter {
              public <init>(com.squareup.moshi.Moshi,java.lang.reflect.Type[]);
          }
          """.trimIndent(),
        )

        "moshi-testPackage.UsingQualifiers" -> {
          assertThat(generatedFile.readText()).contains(
            """
            -keepnames class testPackage.UsingQualifiers
            -if class testPackage.UsingQualifiers
            -keep class testPackage.UsingQualifiersJsonAdapter {
                public <init>(com.squareup.moshi.Moshi);
            }
            """.trimIndent(),
          )
        }

        "moshi-testPackage.MixedTypes" -> assertThat(generatedFile.readText()).contains(
          """
          -keepnames class testPackage.MixedTypes
          -if class testPackage.MixedTypes
          -keep class testPackage.MixedTypesJsonAdapter {
              public <init>(com.squareup.moshi.Moshi);
          }
          """.trimIndent(),
        )

        "moshi-testPackage.DefaultParams" -> assertThat(generatedFile.readText()).contains(
          """
          -keepnames class testPackage.DefaultParams
          -if class testPackage.DefaultParams
          -keep class testPackage.DefaultParamsJsonAdapter {
              public <init>(com.squareup.moshi.Moshi);
          }
          -if class testPackage.DefaultParams
          -keepnames class kotlin.jvm.internal.DefaultConstructorMarker
          -keepclassmembers class testPackage.DefaultParams {
              public synthetic <init>(java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
          }
          """.trimIndent(),
        )

        "moshi-testPackage.Complex" -> {
          assertThat(generatedFile.readText()).contains(
            """
            -keepnames class testPackage.Complex
            -if class testPackage.Complex
            -keep class testPackage.ComplexJsonAdapter {
                public <init>(com.squareup.moshi.Moshi,java.lang.reflect.Type[]);
            }
            -if class testPackage.Complex
            -keepnames class kotlin.jvm.internal.DefaultConstructorMarker
            -keepclassmembers class testPackage.Complex {
                public synthetic <init>(java.lang.String,java.util.List,java.lang.Object,int,kotlin.jvm.internal.DefaultConstructorMarker);
            }
            """.trimIndent(),
          )
        }

        "moshi-testPackage.MultipleMasks" -> assertThat(generatedFile.readText()).contains(
          """
          -keepnames class testPackage.MultipleMasks
          -if class testPackage.MultipleMasks
          -keep class testPackage.MultipleMasksJsonAdapter {
              public <init>(com.squareup.moshi.Moshi);
          }
          -if class testPackage.MultipleMasks
          -keepnames class kotlin.jvm.internal.DefaultConstructorMarker
          -keepclassmembers class testPackage.MultipleMasks {
              public synthetic <init>(long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,long,int,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
          }
          """.trimIndent(),
        )

        "moshi-testPackage.NestedType.NestedSimple" -> {
          assertThat(generatedFile.readText()).contains(
            """
            -keepnames class testPackage.NestedType${'$'}NestedSimple
            -if class testPackage.NestedType${'$'}NestedSimple
            -keep class testPackage.NestedType_NestedSimpleJsonAdapter {
                public <init>(com.squareup.moshi.Moshi);
            }
            """.trimIndent(),
          )
        }

        else -> error("Unexpected proguard file! ${generatedFile.name}")
      }
    }
  }

  private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
    return KotlinCompilation()
      .apply {
        workingDir = temporaryFolder.root
        inheritClassPath = true
        sources = sourceFiles.asList()
        verbose = false
        configureKsp(useKsp2 = useKSP2) {
          symbolProcessorProviders += JsonClassSymbolProcessorProvider()
          incremental = true // The default now
          if (!useKSP2) {
            withCompilation = true // Only necessary for KSP1
            languageVersion = "1.9"
          }
        }
      }
  }

  private fun compile(vararg sourceFiles: SourceFile): JvmCompilationResult {
    return prepareCompilation(*sourceFiles).compile()
  }
}
