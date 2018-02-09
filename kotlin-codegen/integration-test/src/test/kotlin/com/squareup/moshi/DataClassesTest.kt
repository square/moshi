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
import org.assertj.core.api.Assertions.fail
import org.intellij.lang.annotations.Language
import org.junit.Test

class DataClassesTest {

  private val moshi = Moshi.Builder().add(MoshiSerializableFactory.getInstance()).build()

  @Test
  fun jsonAnnotation() {
    val adapter = moshi.adapter(JsonAnnotation::class.java)

    // Read
    @Language("JSON")
    val json = """{"foo": "bar"}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.bar).isEqualTo("bar")

    // Write
    @Language("JSON")
    val expectedJson = """{"foo":"baz"}"""

    assertThat(adapter.toJson(JsonAnnotation("baz"))).isEqualTo(expectedJson)
  }

  @MoshiSerializable
  data class JsonAnnotation(@Json(name = "foo") val bar: String)

  @Test
  fun defaultValues() {
    val adapter = moshi.adapter(DefaultValues::class.java)

    // Read/write with default values
    @Language("JSON")
    val json = """{"foo":"fooString"}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.foo).isEqualTo("fooString")
    assertThat(instance.bar).isEqualTo("")
    assertThat(instance.nullableBar).isNull()
    assertThat(instance.bazList).apply {
      isNotNull()
      isEmpty()
    }

    @Language("JSON") val expected = """{"foo":"fooString","bar":"","bazList":[]}"""
    assertThat(adapter.toJson(DefaultValues("fooString"))).isEqualTo(expected)

    // Read/write with real values
    @Language("JSON")
    val json2 = """
      {"foo":"fooString","bar":"barString","nullableBar":"bar","bazList":["baz"]}
      """.trimIndent()

    val instance2 = adapter.fromJson(json2)!!
    assertThat(instance2.foo).isEqualTo("fooString")
    assertThat(instance2.bar).isEqualTo("barString")
    assertThat(instance2.nullableBar).isEqualTo("bar")
    assertThat(instance2.bazList).containsExactly("baz")
    assertThat(adapter.toJson(instance2)).isEqualTo(json2)
  }

  @MoshiSerializable
  data class DefaultValues(val foo: String,
      val bar: String = "",
      val nullableBar: String? = null,
      val bazList: List<String> = emptyList())

  @Test
  fun nullableArray() {
    val adapter = moshi.adapter(NullableArray::class.java)

    @Language("JSON")
    val json = """{"data":[null,"why"]}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.data).containsExactly(null, "why")
    assertThat(adapter.toJson(instance)).isEqualTo(json)
  }

  @MoshiSerializable
  data class NullableArray(val data: Array<String?>)

  @Test
  fun primitiveArray() {
    val adapter = moshi.adapter(PrimitiveArray::class.java)

    @Language("JSON")
    val json = """{"ints":[0,1]}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.ints).containsExactly(0, 1)
    assertThat(adapter.toJson(instance)).isEqualTo(json)
  }

  @MoshiSerializable
  data class PrimitiveArray(val ints: IntArray)

  @Test
  fun nullableTypes() {
    val adapter = moshi.adapter(NullabeTypes::class.java)

    @Language("JSON")
    val json = """{"foo":"foo","nullableString":null}"""
    @Language("JSON")
    val invalidJson = """{"foo":null,"nullableString":null}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.foo).isEqualTo("foo")
    assertThat(instance.nullableString).isNull()

    try {
      adapter.fromJson(invalidJson)
      fail("The invalid json should have failed!")
    } catch (e: KotlinNullPointerException) {
      // Expected
    }
  }

  @MoshiSerializable
  data class NullabeTypes(
      val foo: String,
      val nullableString: String?
  )

}

/**
 * This is here mostly just to ensure it still compiles. Covers variance, @Json, default values,
 * nullability, primitive arrays, and some wacky generics.
 */
@MoshiSerializable
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
    val wildcardOut: List<out String> = emptyList(),
    val wildcardIn: Array<in String>,
    val any: List<*>,
    val anyTwo: List<Any>,
    val anyOut: List<out Any>,
    val favoriteThreeNumbers: IntArray,
    val favoriteArrayValues: Array<String>,
    val favoriteNullableArrayValues: Array<String?>,
    val nullableSetListMapArrayNullableIntWithDefault: Set<List<Map<String, Array<IntArray?>>>>? = null
)
