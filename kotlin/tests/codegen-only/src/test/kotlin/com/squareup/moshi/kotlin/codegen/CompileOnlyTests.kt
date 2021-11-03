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
package com.squareup.moshi.kotlin.codegen

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.kotlin.codegen.test.extra.AbstractClassInModuleA
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.TYPE

/*
 * These are classes that need only compile.
 */

// Regression test for https://github.com/square/moshi/issues/905
@JsonClass(generateAdapter = true)
data class GenericTestClassWithDefaults<T>(
  val input: String = "",
  val genericInput: T
)

@Target(TYPE)
annotation class TypeAnnotation

/**
 * Compilation-only test to ensure we don't render types with their annotations.
 * Regression test for https://github.com/square/moshi/issues/1033
 */
@JsonClass(generateAdapter = true)
data class TypeAnnotationClass(
  val propertyWithAnnotatedType: @TypeAnnotation String = "",
  val generic: List<@TypeAnnotation String>
)

// Regression test for https://github.com/square/moshi/issues/1277
@JsonClass(generateAdapter = true)
data class OtherTestModel(val TestModel: TestModel? = null)
@JsonClass(generateAdapter = true)
data class TestModel(
  val someVariable: Int,
  val anotherVariable: String
)

// Regression test for https://github.com/square/moshi/issues/1022
@JsonClass(generateAdapter = true)
internal data class MismatchParentAndNestedClassVisibility(
  val type: Int,
  val name: String? = null
) {

  @JsonClass(generateAdapter = true)
  data class NestedClass(
    val nestedProperty: String
  )
}

// Regression test for https://github.com/square/moshi/issues/1052
@JsonClass(generateAdapter = true)
data class KeysWithSpaces(
  @Json(name = "1. Information") val information: String,
  @Json(name = "2. Symbol") val symbol: String,
  @Json(name = "3. Last Refreshed") val lastRefreshed: String,
  @Json(name = "4. Interval") val interval: String,
  @Json(name = "5. Output Size") val size: String,
  @Json(name = "6. Time Zone") val timeZone: String
)

// Regression test for https://github.com/square/moshi/issues/848
@JsonClass(generateAdapter = true)
data class Hotwords(
  val `class`: List<String>?
)

/**
 * This is here mostly just to ensure it still compiles. Covers variance, @Json, default values,
 * nullability, primitive arrays, and some wacky generics.
 */
@JsonClass(generateAdapter = true)
data class SmokeTestType(
  @Json(name = "first_name") val firstName: String,
  @Json(name = "last_name") val lastName: String,
  val age: Int,
  val nationalities: List<String> = emptyList(),
  val weight: Float,
  val tattoos: Boolean = false,
  val race: String?,
  val hasChildren: Boolean = false,
  val favoriteFood: String? = null,
  val favoriteDrink: String? = "Water",
  val wildcardOut: MutableList<out String> = mutableListOf(),
  val nullableWildcardOut: MutableList<out String?> = mutableListOf(),
  val wildcardIn: Array<in String>,
  val any: List<*>,
  val anyTwo: List<Any>,
  val anyOut: MutableList<out Any>,
  val nullableAnyOut: MutableList<out Any?>,
  val favoriteThreeNumbers: IntArray,
  val favoriteArrayValues: Array<String>,
  val favoriteNullableArrayValues: Array<String?>,
  val nullableSetListMapArrayNullableIntWithDefault: Set<List<Map<String, Array<IntArray?>>>>? = null,
  val aliasedName: TypeAliasName = "Woah",
  val genericAlias: GenericTypeAlias = listOf("Woah"),
  // Regression test for https://github.com/square/moshi/issues/1272
  val nestedArray: Array<Map<String, Any>>? = null
)

typealias TypeAliasName = String
typealias GenericTypeAlias = List<String>

// Regression test for enum constants in annotations and array types
// https://github.com/ZacSweers/MoshiX/issues/103
@Retention(RUNTIME)
@JsonQualifier
annotation class UpperCase(val foo: Array<Foo>)

enum class Foo { BAR }

@JsonClass(generateAdapter = true)
data class ClassWithQualifier(
  @UpperCase(foo = [Foo.BAR])
  val a: Int
)

// Regression for https://github.com/ZacSweers/MoshiX/issues/120
@JsonClass(generateAdapter = true)
data class DataClassInModuleB(
  val id: String
) : AbstractClassInModuleA()
