/*
 * Copyright (C) 2017 Square, Inc.
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
package com.squareup.moshi.kotlin.reflect

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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import java.util.Locale
import java.util.SimpleTimeZone
import kotlin.annotation.AnnotationRetention.RUNTIME

@Suppress("UNUSED", "UNUSED_PARAMETER")
class KotlinJsonAdapterTest {
  @Test fun constructorParameters() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ConstructorParameters>()

    val encoded = ConstructorParameters(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  class ConstructorParameters(var a: Int, var b: Int)

  @Test fun properties() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<Properties>()

    val encoded = Properties()
    encoded.a = 3
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":3,"b":5}""")!!
    assertThat(decoded.a).isEqualTo(3)
    assertThat(decoded.b).isEqualTo(5)
  }

  class Properties {
    var a: Int = -1
    var b: Int = -1
  }

  @Test fun constructorParametersAndProperties() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ConstructorParametersAndProperties>()

    val encoded = ConstructorParametersAndProperties(3)
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  class ConstructorParametersAndProperties(var a: Int) {
    var b: Int = -1
  }

  @Test fun immutableConstructorParameters() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ImmutableConstructorParameters>()

    val encoded = ImmutableConstructorParameters(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  class ImmutableConstructorParameters(val a: Int, val b: Int)

  @Test fun immutableProperties() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ImmutableProperties>()

    val encoded = ImmutableProperties(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":3,"b":5}""")!!
    assertThat(decoded.a).isEqualTo(3)
    assertThat(decoded.b).isEqualTo(5)
  }

  class ImmutableProperties(a: Int, b: Int) {
    val a = a
    val b = b
  }

  @Test fun constructorDefaults() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ConstructorDefaultValues>()

    val encoded = ConstructorDefaultValues(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
  }

  class ConstructorDefaultValues(var a: Int = -1, var b: Int = -2)

  @Test fun duplicatedValueParameter() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<DuplicateValueParameter>()

    try {
      jsonAdapter.fromJson("""{"a":4,"a":4}""")
      fail()
    } catch(expected: JsonDataException) {
      assertThat(expected).hasMessage("Multiple values for 'a' at $.a")
    }
  }

  class DuplicateValueParameter(var a: Int = -1, var b: Int = -2)

  @Test fun duplicatedValueProperty() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

  @Test fun explicitNull() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ExplicitNull>()

    val encoded = ExplicitNull(null, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")
    assertThat(jsonAdapter.serializeNulls().toJson(encoded)).isEqualTo("""{"a":null,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":null,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(null)
    assertThat(decoded.b).isEqualTo(6)
  }

  class ExplicitNull(var a: Int?, var b: Int?)

  @Test fun absentNull() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<AbsentNull>()

    val encoded = AbsentNull(null, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")
    assertThat(jsonAdapter.serializeNulls().toJson(encoded)).isEqualTo("""{"a":null,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"b":6}""")!!
    assertThat(decoded.a).isNull()
    assertThat(decoded.b).isEqualTo(6)
  }

  class AbsentNull(var a: Int?, var b: Int?)

  @Test fun repeatedValue() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<RepeatedValue>()

    try {
      jsonAdapter.fromJson("""{"a":4,"b":null,"b":6}""")
      fail()
    } catch(expected: JsonDataException) {
      assertThat(expected).hasMessage("Multiple values for 'b' at $.b")
    }
  }

  class RepeatedValue(var a: Int, var b: Int?)

  @Test fun constructorParameterWithQualifier() {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(UppercaseJsonAdapter())
        .build()
    val jsonAdapter = moshi.adapter<ConstructorParameterWithQualifier>()

    val encoded = ConstructorParameterWithQualifier("Android", "Banana")
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":"ANDROID","b":"Banana"}""")

    val decoded = jsonAdapter.fromJson("""{"a":"Android","b":"Banana"}""")!!
    assertThat(decoded.a).isEqualTo("android")
    assertThat(decoded.b).isEqualTo("Banana")
  }

  class ConstructorParameterWithQualifier(@Uppercase var a: String, var b: String)

  @Test fun propertyWithQualifier() {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
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

  class PropertyWithQualifier {
    @Uppercase var a: String = ""
    var b: String = ""
  }

  @Test fun constructorParameterWithJsonName() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ConstructorParameterWithJsonName>()

    val encoded = ConstructorParameterWithJsonName(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"key a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"key a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  class ConstructorParameterWithJsonName(@Json(name = "key a") var a: Int, var b: Int)

  @Test fun propertyWithJsonName() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<PropertyWithJsonName>()

    val encoded = PropertyWithJsonName()
    encoded.a = 3
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"key a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"key a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  class PropertyWithJsonName {
    @Json(name = "key a") var a: Int = -1
    var b: Int = -1
  }

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

  @Test fun requiredTransientConstructorParameterFails() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<RequiredTransientConstructorParameter>()
      fail()
    } catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessage("No default value for transient constructor parameter #0 " +
          "a of fun <init>(kotlin.Int): " +
          "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest.RequiredTransientConstructorParameter")
    }
  }

  class RequiredTransientConstructorParameter(@Transient var a: Int)

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

  @Test fun constructorParametersAndPropertiesWithSameNamesMustHaveSameTypes() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<ConstructorParameterWithSameNameAsPropertyButDifferentType>()
      fail()
    } catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessage("'a' has a constructor parameter of type " +
          "kotlin.Int but a property of type kotlin.String.")
    }
  }

  class ConstructorParameterWithSameNameAsPropertyButDifferentType(a: Int) {
    var a = "boo"
  }

  @Test fun supertypeConstructorParameters() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<SubtypeConstructorParameters>()

    val encoded = SubtypeConstructorParameters(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  open class SupertypeConstructorParameters(var a: Int)

  class SubtypeConstructorParameters(a: Int, var b: Int) : SupertypeConstructorParameters(a)

  @Test fun supertypeProperties() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

  class SubtypeProperties : SupertypeProperties() {
    var b: Int = -1
  }

  @Test fun extendsPlatformClassWithPrivateField() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ExtendsPlatformClassWithPrivateField>()

    val encoded = ExtendsPlatformClassWithPrivateField(3)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"id":"B"}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.id).isEqualTo("C")
  }

  internal class ExtendsPlatformClassWithPrivateField(var a: Int) : SimpleTimeZone(0, "C")

  @Test fun extendsPlatformClassWithProtectedField() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<ExtendsPlatformClassWithProtectedField>()

    val encoded = ExtendsPlatformClassWithProtectedField(3)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"buf":[0,0],"count":0}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"buf":[0,0],"size":0}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.buf()).isEqualTo(ByteArray(2, { 0 }))
    assertThat(decoded.count()).isEqualTo(0)
  }

  internal class ExtendsPlatformClassWithProtectedField(var a: Int) : ByteArrayOutputStream(2) {
    fun buf() = buf
    fun count() = count
  }

  @Test fun platformTypeThrows() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<Triple<*, *, *>>()
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage(
          "Platform class kotlin.Triple in kotlin.Triple<java.lang.Object, java.lang.Object, java.lang.Object> requires explicit JsonAdapter to be registered")
    }
  }

  @Test fun privateConstructorParameters() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<PrivateConstructorParameters>()

    val encoded = PrivateConstructorParameters(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a()).isEqualTo(4)
    assertThat(decoded.b()).isEqualTo(6)
  }

  class PrivateConstructorParameters(private var a: Int, private var b: Int) {
    fun a() = a
    fun b() = b
  }

  @Test fun privateConstructor() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<PrivateConstructor>()

    val encoded = PrivateConstructor.newInstance(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a()).isEqualTo(4)
    assertThat(decoded.b()).isEqualTo(6)
  }

  class PrivateConstructor private constructor(var a: Int, var b: Int) {
    fun a() = a
    fun b() = b
    companion object {
      fun newInstance(a: Int, b: Int) = PrivateConstructor(a, b)
    }
  }

  @Test fun privateProperties() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<PrivateProperties>()

    val encoded = PrivateProperties()
    encoded.a(3)
    encoded.b(5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a()).isEqualTo(4)
    assertThat(decoded.b()).isEqualTo(6)
  }

  class PrivateProperties {
    private var a: Int = -1
    private var b: Int = -1

    fun a() = a

    fun a(a: Int) {
      this.a = a
    }

    fun b() = b

    fun b(b: Int) {
      this.b = b
    }
  }

  @Test fun unsettablePropertyIgnored() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<UnsettableProperty>()

    val encoded = UnsettableProperty()
    encoded.b = 5
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(-1)
    assertThat(decoded.b).isEqualTo(6)
  }

  class UnsettableProperty {
    val a: Int = -1
    var b: Int = -1
  }

  @Test fun getterOnlyNoBackingField() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val jsonAdapter = moshi.adapter<GetterOnly>()

    val encoded = GetterOnly(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
    assertThat(decoded.total).isEqualTo(10)
  }

  class GetterOnly(var a: Int, var b: Int) {
    val total : Int
      get() = a + b
  }

  @Test fun getterAndSetterNoBackingField() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

  class GetterAndSetter(var a: Int, var b: Int) {
    var total : Int
      get() = a + b
      set(value) {
        b = value - a
      }
  }

  @Test fun nonPropertyConstructorParameter() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<NonPropertyConstructorParameter>()
      fail()
    } catch(expected: IllegalArgumentException) {
      assertThat(expected).hasMessage(
          "No property for required constructor parameter #0 a of fun <init>(" +
              "kotlin.Int, kotlin.Int): ${NonPropertyConstructorParameter::class.qualifiedName}")
    }
  }

  class NonPropertyConstructorParameter(a: Int, val b: Int)

  @Test fun kotlinEnumsAreNotCovered() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter<UsingEnum>()

    assertThat(adapter.fromJson("""{"e": "A"}""")).isEqualTo(UsingEnum(KotlinEnum.A))
  }

  data class UsingEnum(val e: KotlinEnum)

  enum class KotlinEnum {
    A, B
  }

  @Test fun interfacesNotSupported() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<Interface>()
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("No JsonAdapter for interface " +
          "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest\$Interface (with no annotations)")
    }
  }

  interface Interface

  @Test fun abstractClassesNotSupported() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<AbstractClass>()
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("Cannot serialize abstract class " +
          "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest\$AbstractClass")
    }
  }

  abstract class AbstractClass(val a: Int)

  @Test fun innerClassesNotSupported() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<InnerClass>()
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("Cannot serialize inner class " +
          "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest\$InnerClass")
    }
  }

  inner class InnerClass(val a: Int)

  @Test fun localClassesNotSupported() {
    class LocalClass(val a: Int)
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<LocalClass>()
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("Cannot serialize local class or object expression " +
          "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest\$localClassesNotSupported\$LocalClass")
    }
  }

  @Test fun objectDeclarationsNotSupported() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter<ObjectDeclaration>()
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("Cannot serialize object declaration " +
          "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest\$ObjectDeclaration")
    }
  }

  object ObjectDeclaration {
    var a = 5
  }

  @Test fun objectExpressionsNotSupported() {
    val expression = object : Any() {
      var a = 5
    }
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    try {
      moshi.adapter(expression.javaClass)
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("Cannot serialize local class or object expression " +
          "com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest\$objectExpressionsNotSupported" +
          "\$expression$1")
    }
  }

  @Test fun manyProperties32() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

  class ManyProperties32(
    var v01: Int, var v02: Int, var v03: Int, var v04: Int, var v05: Int,
    var v06: Int, var v07: Int, var v08: Int, var v09: Int, var v10: Int,
    var v11: Int, var v12: Int, var v13: Int, var v14: Int, var v15: Int,
    var v16: Int, var v17: Int, var v18: Int, var v19: Int, var v20: Int,
    var v21: Int, var v22: Int, var v23: Int, var v24: Int, var v25: Int,
    var v26: Int, var v27: Int, var v28: Int, var v29: Int, var v30: Int,
    var v31: Int, var v32: Int)

  @Test fun manyProperties33() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

  class ManyProperties33(
    var v01: Int, var v02: Int, var v03: Int, var v04: Int, var v05: Int,
    var v06: Int, var v07: Int, var v08: Int, var v09: Int, var v10: Int,
    var v11: Int, var v12: Int, var v13: Int, var v14: Int, var v15: Int,
    var v16: Int, var v17: Int, var v18: Int, var v19: Int, var v20: Int,
    var v21: Int, var v22: Int, var v23: Int, var v24: Int, var v25: Int,
    var v26: Int, var v27: Int, var v28: Int, var v29: Int, var v30: Int,
    var v31: Int, var v32: Int, var v33: Int)

  data class Box<out T>(val data: T)

  @Test fun genericTypes() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val stringBoxAdapter = moshi.adapter<Box<String>>(
        Types.newParameterizedTypeWithOwner(KotlinJsonAdapterTest::class.java, Box::class.java,
            String::class.java))
    assertThat(stringBoxAdapter.fromJson("""{"data":"hello"}""")).isEqualTo(Box("hello"))
    assertThat(stringBoxAdapter.toJson(Box("hello"))).isEqualTo("""{"data":"hello"}""")
  }

  data class NestedGenerics<R, C, out V>(val value: Map<R, Map<C, List<V>>>)

  @Test fun nestedGenericTypes() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val type = Types.newParameterizedTypeWithOwner(
        KotlinJsonAdapterTest::class.java,
        NestedGenerics::class.java,
        String::class.java,
        Int::class.javaObjectType,
        Types.newParameterizedTypeWithOwner(
            KotlinJsonAdapterTest::class.java,
            Box::class.java,
            String::class.java
        )
    )
    val adapter = moshi.adapter<NestedGenerics<String, Int, Box<String>>>(type).indent("  ")
    val json = """
      |{
      |  "value": {
      |    "hello": {
      |      "1": [
      |        {
      |          "data": " "
      |        },
      |        {
      |          "data": "world!"
      |        }
      |      ]
      |    }
      |  }
      |}
      """.trimMargin()
    val value = NestedGenerics(mapOf("hello" to mapOf(1 to listOf(Box(" "), Box("world!")))))
    assertThat(adapter.fromJson(json)).isEqualTo(value)
    assertThat(adapter.toJson(value)).isEqualTo(json)
  }

  @Test fun mixingReflectionAndCodegen() {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    val generatedAdapter = moshi.adapter<UsesGeneratedAdapter>()
    val reflectionAdapter = moshi.adapter<UsesReflectionAdapter>()

    assertThat(generatedAdapter.toString())
        .isEqualTo("GeneratedJsonAdapter(KotlinJsonAdapterTest.UsesGeneratedAdapter).nullSafe()")
    assertThat(reflectionAdapter.toString())
        .isEqualTo("KotlinJsonAdapter(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest" +
            ".UsesReflectionAdapter).nullSafe()")
  }

  @JsonClass(generateAdapter = true)
  class UsesGeneratedAdapter(var a: Int, var b: Int)

  @JsonClass(generateAdapter = false)
  class UsesReflectionAdapter(var a: Int, var b: Int)

  @Retention(RUNTIME)
  @JsonQualifier
  annotation class Uppercase

  class UppercaseJsonAdapter {
    @ToJson fun toJson(@Uppercase s: String) : String {
      return s.toUpperCase(Locale.US)
    }
    @FromJson @Uppercase fun fromJson(s: String) : String {
      return s.toLowerCase(Locale.US)
    }
  }

  data class HasNullableBoolean(val boolean: Boolean?)

  @Test fun nullablePrimitivesUseBoxedPrimitiveAdapters() {
    val moshi = Moshi.Builder()
        .add(JsonAdapter.Factory { type, _, _ ->
          if (Boolean::class.javaObjectType == type) {
            return@Factory object: JsonAdapter<Boolean?>() {
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
        .add(KotlinJsonAdapterFactory())
        .build()
    val adapter = moshi.adapter<HasNullableBoolean>().serializeNulls()
    assertThat(adapter.fromJson("""{"boolean":"not a boolean"}"""))
        .isEqualTo(HasNullableBoolean(null))
    assertThat(adapter.toJson(HasNullableBoolean(null))).isEqualTo("""{"boolean":null}""")
  }

  @Test fun adaptersAreNullSafe() {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // TODO in CR: We had to mark this as nullable, vs before the jsonadapter factory would always run
    val adapter = moshi.adapter<HasNullableBoolean?>()
    assertThat(adapter.fromJson("null")).isNull()
    assertThat(adapter.toJson(null)).isEqualTo("null")
  }

  @Test fun kotlinClassesWithoutAdapterAreRefused() {
    val moshi = Moshi.Builder().build()
    try {
      moshi.adapter<PlainKotlinClass>()
      fail("Should not pass here")
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageContaining("Reflective serialization of Kotlin classes")
    }
  }

  class PlainKotlinClass

  @Test fun mapOfStringToStandardReflectionWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToStandardReflection::class.java,
        """{"map":{"key":"value"}}""",
        MapOfStringToStandardReflection(mapOf("key" to "value")))
  }

  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToStandardReflection(val map: Map<String, String> = mapOf())

  @Test fun mapOfStringToStandardCodegenWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToStandardCodegen::class.java,
        """{"map":{"key":"value"}}""",
        MapOfStringToStandardCodegen(mapOf("key" to "value")))
  }

  @JsonClass(generateAdapter = true)
  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToStandardCodegen(val map: Map<String, String> = mapOf())

  @Test fun mapOfStringToEnumReflectionWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToEnumReflection::class.java,
        """{"map":{"key":"A"}}""",
        MapOfStringToEnumReflection(mapOf("key" to KotlinEnum.A)))
  }

  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToEnumReflection(val map: Map<String, KotlinEnum> = mapOf())

  @Test fun mapOfStringToEnumCodegenWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToEnumCodegen::class.java,
        """{"map":{"key":"A"}}""",
        MapOfStringToEnumCodegen(mapOf("key" to KotlinEnum.A)))
  }

  @JsonClass(generateAdapter = true)
  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToEnumCodegen(val map: Map<String, KotlinEnum> = mapOf())

  @Test fun mapOfStringToCollectionReflectionWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToCollectionReflection::class.java,
        """{"map":{"key":[]}}""",
        MapOfStringToCollectionReflection(mapOf("key" to listOf())))
  }

  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToCollectionReflection(val map: Map<String, List<Int>> = mapOf())

  @Test fun mapOfStringToCollectionCodegenWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToCollectionCodegen::class.java,
        """{"map":{"key":[]}}""",
        MapOfStringToCollectionCodegen(mapOf("key" to listOf())))
  }

  @JsonClass(generateAdapter = true)
  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToCollectionCodegen(val map: Map<String, List<Int>> = mapOf())

  @Test fun mapOfStringToMapReflectionWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToMapReflection::class.java,
        """{"map":{"key":{}}}""",
        MapOfStringToMapReflection(mapOf("key" to mapOf())))
  }

  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToMapReflection(val map: Map<String, Map<String, Int>> = mapOf())

  @Test fun mapOfStringToMapCodegenWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToMapCodegen::class.java,
        """{"map":{"key":{}}}""",
        MapOfStringToMapCodegen(mapOf("key" to mapOf())))
  }

  @JsonClass(generateAdapter = true)
  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToMapCodegen(val map: Map<String, Map<String, Int>> = mapOf())

  @Test fun mapOfStringToArrayReflectionWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToArrayReflection::class.java,
        """{"map":{"key":[]}}""",
        MapOfStringToArrayReflection(mapOf("key" to arrayOf())))
  }

  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToArrayReflection(val map: Map<String, Array<Int>> = mapOf())

  @Test fun mapOfStringToArrayCodegenWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToArrayCodegen::class.java,
        """{"map":{"key":[]}}""",
        MapOfStringToArrayCodegen(mapOf("key" to arrayOf())))
  }

  @JsonClass(generateAdapter = true)
  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToArrayCodegen(val map: Map<String, Array<Int>> = mapOf())

  @Test fun mapOfStringToClassReflectionWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToClassReflection::class.java,
        """{"map":{"key":{"a":19,"b":42}}}""",
        MapOfStringToClassReflection(mapOf("key" to ConstructorParameters(19, 42))))
  }

  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToClassReflection(val map: Map<String, ConstructorParameters> = mapOf())

  @Test fun mapOfStringToClassCodegenWildcards() {
    mapWildcardsParameterizedTest(
        MapOfStringToClassCodegen::class.java,
        """{"map":{"key":{"a":19,"b":42}}}""",
        MapOfStringToClassCodegen(mapOf("key" to ConstructorParameters(19, 42))))
  }

  @JsonClass(generateAdapter = true)
  @JvmSuppressWildcards(suppress = false)
  data class MapOfStringToClassCodegen(val map: Map<String, ConstructorParameters> = mapOf())

  @Test fun sealedClassesAreRejected() {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    try {
      moshi.adapter<SealedClass>()
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageContaining("Cannot reflectively serialize sealed class")
    }
  }

  sealed class SealedClass

  private fun <T> mapWildcardsParameterizedTest(type: Class<T>, json: String, value: T) {
    // Ensure the map was created with the expected wildcards of a Kotlin map.
    val fieldType = type.getDeclaredField("map").genericType
    val fieldTypeArguments = (fieldType as ParameterizedType).actualTypeArguments
    assertThat(fieldTypeArguments[0]).isNotInstanceOf(WildcardType::class.java)
    assertThat(fieldTypeArguments[1]).isInstanceOf(WildcardType::class.java)

    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(type)

    assertThat(adapter.fromJson(json)).isEqualToComparingFieldByFieldRecursively(value)
    assertThat(adapter.toJson(value)).isEqualTo(json)
  }
}
