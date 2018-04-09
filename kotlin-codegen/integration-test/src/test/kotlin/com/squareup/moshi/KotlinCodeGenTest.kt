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
package com.squareup.moshi

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayOutputStream

class KotlinCodeGenTest {
  @Ignore @Test fun duplicatedValue() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(DuplicateValue::class.java)

    try {
      jsonAdapter.fromJson("""{"a":4,"a":4}""")
      fail()
    } catch(expected: JsonDataException) {
      assertThat(expected).hasMessage("Multiple values for a at $.a")
    }
  }

  class DuplicateValue(var a: Int = -1, var b: Int = -2)

  @Ignore @Test fun repeatedValue() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(RepeatedValue::class.java)

    try {
      jsonAdapter.fromJson("""{"a":4,"b":null,"b":6}""")
      fail()
    } catch(expected: JsonDataException) {
      assertThat(expected).hasMessage("Multiple values for b at $.b")
    }
  }

  class RepeatedValue(var a: Int, var b: Int?)

  @Ignore @Test fun supertypeConstructorParameters() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(SubtypeConstructorParameters::class.java)

    val encoded = SubtypeConstructorParameters(3, 5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a).isEqualTo(4)
    assertThat(decoded.b).isEqualTo(6)
  }

  open class SupertypeConstructorParameters(var a: Int)

  class SubtypeConstructorParameters(a: Int, var b: Int) : SupertypeConstructorParameters(a)

  @Ignore @Test fun supertypeProperties() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(SubtypeProperties::class.java)

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

  @Ignore @Test fun extendsPlatformClassWithProtectedField() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(ExtendsPlatformClassWithProtectedField::class.java)

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

  @Ignore @Test fun platformTypeThrows() {
    val moshi = Moshi.Builder().build()
    try {
      moshi.adapter(Triple::class.java)
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessage("Platform class kotlin.Triple (with no annotations) "
          + "requires explicit JsonAdapter to be registered")
    }
  }

  @Ignore @Test fun privateConstructorParameters() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(PrivateConstructorParameters::class.java)

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

  @Ignore @Test fun privateConstructor() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(PrivateConstructor::class.java)

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

  @Ignore @Test fun privateProperties() {
    val moshi = Moshi.Builder().build()
    val jsonAdapter = moshi.adapter(PrivateProperties::class.java)

    val encoded = PrivateProperties()
    encoded.a(3)
    encoded.b(5)
    assertThat(jsonAdapter.toJson(encoded)).isEqualTo("""{"a":3,"b":5}""")

    val decoded = jsonAdapter.fromJson("""{"a":4,"b":6}""")!!
    assertThat(decoded.a()).isEqualTo(4)
    assertThat(decoded.b()).isEqualTo(6)
  }

  class PrivateProperties {
    var a: Int = -1
    var b: Int = -1

    fun a() = a

    fun a(a: Int) {
      this.a = a
    }

    fun b() = b

    fun b(b: Int) {
      this.b = b
    }
  }

  @Ignore @Test fun kotlinEnumsAreNotCovered() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter(UsingEnum::class.java)

    assertThat(adapter.fromJson("""{"e": "A"}""")).isEqualTo(UsingEnum(KotlinEnum.A))
  }

  data class UsingEnum(val e: KotlinEnum)

  enum class KotlinEnum {
    A, B
  }
}
