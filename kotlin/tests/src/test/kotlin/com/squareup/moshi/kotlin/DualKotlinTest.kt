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
package com.squareup.moshi.kotlin

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.intellij.lang.annotations.Language
import org.junit.Assert.fail
import org.junit.Test
import kotlin.annotation.AnnotationRetention.RUNTIME

class DualKotlinTest {

  @Suppress("UNCHECKED_CAST")
  private val moshi = Moshi.Builder()
    // If code gen ran, the generated adapter will be tried first. If it can't find it, it will
    // gracefully fall back to the KotlinJsonAdapter. This allows us to easily test both.
    .addLast(KotlinJsonAdapterFactory())
    .build()

  @Test fun requiredValueAbsent() {
    val jsonAdapter = moshi.adapter<RequiredValueAbsent>()

    try {
      //language=JSON
      jsonAdapter.fromJson("""{"a":4}""")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Required value 'b' missing at $")
    }
  }

  @JsonClass(generateAdapter = true)
  class RequiredValueAbsent(var a: Int = 3, var b: Int)

  @Test fun requiredValueWithDifferentJsonNameAbsent() {
    val jsonAdapter = moshi.adapter<RequiredValueWithDifferentJsonNameAbsent>()

    try {
      //language=JSON
      jsonAdapter.fromJson("""{"a":4}""")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Required value 'b' (JSON name 'bPrime') missing at \$")
    }
  }

  @JsonClass(generateAdapter = true)
  class RequiredValueWithDifferentJsonNameAbsent(var a: Int = 3, @Json(name = "bPrime") var b: Int)

  @Test fun nonNullPropertySetToNullFailsWithJsonDataException() {
    val jsonAdapter = moshi.adapter<HasNonNullProperty>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":null}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Non-null value 'a' was null at \$.a")
    }
  }

  @Test fun nonNullPropertySetToNullFromAdapterFailsWithJsonDataException() {
    val jsonAdapter = moshi.newBuilder()
      .add(
        object {
          @Suppress("UNUSED_PARAMETER")
          @FromJson
          fun fromJson(string: String): String? = null
        }
      )
      .build()
      .adapter<HasNonNullProperty>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":\"hello\"}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Non-null value 'a' was null at \$.a")
    }
  }

  @JsonClass(generateAdapter = true)
  class HasNonNullProperty {
    var a: String = ""
  }

  @Test fun nonNullPropertyWithJsonNameSetToNullFailsWithJsonDataException() {
    val jsonAdapter = moshi.adapter<HasNonNullPropertyDifferentJsonName>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"aPrime\":null}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Non-null value 'a' (JSON name 'aPrime') was null at \$.aPrime")
    }
  }

  @Test fun nonNullPropertyWithJsonNameSetToNullFromAdapterFailsWithJsonDataException() {
    val jsonAdapter = moshi.newBuilder()
      .add(
        object {
          @Suppress("UNUSED_PARAMETER")
          @FromJson
          fun fromJson(string: String): String? = null
        }
      )
      .build()
      .adapter<HasNonNullPropertyDifferentJsonName>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"aPrime\":\"hello\"}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Non-null value 'a' (JSON name 'aPrime') was null at \$.aPrime")
    }
  }

  @JsonClass(generateAdapter = true)
  class HasNonNullPropertyDifferentJsonName {
    @Json(name = "aPrime") var a: String = ""
  }

  @Test fun nonNullConstructorParameterCalledWithNullFailsWithJsonDataException() {
    val jsonAdapter = moshi.adapter<HasNonNullConstructorParameter>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":null}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Non-null value 'a' was null at \$.a")
    }
  }

  @Test fun nonNullConstructorParameterCalledWithNullFromAdapterFailsWithJsonDataException() {
    val jsonAdapter = moshi.newBuilder()
      .add(
        object {
          @Suppress("UNUSED_PARAMETER")
          @FromJson
          fun fromJson(string: String): String? = null
        }
      )
      .build()
      .adapter<HasNonNullConstructorParameter>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":\"hello\"}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessageThat().isEqualTo("Non-null value 'a' was null at \$.a")
    }
  }

  @Retention(RUNTIME)
  annotation class Nullable

  @JsonClass(generateAdapter = true)
  data class HasNonNullConstructorParameter(val a: String)

  @JsonClass(generateAdapter = true)
  data class HasNullableConstructorParameter(val a: String?)

  @Test fun delegatesToInstalledAdaptersBeforeNullChecking() {
    val localMoshi = moshi.newBuilder()
      .add(
        object {
          @FromJson
          fun fromJson(@Nullable string: String?): String {
            return string ?: "fallback"
          }

          @ToJson
          fun toJson(@Nullable value: String?): String {
            return value ?: "fallback"
          }
        }
      )
      .build()

    val hasNonNullConstructorParameterAdapter =
      localMoshi.adapter<HasNonNullConstructorParameter>()
    assertThat(
      //language=JSON
      hasNonNullConstructorParameterAdapter
        .fromJson("{\"a\":null}")
    ).isEqualTo(HasNonNullConstructorParameter("fallback"))

    val hasNullableConstructorParameterAdapter =
      localMoshi.adapter<HasNullableConstructorParameter>()
    assertThat(
      //language=JSON
      hasNullableConstructorParameterAdapter
        .fromJson("{\"a\":null}")
    ).isEqualTo(HasNullableConstructorParameter("fallback"))
    //language=JSON
    assertThat(
      hasNullableConstructorParameterAdapter
        .toJson(HasNullableConstructorParameter(null))
    ).isEqualTo("{\"a\":\"fallback\"}")
  }

  @JsonClass(generateAdapter = true)
  data class HasNullableTypeWithGeneratedAdapter(val a: HasNonNullConstructorParameter?)

  @Test fun delegatesToInstalledAdaptersBeforeNullCheckingWithGeneratedAdapter() {
    val adapter = moshi.adapter<HasNullableTypeWithGeneratedAdapter>()

    val encoded = HasNullableTypeWithGeneratedAdapter(null)
    //language=JSON
    assertThat(adapter.toJson(encoded)).isEqualTo("""{}""")
    //language=JSON
    assertThat(adapter.serializeNulls().toJson(encoded)).isEqualTo("""{"a":null}""")

    //language=JSON
    val decoded = adapter.fromJson("""{"a":null}""")!!
    assertThat(decoded.a).isEqualTo(null)
  }

  @Test fun valueClass() {
    val adapter = moshi.adapter<ValueClass>()

    val inline = ValueClass(5)

    val expectedJson =
      """{"i":5}"""
    assertThat(adapter.toJson(inline)).isEqualTo(expectedJson)

    val testJson =
      """{"i":6}"""
    val result = adapter.fromJson(testJson)!!
    assertThat(result.i).isEqualTo(6)

    // TODO doesn't work yet.
    //  need to invoke the constructor_impl$default static method, invoke constructor with result
//    val testEmptyJson =
//      """{}"""
//    val result2 = adapter.fromJson(testEmptyJson)!!
//    assertThat(result2.i).isEqualTo(0)
  }

  @JsonClass(generateAdapter = true)
  data class InlineConsumer(val inline: ValueClass)

  @Test fun inlineClassConsumer() {
    val adapter = moshi.adapter<InlineConsumer>()

    val consumer = InlineConsumer(ValueClass(23))

    @Language("JSON")
    val expectedJson =
      """{"inline":{"i":23}}"""
    assertThat(adapter.toJson(consumer)).isEqualTo(expectedJson)

    @Language("JSON")
    val testJson =
      """{"inline":{"i":42}}"""
    val result = adapter.fromJson(testJson)!!
    assertThat(result.inline.i).isEqualTo(42)
  }

  // Regression test for https://github.com/square/moshi/issues/955
  @Test fun backwardReferencingTypeVars() {
    val adapter = moshi.adapter<TextAssetMetaData>()

    @Language("JSON")
    val testJson =
      """{"text":"text"}"""

    assertThat(adapter.toJson(TextAssetMetaData("text"))).isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result.text).isEqualTo("text")
  }

  @JsonClass(generateAdapter = true)
  class TextAssetMetaData(val text: String) : AssetMetaData<TextAsset>()
  class TextAsset : Asset<TextAsset>()
  abstract class Asset<A : Asset<A>>
  abstract class AssetMetaData<A : Asset<A>>

  // Regression test for https://github.com/ZacSweers/MoshiX/issues/125
  @Test fun selfReferencingTypeVars() {
    val adapter = moshi.adapter<StringNodeNumberNode>()

    val data = StringNodeNumberNode().also {
      it.t = StringNodeNumberNode().also {
        it.text = "child 1"
      }
      it.text = "root"
      it.r = NumberStringNode().also {
        it.number = 0
        it.t = NumberStringNode().also {
          it.number = 1
        }
        it.r = StringNodeNumberNode().also {
          it.text = "grand child 1"
        }
      }
    }

    assertThat(adapter.toJson(data))
      //language=JSON
      .isEqualTo(
        """
        {"text":"root","r":{"number":0,"r":{"text":"grand child 1"},"t":{"number":1}},"t":{"text":"child 1"}}
        """.trimIndent()
      )
  }

  @JsonClass(generateAdapter = true)
  open class Node<T : Node<T, R>, R : Node<R, T>> {
    // kotlin-reflect doesn't preserve ordering, so put these in alphabetical order so that
    // both reflective and code gen tests work the same
    var r: R? = null
    var t: T? = null
  }

  @JsonClass(generateAdapter = true)
  class StringNodeNumberNode : Node<StringNodeNumberNode, NumberStringNode>() {
    var text: String = ""
  }

  @JsonClass(generateAdapter = true)
  class NumberStringNode : Node<NumberStringNode, StringNodeNumberNode>() {
    var number: Int = 0
  }

  // Regression test for https://github.com/square/moshi/issues/968
  @Test fun abstractSuperProperties() {
    val adapter = moshi.adapter<InternalAbstractProperty>()

    @Language("JSON")
    val testJson =
      """{"test":"text"}"""

    assertThat(adapter.toJson(InternalAbstractProperty("text"))).isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result.test).isEqualTo("text")
  }

  abstract class InternalAbstractPropertyBase {
    internal abstract val test: String

    // Regression for https://github.com/square/moshi/issues/974
    abstract fun abstractFun(): String
  }

  @JsonClass(generateAdapter = true)
  class InternalAbstractProperty(override val test: String) : InternalAbstractPropertyBase() {
    override fun abstractFun(): String {
      return test
    }
  }

  // Regression test for https://github.com/square/moshi/issues/975
  @Test fun multipleConstructors() {
    val adapter = moshi.adapter<MultipleConstructorsB>()

    //language=JSON
    assertThat(adapter.toJson(MultipleConstructorsB(6))).isEqualTo("""{"f":{"f":6},"b":6}""")

    @Language("JSON")
    val testJson =
      """{"b":6}"""
    val result = adapter.fromJson(testJson)!!
    assertThat(result.b).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  class MultipleConstructorsA(val f: Int)

  @JsonClass(generateAdapter = true)
  class MultipleConstructorsB(val f: MultipleConstructorsA = MultipleConstructorsA(5), val b: Int) {
    constructor(f: Int, b: Int = 6) : this(MultipleConstructorsA(f), b)
  }

  @Test fun `multiple non-property parameters`() {
    val adapter = moshi.adapter<MultipleNonPropertyParameters>()

    @Language("JSON")
    val testJson =
      """{"prop":7}"""

    assertThat(adapter.toJson(MultipleNonPropertyParameters(7))).isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result.prop).isEqualTo(7)
  }

  @JsonClass(generateAdapter = true)
  class MultipleNonPropertyParameters(
    val prop: Int,
    param1: Int = 1,
    param2: Int = 2
  ) {
    init {
      // Ensure the params always uses their default value
      require(param1 == 1)
      require(param2 == 2)
    }
  }

  // Tests the case of multiple parameters with no parameter properties.
  @Test fun `only multiple non-property parameters`() {
    val adapter = moshi.adapter<OnlyMultipleNonPropertyParameters>()

    @Language("JSON")
    val testJson =
      """{"prop":7}"""

    assertThat(adapter.toJson(OnlyMultipleNonPropertyParameters().apply { prop = 7 }))
      .isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result.prop).isEqualTo(7)
  }

  @JsonClass(generateAdapter = true)
  class OnlyMultipleNonPropertyParameters(
    param1: Int = 1,
    param2: Int = 2
  ) {
    init {
      // Ensure the params always uses their default value
      require(param1 == 1)
      require(param2 == 2)
    }

    var prop: Int = 0
  }

  @Test fun typeAliasUnwrapping() {
    val adapter = moshi
      .newBuilder()
      .add(Types.supertypeOf(Int::class.javaObjectType), moshi.adapter<Int>())
      .build()
      .adapter<TypeAliasUnwrapping>()

    @Language("JSON")
    val testJson =
      """{"simpleClass":6,"parameterized":{"value":6},"wildcardIn":{"value":6},"wildcardOut":{"value":6},"complex":{"value":[{"value":6}]}}"""

    val testValue = TypeAliasUnwrapping(
      simpleClass = 6,
      parameterized = GenericClass(6),
      wildcardIn = GenericClass(6),
      wildcardOut = GenericClass(6),
      complex = GenericClass(listOf(GenericClass(6)))
    )
    assertThat(adapter.toJson(testValue)).isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result).isEqualTo(testValue)
  }

  @JsonClass(generateAdapter = true)
  data class TypeAliasUnwrapping(
    val simpleClass: TypeAlias,
    val parameterized: GenericClass<TypeAlias>,
    val wildcardIn: GenericClass<in TypeAlias>,
    val wildcardOut: GenericClass<out TypeAlias>,
    val complex: GenericClass<GenericTypeAlias>?
  )

  // Regression test for https://github.com/square/moshi/issues/991
  @Test fun nullablePrimitiveProperties() {
    val adapter = moshi.adapter<NullablePrimitives>()

    @Language("JSON")
    val testJson =
      """{"objectType":"value","boolean":true,"byte":3,"char":"a","short":3,"int":3,"long":3,"float":3.2,"double":3.2}"""

    val instance = NullablePrimitives(
      objectType = "value",
      boolean = true,
      byte = 3,
      char = 'a',
      short = 3,
      int = 3,
      long = 3,
      float = 3.2f,
      double = 3.2
    )
    assertThat(adapter.toJson(instance))
      .isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result).isEqualTo(instance)
  }

  @JsonClass(generateAdapter = true)
  data class NullablePrimitives(
    val objectType: String = "",
    val boolean: Boolean,
    val nullableBoolean: Boolean? = null,
    val byte: Byte,
    val nullableByte: Byte? = null,
    val char: Char,
    val nullableChar: Char? = null,
    val short: Short,
    val nullableShort: Short? = null,
    val int: Int,
    val nullableInt: Int? = null,
    val long: Long,
    val nullableLong: Long? = null,
    val float: Float,
    val nullableFloat: Float? = null,
    val double: Double,
    val nullableDouble: Double? = null
  )

  // Regression test for https://github.com/square/moshi/issues/990
  @Test fun nullableProperties() {
    val adapter = moshi.adapter<NullableList>()

    @Language("JSON")
    val testJson =
      """{"nullableList":null}"""

    assertThat(adapter.serializeNulls().toJson(NullableList(null)))
      .isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result.nullableList).isNull()
  }

  @JsonClass(generateAdapter = true)
  data class NullableList(val nullableList: List<Any>?)

  @Test fun typeAliasNullability() {
    val adapter = moshi.adapter<TypeAliasNullability>()

    @Language("JSON")
    val testJson =
      """{"aShouldBeNonNull":3,"nullableAShouldBeNullable":null,"redundantNullableAShouldBeNullable":null,"manuallyNullableAShouldBeNullable":null,"convolutedMultiNullableShouldBeNullable":null,"deepNestedNullableShouldBeNullable":null}"""

    val instance = TypeAliasNullability(3, null, null, null, null, null)
    assertThat(adapter.serializeNulls().toJson(instance))
      .isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result).isEqualTo(instance)
  }

  @Suppress("REDUNDANT_NULLABLE")
  @JsonClass(generateAdapter = true)
  data class TypeAliasNullability(
    val aShouldBeNonNull: A,
    val nullableAShouldBeNullable: NullableA,
    val redundantNullableAShouldBeNullable: NullableA?,
    val manuallyNullableAShouldBeNullable: A?,
    val convolutedMultiNullableShouldBeNullable: NullableB?,
    val deepNestedNullableShouldBeNullable: E
  )

  // Regression test for https://github.com/square/moshi/issues/1009
  @Test fun outDeclaration() {
    val adapter = moshi.adapter<OutDeclaration<Int>>()

    @Language("JSON")
    val testJson =
      """{"input":3}"""

    val instance = OutDeclaration(3)
    assertThat(adapter.serializeNulls().toJson(instance))
      .isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result).isEqualTo(instance)
  }

  @JsonClass(generateAdapter = true)
  data class OutDeclaration<out T>(val input: T)

  @Test fun intersectionTypes() {
    val adapter = moshi.adapter<IntersectionTypes<IntersectionTypesEnum>>()

    @Language("JSON")
    val testJson =
      """{"value":"VALUE"}"""

    val instance = IntersectionTypes(IntersectionTypesEnum.VALUE)
    assertThat(adapter.serializeNulls().toJson(instance))
      .isEqualTo(testJson)

    val result = adapter.fromJson(testJson)!!
    assertThat(result).isEqualTo(instance)
  }

  interface IntersectionTypeInterface<E : Enum<E>>

  enum class IntersectionTypesEnum : IntersectionTypeInterface<IntersectionTypesEnum> {
    VALUE
  }

  @JsonClass(generateAdapter = true)
  data class IntersectionTypes<E>(
    val value: E
  ) where E : Enum<E>, E : IntersectionTypeInterface<E>

  @Test fun transientConstructorParameter() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<TransientConstructorParameter>()

    val encoded = TransientConstructorParameter(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
  }

  class TransientConstructorParameter(@Transient var a: Int = -1, var b: Int = -1)

  @Test fun multipleTransientConstructorParameters() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter(MultipleTransientConstructorParameters::class.java)

    val encoded = MultipleTransientConstructorParameters(3, 5, 7)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
    assertThat(decoded.c).isEqualTo(-1)
  }

  class MultipleTransientConstructorParameters(@Transient var a: Int = -1, var b: Int = -1, @Transient var c: Int = -1)

  @Test fun transientProperty() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

  class TransientProperty {
    @Transient var a: Int = -1
    @Transient private var b: Int = -1
    var c: Int = -1

    fun getB() = b

    fun setB(b: Int) {
      this.b = b
    }
  }

  @Test fun ignoredConstructorParameter() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<IgnoredConstructorParameter>()

    val encoded = IgnoredConstructorParameter(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
  }

  class IgnoredConstructorParameter(@Json(ignore = true) var a: Int = -1, var b: Int = -1)

  @Test fun multipleIgnoredConstructorParameters() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter(MultipleIgnoredConstructorParameters::class.java)

    val encoded = MultipleIgnoredConstructorParameters(3, 5, 7)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
    assertThat(decoded.c).isEqualTo(-1)
  }

  class MultipleIgnoredConstructorParameters(
    @Json(ignore = true) var a: Int = -1,
    var b: Int = -1,
    @Json(ignore = true) var c: Int = -1
  )

  @Test fun ignoredProperty() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<IgnoredProperty>()

    val encoded = IgnoredProperty()
    encoded.a = 3
    encoded.setB(4)
    encoded.c = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"c":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":5,"c":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.getB()).isEqualTo(-1)
    assertThat(decoded.c).isEqualTo(6)
  }

  class IgnoredProperty {
    @Json(ignore = true) var a: Int = -1
    @Json(ignore = true) private var b: Int = -1
    var c: Int = -1

    fun getB() = b

    fun setB(b: Int) {
      this.b = b
    }
  }
}

typealias TypeAlias = Int
@Suppress("REDUNDANT_PROJECTION")
typealias GenericTypeAlias = List<out GenericClass<in TypeAlias>?>?

@JsonClass(generateAdapter = true)
data class GenericClass<T>(val value: T)

// Has to be outside since value classes are only allowed on top level
@JvmInline
@JsonClass(generateAdapter = true)
value class ValueClass(val i: Int = 0)

typealias A = Int
typealias NullableA = A?
typealias B = NullableA
typealias NullableB = B?
typealias C = NullableA
typealias D = C
typealias E = D
