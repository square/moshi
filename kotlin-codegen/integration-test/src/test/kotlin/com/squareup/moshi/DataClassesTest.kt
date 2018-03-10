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

  private val moshi = Moshi.Builder().add(MoshiSerializableFactory()).build()

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
    } catch (e: JsonDataException) {
      assertThat(e).hasMessageContaining("foo")
    }
  }

  @MoshiSerializable
  data class NullabeTypes(
      val foo: String,
      val nullableString: String?
  )

  @Test
  fun collections() {
    val adapter = moshi.adapter(SpecialCollections::class.java)

    val specialCollections = SpecialCollections(
        mutableListOf(),
        mutableSetOf(),
        mutableMapOf(),
        emptyList(),
        emptySet(),
        emptyMap()
    )

    val json = adapter.toJson(specialCollections)
    val newCollections = adapter.fromJson(json)
    assertThat(newCollections).isEqualTo(specialCollections)
  }

  @MoshiSerializable
  data class SpecialCollections(
      val mutableList: MutableList<String>,
      val mutableSet: MutableSet<String>,
      val mutableMap: MutableMap<String, String>,
      val immutableList: List<String>,
      val immutableSet: Set<String>,
      val immutableMap: Map<String, String>
  )

  @Test
  fun mutableProperties() {
    val adapter = moshi.adapter(MutableProperties::class.java)

    val mutableProperties = MutableProperties(
        "immutableProperty",
        "mutableProperty",
        mutableListOf("immutableMutableList"),
        mutableListOf("immutableImmutableList"),
        mutableListOf("mutableMutableList"),
        mutableListOf("mutableImmutableList"),
        "immutableProperty",
        "mutableProperty",
        mutableListOf("immutableMutableList"),
        mutableListOf("immutableImmutableList"),
        mutableListOf("mutableMutableList"),
        mutableListOf("mutableImmutableList")
    )

    val json = adapter.toJson(mutableProperties)
    val newMutableProperties = adapter.fromJson(json)
    assertThat(newMutableProperties).isEqualTo(mutableProperties)
  }

  @MoshiSerializable
  data class MutableProperties(
      val immutableProperty: String,
      var mutableProperty: String,
      val immutableMutableList: MutableList<String>,
      val immutableImmutableList: List<String>,
      var mutableMutableList: MutableList<String>,
      var mutableImmutableList: List<String>,
      val nullableImmutableProperty: String?,
      var nullableMutableProperty: String?,
      val nullableImmutableMutableList: MutableList<String>?,
      val nullableImmutableImmutableList: List<String>?,
      var nullableMutableMutableList: MutableList<String>?,
      var nullableMutableImmutableList: List<String>
  )

  @Test
  fun nullableTypeParams() {
    val adapter = moshi.adapter<NullableTypeParams<Int>>(
        Types.newParameterizedType(NullableTypeParams::class.java, Int::class.javaObjectType))
    val nullSerializing = adapter.serializeNulls()

    val nullableTypeParams = NullableTypeParams<Int>(
        listOf("foo", null, "bar"),
        setOf("foo", null, "bar"),
        mapOf("foo" to "bar", "baz" to null),
        null
    )

    val noNullsTypeParams = NullableTypeParams<Int>(
        nullableTypeParams.nullableList,
        nullableTypeParams.nullableSet,
        nullableTypeParams.nullableMap.filterValues { it != null },
        null
    )

    val json = adapter.toJson(nullableTypeParams)
    val newNullableTypeParams = adapter.fromJson(json)
    assertThat(newNullableTypeParams).isEqualTo(noNullsTypeParams)

    val nullSerializedJson = nullSerializing.toJson(nullableTypeParams)
    val nullSerializedNullableTypeParams = adapter.fromJson(nullSerializedJson)
    assertThat(nullSerializedNullableTypeParams).isEqualTo(nullableTypeParams)
  }
}

// Has to be outside to avoid Types seeing an owning class
@MoshiSerializable
data class NullableTypeParams<T>(
    val nullableList: List<String?>,
    val nullableSet: Set<String?>,
    val nullableMap: Map<String, String?>,
    val nullableT: T?
)

typealias TypeAliasName = String

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
    val nullableSetListMapArrayNullableIntWithDefault: Set<List<Map<String, Array<IntArray?>>>>? = null,
    val aliasedName: TypeAliasName = "Woah"
)
