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

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.internal.NullSafeJsonAdapter
import com.squareup.moshi.kotlin.reflect.adapter
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.util.Locale
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.properties.Delegates
import kotlin.reflect.full.memberProperties

@ExperimentalStdlibApi
@Suppress("UNUSED", "UNUSED_PARAMETER")
class GeneratedAdaptersTest {

  private val moshi = Moshi.Builder().build()

  @Test
  fun jsonAnnotation() {
    val adapter = moshi.adapter<JsonAnnotation>()

    // Read
    @Language("JSON")
    val json = """{"foo": "bar"}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.bar).isEqualTo("bar")

    // Write
    @Language("JSON")
    val expectedJson = """{"foo":"baz"}"""

    assertThat(adapter.toJson(
        JsonAnnotation("baz"))).isEqualTo(expectedJson)
  }

  @JsonClass(generateAdapter = true)
  data class JsonAnnotation(@Json(name = "foo") val bar: String)

  @Test
  fun jsonAnnotationWithDollarSign() {
    val adapter = moshi.adapter<JsonAnnotationWithDollarSign>()

    // Read
    val json = "{\"\$foo\": \"bar\"}"

    val instance = adapter.fromJson(json)!!
    assertThat(instance.bar).isEqualTo("bar")

    // Write
    val expectedJson = "{\"\$foo\":\"baz\"}"

    assertThat(adapter.toJson(
        JsonAnnotationWithDollarSign("baz"))).isEqualTo(expectedJson)
  }

  @JsonClass(generateAdapter = true)
  data class JsonAnnotationWithDollarSign(@Json(name = "\$foo") val bar: String)

  @Test
  fun jsonAnnotationWithQuotationMark() {
    val adapter = moshi.adapter<JsonAnnotationWithQuotationMark>()

    // Read
    val json = """{"\"foo\"": "bar"}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.bar).isEqualTo("bar")

    // Write
    val expectedJson = """{"\"foo\"":"baz"}"""

    assertThat(adapter.toJson(
        JsonAnnotationWithQuotationMark("baz"))).isEqualTo(expectedJson)
  }

  @JsonClass(generateAdapter = true)
  data class JsonAnnotationWithQuotationMark(@Json(name = "\"foo\"") val bar: String)

  @Test
  fun defaultValues() {
    val adapter = moshi.adapter<DefaultValues>()

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
    assertThat(adapter.toJson(
        DefaultValues("fooString"))).isEqualTo(expected)

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

  @JsonClass(generateAdapter = true)
  data class DefaultValues(
    val foo: String,
    val bar: String = "",
    val nullableBar: String? = null,
    val bazList: List<String> = emptyList())

  @Test
  fun nullableArray() {
    val adapter = moshi.adapter<NullableArray>()

    @Language("JSON")
    val json = """{"data":[null,"why"]}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.data).containsExactly(null, "why")
    assertThat(adapter.toJson(instance)).isEqualTo(json)
  }

  @JsonClass(generateAdapter = true)
  data class NullableArray(val data: Array<String?>)

  @Test
  fun primitiveArray() {
    val adapter = moshi.adapter<PrimitiveArray>()

    @Language("JSON")
    val json = """{"ints":[0,1]}"""

    val instance = adapter.fromJson(json)!!
    assertThat(instance.ints).containsExactly(0, 1)
    assertThat(adapter.toJson(instance)).isEqualTo(json)
  }

  @JsonClass(generateAdapter = true)
  data class PrimitiveArray(val ints: IntArray)

  @Test
  fun nullableTypes() {
    val adapter = moshi.adapter<NullabeTypes>()

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

  @JsonClass(generateAdapter = true)
  data class NullabeTypes(
      val foo: String,
      val nullableString: String?
  )

  @Test
  fun collections() {
    val adapter = moshi.adapter<SpecialCollections>()

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

  @JsonClass(generateAdapter = true)
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
    val adapter = moshi.adapter<MutableProperties>()

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

  @JsonClass(generateAdapter = true)
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
        Types.newParameterizedTypeWithOwner(
            GeneratedAdaptersTest::class.java,
            NullableTypeParams::class.java, Int::class.javaObjectType))
    val nullSerializing = adapter.serializeNulls()

    val nullableTypeParams = NullableTypeParams(
        listOf("foo", null, "bar"),
        setOf("foo", null, "bar"),
        mapOf("foo" to "bar", "baz" to null),
        null,
        1
    )

    val noNullsTypeParams = NullableTypeParams(
        nullableTypeParams.nullableList,
        nullableTypeParams.nullableSet,
        nullableTypeParams.nullableMap.filterValues { it != null },
        null,
        1
    )

    val json = adapter.toJson(nullableTypeParams)
    val newNullableTypeParams = adapter.fromJson(json)
    assertThat(newNullableTypeParams).isEqualTo(noNullsTypeParams)

    val nullSerializedJson = nullSerializing.toJson(nullableTypeParams)
    val nullSerializedNullableTypeParams = adapter.fromJson(nullSerializedJson)
    assertThat(nullSerializedNullableTypeParams).isEqualTo(nullableTypeParams)
  }

  @JsonClass(generateAdapter = true)
  data class NullableTypeParams<T>(
    val nullableList: List<String?>,
    val nullableSet: Set<String?>,
    val nullableMap: Map<String, String?>,
    val nullableT: T?,
    val nonNullT: T
  )

  @Test fun doNotGenerateAdapter() {
    try {
      Class.forName("${GeneratedAdaptersTest::class.java.name}_DoNotGenerateAdapterJsonAdapter")
      fail("found a generated adapter for a type that shouldn't have one")
    } catch (expected: ClassNotFoundException) {
    }
  }

  @JsonClass(generateAdapter = false)
  data class DoNotGenerateAdapter(val foo: String)

  @Test fun constructorParameters() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ConstructorParameters>()

    val encoded = ConstructorParameters(3,
        5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class ConstructorParameters(var a: Int, var b: Int)

  @Test fun properties() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<Properties>()

    val encoded = Properties()
    encoded.a = 3
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":3,"b":5}""")!!
    assertThat(decoded.a).isEqualTo(3)
    assertThat(decoded.b).isEqualTo(5)
  }

  @JsonClass(generateAdapter = true)
  class Properties {
    var a: Int = -1
    var b: Int = -1
  }

  @Test fun constructorParametersAndProperties() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ConstructorParametersAndProperties>()

    val encoded = ConstructorParametersAndProperties(
        3)
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class ConstructorParametersAndProperties(var a: Int) {
    var b: Int = -1
  }

  @Test fun immutableConstructorParameters() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ImmutableConstructorParameters>()

    val encoded = ImmutableConstructorParameters(
        3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class ImmutableConstructorParameters(val a: Int, val b: Int)

  @Test fun immutableProperties() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ImmutableProperties>()

    val encoded = ImmutableProperties(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":3,"b":5}""")!!
    assertThat(decoded.a).isEqualTo(3)
    assertThat(decoded.b).isEqualTo(5)
  }

  @JsonClass(generateAdapter = true)
  class ImmutableProperties(a: Int, b: Int) {
    val a = a
    val b = b
  }

  @Test fun constructorDefaults() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ConstructorDefaultValues>()

    val encoded = ConstructorDefaultValues(
        3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class ConstructorDefaultValues(var a: Int = -1, var b: Int = -2)

  @Test fun explicitNull() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ExplicitNull>()

    val encoded = ExplicitNull(null, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")
    assertThat(jsonAdapter.serializeNulls().toJson(encoded)).isEqualTo("""{"a":null,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":null,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(null)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class ExplicitNull(var a: Int?, var b: Int?)

  @Test fun absentNull() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<AbsentNull>()

    val encoded = AbsentNull(null, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")
    assertThat(jsonAdapter.serializeNulls().toJson(encoded)).isEqualTo("""{"a":null,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"b":6}""")!!
    assertThat(decoded.a).isNull()
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class AbsentNull(var a: Int?, var b: Int?)

  @Test fun constructorParameterWithQualifier() {
    val moshi = Moshi.Builder()
        .add(UppercaseJsonAdapter())
        .build()
    val jsonAdapter = moshi.adapter<ConstructorParameterWithQualifier>()

    val encoded = ConstructorParameterWithQualifier(
        "Android", "Banana")
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":"ANDROID","b":"Banana"}""")

    val decoded = jsonAdapter.fromJson("""{"a":"Android","b":"Banana"}""")!!
    assertThat(decoded.a).isEqualTo("android")
    assertThat(decoded.b).isEqualTo("Banana")
  }

  @JsonClass(generateAdapter = true)
  class ConstructorParameterWithQualifier(@Uppercase(inFrench = true) var a: String, var b: String)

  @Test fun propertyWithQualifier() {
    val moshi = Moshi.Builder()
        .add(UppercaseJsonAdapter())
        .build()
    val jsonAdapter = moshi.adapter<PropertyWithQualifier>()

    val encoded = PropertyWithQualifier()
    encoded.a = "Android"
    encoded.b = "Banana"
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":"ANDROID","b":"Banana"}""")

    val decoded = jsonAdapter.fromJson("""{"a":"Android","b":"Banana"}""")!!
    assertThat(decoded.a).isEqualTo("android")
    assertThat(decoded.b).isEqualTo("Banana")
  }

  @JsonClass(generateAdapter = true)
  class PropertyWithQualifier {
    @Uppercase(inFrench = true) var a: String = ""
    var b: String = ""
  }

  @Test fun constructorParameterWithJsonName() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ConstructorParameterWithJsonName>()

    val encoded = ConstructorParameterWithJsonName(
        3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"key a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"key a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class ConstructorParameterWithJsonName(@Json(name = "key a") var a: Int, var b: Int)

  @Test fun propertyWithJsonName() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<PropertyWithJsonName>()

    val encoded = PropertyWithJsonName()
    encoded.a = 3
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"key a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"key a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class PropertyWithJsonName {
    @Json(name = "key a") var a: Int = -1
    var b: Int = -1
  }

  @Test fun transientConstructorParameter() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<TransientConstructorParameter>()

    val encoded = TransientConstructorParameter(
        3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class TransientConstructorParameter(@Transient var a: Int = -1, var b: Int = -1)

  @Test fun multipleTransientConstructorParameters() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(MultipleTransientConstructorParameters::class.java)

    val encoded = MultipleTransientConstructorParameters(3, 5, 7)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
    assertThat(decoded.c).isEqualTo(-1)
  }

  @JsonClass(generateAdapter = true)
  class MultipleTransientConstructorParameters(@Transient var a: Int = -1, var b: Int = -1, @Transient var c: Int = -1)

  @Test fun transientProperty() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<TransientProperty>()

    val encoded = TransientProperty()
    encoded.a = 3
    encoded.setB(4)
    encoded.c = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"c":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":5,"c":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.getB()).isEqualTo(-1)
    assertThat(decoded.c).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class TransientProperty {
    @Transient var a: Int = -1
    @Transient private var b: Int = -1
    var c: Int = -1

    fun getB() = b

    fun setB(b: Int) {
      this.b = b
    }
  }

  @Test fun transientDelegateProperty() {
    val jsonAdapter = moshi.adapter<TransientDelegateProperty>()

    val encoded = TransientDelegateProperty()
    encoded.a = 3
    encoded.setB(4)
    encoded.c = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"c":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":5,"c":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.getB()).isEqualTo(-1)
    assertThat(decoded.c).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class TransientDelegateProperty {

    private fun <T>delegate(initial: T) = Delegates.observable(initial) { _, _, _-> }

    @delegate:Transient var a: Int by delegate(-1)
    @delegate:Transient private var b: Int by delegate(-1)
    var c: Int by delegate(-1)

    @JvmName("getBPublic")
    fun getB() = b
    @JvmName("setBPublic")
    fun setB(b: Int) {
      this.b = b
    }
  }

  @Test fun manyProperties32() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ManyProperties32>()

    val encoded = ManyProperties32(
        101, 102, 103, 104, 105,
        106, 107, 108, 109, 110,
        111, 112, 113, 114, 115,
        116, 117, 118, 119, 120,
        121, 122, 123, 124, 125,
        126, 127, 128, 129, 130,
        131, 132)
    val json = ("""
        |{
        |"v01":101,"v02":102,"v03":103,"v04":104,"v05":105,
        |"v06":106,"v07":107,"v08":108,"v09":109,"v10":110,
        |"v11":111,"v12":112,"v13":113,"v14":114,"v15":115,
        |"v16":116,"v17":117,"v18":118,"v19":119,"v20":120,
        |"v21":121,"v22":122,"v23":123,"v24":124,"v25":125,
        |"v26":126,"v27":127,"v28":128,"v29":129,"v30":130,
        |"v31":131,"v32":132
        |}
        |""").trimMargin().replace("\n", "")

    assertThat(jsonAdapter.toJson(encoded)).isEqualTo(json)

    val decoded = jsonAdapter.fromJson(json)!!
    assertThat(decoded.v01).isEqualTo(101)
    assertThat(decoded.v32).isEqualTo(132)
  }

  @JsonClass(generateAdapter = true)
  class ManyProperties32(
    var v01: Int, var v02: Int, var v03: Int, var v04: Int, var v05: Int,
    var v06: Int, var v07: Int, var v08: Int, var v09: Int, var v10: Int,
    var v11: Int, var v12: Int, var v13: Int, var v14: Int, var v15: Int,
    var v16: Int, var v17: Int, var v18: Int, var v19: Int, var v20: Int,
    var v21: Int, var v22: Int, var v23: Int, var v24: Int, var v25: Int,
    var v26: Int, var v27: Int, var v28: Int, var v29: Int, var v30: Int,
    var v31: Int, var v32: Int)

  @Test fun manyProperties33() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ManyProperties33>()

    val encoded = ManyProperties33(
        101, 102, 103, 104, 105,
        106, 107, 108, 109, 110,
        111, 112, 113, 114, 115,
        116, 117, 118, 119, 120,
        121, 122, 123, 124, 125,
        126, 127, 128, 129, 130,
        131, 132, 133)
    val json = ("""
        |{
        |"v01":101,"v02":102,"v03":103,"v04":104,"v05":105,
        |"v06":106,"v07":107,"v08":108,"v09":109,"v10":110,
        |"v11":111,"v12":112,"v13":113,"v14":114,"v15":115,
        |"v16":116,"v17":117,"v18":118,"v19":119,"v20":120,
        |"v21":121,"v22":122,"v23":123,"v24":124,"v25":125,
        |"v26":126,"v27":127,"v28":128,"v29":129,"v30":130,
        |"v31":131,"v32":132,"v33":133
        |}
        |""").trimMargin().replace("\n", "")

    assertThat(jsonAdapter.toJson(encoded)).isEqualTo(json)

    val decoded = jsonAdapter.fromJson(json)!!
    assertThat(decoded.v01).isEqualTo(101)
    assertThat(decoded.v32).isEqualTo(132)
    assertThat(decoded.v33).isEqualTo(133)
  }

  @JsonClass(generateAdapter = true)
  class ManyProperties33(
    var v01: Int, var v02: Int, var v03: Int, var v04: Int, var v05: Int,
    var v06: Int, var v07: Int, var v08: Int, var v09: Int, var v10: Int,
    var v11: Int, var v12: Int, var v13: Int, var v14: Int, var v15: Int,
    var v16: Int, var v17: Int, var v18: Int, var v19: Int, var v20: Int,
    var v21: Int, var v22: Int, var v23: Int, var v24: Int, var v25: Int,
    var v26: Int, var v27: Int, var v28: Int, var v29: Int, var v30: Int,
    var v31: Int, var v32: Int, var v33: Int)

  @Test fun unsettablePropertyIgnored() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<UnsettableProperty>()

    val encoded = UnsettableProperty()
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class UnsettableProperty {
    val a: Int = -1
    var b: Int = -1
  }

  @Test fun getterOnlyNoBackingField() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<GetterOnly>()

    val encoded = GetterOnly(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
    assertThat(decoded.total).isEqualTo(10)
  }

  @JsonClass(generateAdapter = true)
  class GetterOnly(var a: Int, var b: Int) {
    val total : Int
      get() = a + b
  }

  @Test fun getterAndSetterNoBackingField() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<GetterAndSetter>()

    val encoded = GetterAndSetter(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5,"total":8}""")

    // Whether b is 6 or 7 is an implementation detail. Currently we call constructors then setters.
    val decoded1 = jsonAdapter.fromJson("""{"a":4,"b":6,"total":11}""")!!
    assertThat(decoded1.a).isEqualTo(4)
    assertThat(decoded1.b).isEqualTo(7)
    assertThat(decoded1.total).isEqualTo(11)

    // Whether b is 6 or 7 is an implementation detail. Currently we call constructors then setters.
    val decoded2 = jsonAdapter.fromJson("""{"a":4,"total":11,"b":6}""")!!
    assertThat(decoded2.a).isEqualTo(4)
    assertThat(decoded2.b).isEqualTo(7)
    assertThat(decoded2.total).isEqualTo(11)
  }

  @JsonClass(generateAdapter = true)
  class GetterAndSetter(var a: Int, var b: Int) {
    var total : Int
      get() = a + b
      set(value) {
        b = value - a
      }
  }

  @Test fun supertypeConstructorParameters() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<SubtypeConstructorParameters>()

    val encoded = SubtypeConstructorParameters(
        3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  open class SupertypeConstructorParameters(var a: Int)

  @JsonClass(generateAdapter = true)
  class SubtypeConstructorParameters(a: Int, var b: Int) : SupertypeConstructorParameters(a)

  @Test fun supertypeProperties() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<SubtypeProperties>()

    val encoded = SubtypeProperties()
    encoded.a = 3
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5,"a":3}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  open class SupertypeProperties {
    var a: Int = -1
  }

  @JsonClass(generateAdapter = true)
  class SubtypeProperties : SupertypeProperties() {
    var b: Int = -1
  }

  /** Generated adapters don't track enough state to detect duplicated values. */
  @Ignore @Test fun duplicatedValueParameter() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<DuplicateValueParameter>()

    try {
      jsonAdapter.fromJson("""{"a":4,"a":4}""")
      fail()
    } catch(expected: JsonDataException) {
      assertThat(expected).hasMessage("Multiple values for 'a' at $.a")
    }
  }

  class DuplicateValueParameter(var a: Int = -1, var b: Int = -2)

  /** Generated adapters don't track enough state to detect duplicated values. */
  @Ignore @Test fun duplicatedValueProperty() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<DuplicateValueProperty>()

    try {
      jsonAdapter.fromJson("""{"a":4,"a":4}""")
      fail()
    } catch(expected: JsonDataException) {
      assertThat(expected).hasMessage("Multiple values for 'a' at $.a")
    }
  }

  class DuplicateValueProperty {
    var a: Int = -1
    var b: Int = -2
  }

  @Test fun extensionProperty() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter<ExtensionProperty>()

    val encoded = ExtensionProperty(3)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
  }

  @JsonClass(generateAdapter = true)
  class ExtensionProperty(var a: Int)

  var ExtensionProperty.b: Int
    get() {
      throw AssertionError()
    }
    set(value) {
      throw AssertionError()
    }

  /** https://github.com/square/moshi/issues/563 */
  @Test fun qualifiedAdaptersAreShared() {
    val moshi = Moshi.Builder()
        .add(UppercaseJsonAdapter())
        .build()
    val jsonAdapter = moshi.adapter<MultiplePropertiesShareAdapter>()

    val encoded = MultiplePropertiesShareAdapter(
        "Android", "Banana")
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":"ANDROID","b":"BANANA"}""")

    val delegateAdapters = GeneratedAdaptersTest_MultiplePropertiesShareAdapterJsonAdapter::class
        .memberProperties.filter {
      it.returnType.classifier == JsonAdapter::class
    }
    assertThat(delegateAdapters).hasSize(1)
  }

  @JsonClass(generateAdapter = true)
  class MultiplePropertiesShareAdapter(
    @Uppercase(true) var a: String,
    @Uppercase(true) var b: String
  )

  @Test fun toJsonOnly() {
    val moshi = Moshi.Builder()
        .add(CustomToJsonOnlyAdapter())
        .build()
    val jsonAdapter = moshi.adapter<CustomToJsonOnly>()

    assertThat(jsonAdapter.toJson(
        CustomToJsonOnly(1, 2))).isEqualTo("""[1,2]""")

    val fromJson = jsonAdapter.fromJson("""{"a":3,"b":4}""")!!
    assertThat(fromJson.a).isEqualTo(3)
    assertThat(fromJson.b).isEqualTo(4)
  }

  @JsonClass(generateAdapter = true)
  class CustomToJsonOnly(var a: Int, var b: Int)

  class CustomToJsonOnlyAdapter {
    @ToJson fun toJson(v: CustomToJsonOnly) : List<Int> {
      return listOf(v.a, v.b)
    }
  }

  @Test fun fromJsonOnly() {
    val moshi = Moshi.Builder()
        .add(CustomFromJsonOnlyAdapter())
        .build()
    val jsonAdapter = moshi.adapter<CustomFromJsonOnly>()

    assertThat(jsonAdapter.toJson(
        CustomFromJsonOnly(1, 2))).isEqualTo("""{"a":1,"b":2}""")

    val fromJson = jsonAdapter.fromJson("""[3,4]""")!!
    assertThat(fromJson.a).isEqualTo(3)
    assertThat(fromJson.b).isEqualTo(4)
  }

  @JsonClass(generateAdapter = true)
  class CustomFromJsonOnly(var a: Int, var b: Int)

  class CustomFromJsonOnlyAdapter {
    @FromJson fun fromJson(v: List<Int>) : CustomFromJsonOnly {
      return CustomFromJsonOnly(v[0], v[1])
    }
  }

  @Test fun privateTransientIsIgnored() {
    val jsonAdapter = moshi.adapter<PrivateTransient>()

    val privateTransient = PrivateTransient()
    privateTransient.writeA(1)
    privateTransient.b = 2
    assertThat(jsonAdapter.toJson(privateTransient)).isEqualTo("""{"b":2}""")

    val fromJson = jsonAdapter.fromJson("""{"a":3,"b":4}""")!!
    assertThat(fromJson.readA()).isEqualTo(-1)
    assertThat(fromJson.b).isEqualTo(4)
  }

  @JsonClass(generateAdapter = true)
  class PrivateTransient {
    @Transient private var a: Int = -1
    var b: Int = -1

    fun readA(): Int {
      return a
    }

    fun writeA(a: Int) {
      this.a = a
    }
  }

  @Test fun propertyIsNothing() {
    val moshi = Moshi.Builder()
        .add(NothingAdapter())
        .build()
    val jsonAdapter = moshi.adapter<HasNothingProperty>().serializeNulls()

    val toJson = HasNothingProperty()
    toJson.a = "1"
    assertThat(jsonAdapter.toJson(toJson)).isEqualTo("""{"a":"1","b":null}""")

    val fromJson = jsonAdapter.fromJson("""{"a":"3","b":null}""")!!
    assertThat(fromJson.a).isEqualTo("3")
    assertNull(fromJson.b)
  }

  class NothingAdapter {
    @ToJson fun toJson(jsonWriter: JsonWriter, unused: Nothing?) {
      jsonWriter.nullValue()
    }

    @FromJson fun fromJson(jsonReader: JsonReader) : Nothing? {
      jsonReader.skipValue()
      return null
    }
  }

  @JsonClass(generateAdapter = true)
  class HasNothingProperty {
    var a: String? = null
    var b: Nothing? = null
  }

  @Test fun enclosedParameterizedType() {
    val jsonAdapter = moshi.adapter<HasParameterizedProperty>()

    assertThat(jsonAdapter.toJson(
        HasParameterizedProperty(
            Twins("1", "2"))))
        .isEqualTo("""{"twins":{"a":"1","b":"2"}}""")

    val hasParameterizedProperty = jsonAdapter.fromJson("""{"twins":{"a":"3","b":"4"}}""")!!
    assertThat(hasParameterizedProperty.twins.a).isEqualTo("3")
    assertThat(hasParameterizedProperty.twins.b).isEqualTo("4")
  }

  @JsonClass(generateAdapter = true)
  class Twins<T>(var a: T, var b: T)

  @JsonClass(generateAdapter = true)
  class HasParameterizedProperty(val twins: Twins<String>)

  @Test fun uppercasePropertyName() {
    val adapter = moshi.adapter<UppercasePropertyName>()

    val instance = adapter.fromJson("""{"AAA":1,"BBB":2}""")!!
    assertThat(instance.AAA).isEqualTo(1)
    assertThat(instance.BBB).isEqualTo(2)

    assertThat(adapter.toJson(
        UppercasePropertyName(3, 4))).isEqualTo("""{"AAA":3,"BBB":4}""")
  }

  @JsonClass(generateAdapter = true)
  class UppercasePropertyName(val AAA: Int, val BBB: Int)

  /** https://github.com/square/moshi/issues/574 */
  @Test fun mutableUppercasePropertyName() {
    val adapter = moshi.adapter<MutableUppercasePropertyName>()

    val instance = adapter.fromJson("""{"AAA":1,"BBB":2}""")!!
    assertThat(instance.AAA).isEqualTo(1)
    assertThat(instance.BBB).isEqualTo(2)

    val value = MutableUppercasePropertyName()
    value.AAA = 3
    value.BBB = 4
    assertThat(adapter.toJson(value)).isEqualTo("""{"AAA":3,"BBB":4}""")
  }

  @JsonClass(generateAdapter = true)
  class MutableUppercasePropertyName {
    var AAA: Int = -1
    var BBB: Int = -1
  }

  @JsonQualifier
  annotation class Uppercase(val inFrench: Boolean, val onSundays: Boolean = false)

  class UppercaseJsonAdapter {
    @ToJson fun toJson(@Uppercase(inFrench = true) s: String) : String {
      return s.toUpperCase(Locale.US)
    }
    @FromJson @Uppercase(inFrench = true) fun fromJson(s: String) : String {
      return s.toLowerCase(Locale.US)
    }
  }

  @JsonClass(generateAdapter = true)
  data class HasNullableBoolean(val boolean: Boolean?)

  @Test fun nullablePrimitivesUseBoxedPrimitiveAdapters() {
    val moshi = Moshi.Builder()
        .add(JsonAdapter.Factory { type, _, _ ->
          if (Boolean::class.javaObjectType == type) {
            return@Factory object:JsonAdapter<Boolean?>() {
              override fun fromJson(reader: JsonReader): Boolean? {
                if (reader.peek() != JsonReader.Token.BOOLEAN) {
                  reader.skipValue()
                  return null
                }
                return reader.nextBoolean()
              }

              override fun toJson(writer: JsonWriter, value: Boolean?) {
                writer.value(value)
              }
            }
          }
          null
        })
        .build()
    val adapter = moshi.adapter<HasNullableBoolean>().serializeNulls()
    assertThat(adapter.fromJson("""{"boolean":"not a boolean"}"""))
        .isEqualTo(HasNullableBoolean(null))
    assertThat(adapter.toJson(
        HasNullableBoolean(null))).isEqualTo("""{"boolean":null}""")
  }

  @Test fun adaptersAreNullSafe() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<HasNullableBoolean>()
    assertThat(adapter.fromJson("null")).isNull()
    assertThat(adapter.toJson(null)).isEqualTo("null")
  }

  @JsonClass(generateAdapter = true)
  data class HasCollectionOfPrimitives(val listOfInts: List<Int>)

  @Test fun hasCollectionOfPrimitives() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<HasCollectionOfPrimitives>()

    val encoded = HasCollectionOfPrimitives(
        listOf(1, 2, -3))
    assertThat(adapter.toJson(encoded)).isEqualTo("""{"listOfInts":[1,2,-3]}""")

    val decoded = adapter.fromJson("""{"listOfInts":[4,-5,6]}""")!!
    assertThat(decoded).isEqualTo(
        HasCollectionOfPrimitives(
            listOf(4, -5, 6)))
  }

  @JsonClass(generateAdapter = true, generator = "custom")
  data class CustomGeneratedClass(val foo: String)

  @Test fun customGenerator_withClassPresent() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<CustomGeneratedClass>()
    val unwrapped = (adapter as NullSafeJsonAdapter<CustomGeneratedClass>).delegate()
    assertThat(unwrapped).isInstanceOf(
        GeneratedAdaptersTest_CustomGeneratedClassJsonAdapter::class.java)
  }

  @JsonClass(generateAdapter = true, generator = "custom")
  data class CustomGeneratedClassMissing(val foo: String)

  @Test fun customGenerator_withClassMissing() {
    val moshi = Moshi.Builder().build()
    try {
      moshi.adapter<CustomGeneratedClassMissing>()
      fail()
    } catch (e: RuntimeException) {
      assertThat(e).hasMessageContaining("Failed to find the generated JsonAdapter class")
    }
  }

  // https://github.com/square/moshi/issues/921
  @Test fun internalPropertyWithoutBackingField() {
    val adapter = moshi.adapter<InternalPropertyWithoutBackingField>()

    val test = InternalPropertyWithoutBackingField()
    assertThat(adapter.toJson(test)).isEqualTo("""{"bar":5}""")

    assertThat(adapter.fromJson("""{"bar":6}""")!!.bar).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class InternalPropertyWithoutBackingField {

    @Transient
    private var foo: Int = 5

    internal var bar
      get() = foo
      set(f) {
        foo = f
      }
  }

  @JsonClass(generateAdapter = true)
  data class ClassWithFieldJson(
      @field:Json(name = "_links") val links: String
  ) {
    @field:Json(name = "_ids") var ids: String? = null
  }

  // Regression test to ensure annotations with field site targets still use the right name
  @Test fun classWithFieldJsonTargets() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<ClassWithFieldJson>()
    //language=JSON
    val instance = adapter.fromJson("""{"_links": "link", "_ids": "id" }""")!!
    assertThat(instance).isEqualTo(ClassWithFieldJson("link").apply { ids = "id" })
  }

  /*
   * These are a smoke test for https://github.com/square/moshi/issues/1023 to ensure that we
   * suppress deprecation warnings for using deprecated properties or classes.
   *
   * Ideally when stubs are fixed to actually included Deprecated annotations, we could then only
   * generate a deprecation suppression as needed and on targeted usages.
   * https://youtrack.jetbrains.com/issue/KT-34951
   */

  @Deprecated("Deprecated for reasons")
  @JsonClass(generateAdapter = true)
  data class DeprecatedClass(val foo: String)

  @JsonClass(generateAdapter = true)
  data class DeprecatedProperty(@Deprecated("Deprecated for reasons") val foo: String)


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

  @Test fun typesSizeCheckMessages_noArgs() {
    try {
      moshi.adapter(MultipleGenerics::class.java)
      fail("Should have failed to construct the adapter due to missing generics")
    } catch (e: RuntimeException) {
      assertThat(e).hasMessage("Failed to find the generated JsonAdapter constructor for 'class com.squareup.moshi.kotlin.codegen.GeneratedAdaptersTest\$MultipleGenerics'. Suspiciously, the type was not parameterized but the target class 'com.squareup.moshi.kotlin.codegen.GeneratedAdaptersTest_MultipleGenericsJsonAdapter' is generic. Consider using Types#newParameterizedType() to define these missing type variables.")
    }
  }

  @Test fun typesSizeCheckMessages_wrongNumberOfArgs() {
    try {
      GeneratedAdaptersTest_MultipleGenericsJsonAdapter<String, Any, Any, Any>(
          moshi,
          arrayOf(String::class.java)
      )
      fail("Should have failed to construct the adapter due to wrong number of generics")
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("TypeVariable mismatch: Expecting 4 types for generic type variables [A, B, C, D], but received 1")
    }
  }

  @JsonClass(generateAdapter = true)
  data class MultipleGenerics<A, B, C, D>(val prop: String)
}

// Regression test for https://github.com/square/moshi/issues/1022
// Compile-only test
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
// Compile-only test
@JsonClass(generateAdapter = true)
data class KeysWithSpaces(
    @Json(name = "1. Information") val information: String,
    @Json(name = "2. Symbol") val symbol: String,
    @Json(name = "3. Last Refreshed") val lastRefreshed: String,
    @Json(name = "4. Interval") val interval: String,
    @Json(name = "5. Output Size") val size: String,
    @Json(name = "6. Time Zone") val timeZone: String
)

// Has to be outside to avoid Types seeing an owning class
@JsonClass(generateAdapter = true)
data class NullableTypeParams<T>(
    val nullableList: List<String?>,
    val nullableSet: Set<String?>,
    val nullableMap: Map<String, String?>,
    val nullableT: T?,
    val nonNullT: T
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
  val genericAlias: GenericTypeAlias = listOf("Woah")
)

// Compile only, regression test for https://github.com/square/moshi/issues/848
@JsonClass(generateAdapter = true)
data class Hotwords(
    val `class`: List<String>?
)

typealias TypeAliasName = String
typealias GenericTypeAlias = List<String>
