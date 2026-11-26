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
package com.squareup.moshi

import com.squareup.moshi.internal.NonNullJsonAdapter
import com.squareup.moshi.internal.NullSafeJsonAdapter
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.typeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@JsonQualifier @Retention(RUNTIME) annotation class TestAnnotation1

@JsonQualifier @Retention(RUNTIME) annotation class TestAnnotation2

@TestAnnotation1
@TestAnnotation2
class KotlinExtensionsTest {

  @Test
  fun nextAnnotationsShouldWork() {
    val annotations =
      KotlinExtensionsTest::class.java.annotations.filterTo(mutableSetOf()) {
        it.annotationClass.java.isAnnotationPresent(JsonQualifier::class.java)
      }
    assertEquals(2, annotations.size)
    val next = annotations.nextAnnotations<TestAnnotation2>()
    checkNotNull(next)
    assertEquals(1, next.size)
    assertTrue(next.first() is TestAnnotation1)
  }

  @Test
  fun arrayType() {
    val stringArray = String::class.asArrayType()
    check(stringArray.genericComponentType == String::class.java)

    val stringListType = typeOf<List<String>>()
    val stringListArray = stringListType.asArrayType()
    val expected = Types.arrayOf(Types.newParameterizedType(List::class.java, String::class.java))
    assertEquals(stringListArray, expected)
  }

  @Test
  fun addAdapterInferred() {
    // An adapter that always returns -1
    val customIntdapter =
      object : JsonAdapter<Int>() {
        override fun fromJson(reader: JsonReader): Int {
          reader.skipValue()
          return -1
        }

        override fun toJson(writer: JsonWriter, value: Int) {
          throw NotImplementedError()
        }
      }
    val moshi = Moshi.Builder().addAdapter(customIntdapter).build()

    assertEquals(-1, moshi.adapter<Int>().fromJson("5"))
  }

  @Test
  fun addAdapterInferred_parameterized() {
    // An adapter that always returns listOf(-1)
    val customIntListAdapter =
      object : JsonAdapter<List<Int>>() {
        override fun fromJson(reader: JsonReader): List<Int> {
          reader.skipValue()
          return listOf(-1)
        }

        override fun toJson(writer: JsonWriter, value: List<Int>) {
          throw NotImplementedError()
        }
      }
    val moshi = Moshi.Builder().addAdapter(customIntListAdapter).build()

    assertEquals(listOf(-1), moshi.adapter<List<Int>>().fromJson("[5]"))
  }

  @Test
  fun nonNullTypeRejectsNull() {
    val adapter = Moshi.Builder().build().adapter<String>()

    val exception = assertThrows(JsonDataException::class.java) { adapter.fromJson("null") }
    assertEquals("Unexpected null at $", exception.message)
  }

  @Test
  fun nullableTypeAllowsNull() {
    val adapter = Moshi.Builder().build().adapter<String?>()

    assertNull(adapter.fromJson("null"))
    assertEquals("null", adapter.toJson(null))
  }

  @Test
  fun nullableTypeUnwrapsNonNullAdapter() {
    val delegate =
      object : JsonAdapter<String?>() {
        override fun fromJson(reader: JsonReader): String? {
          reader.skipValue()
          return null
        }

        override fun toJson(writer: JsonWriter, value: String?) {
          writer.jsonValue(value)
        }

        override fun toString(): String = "delegate"
      }
    val adapter =
      Moshi.Builder()
        .add(String::class.java, NonNullJsonAdapter(delegate))
        .build()
        .adapter<String?>()

    assertNull(adapter.fromJson("\"value\""))
    assertEquals("delegate.nullSafe()", adapter.toString())
    assertEquals("null", adapter.toJson(null))
  }

  @Test
  fun nonNullTypeUnwrapsNullSafeAdapter() {
    val delegate =
      object : JsonAdapter<String?>() {
        override fun fromJson(reader: JsonReader): String? = reader.nextString()

        override fun toJson(writer: JsonWriter, value: String?) {
          writer.jsonValue(value)
        }

        override fun toString(): String = "delegate"
      }
    val adapter =
      Moshi.Builder()
        .add(String::class.java, NullSafeJsonAdapter(delegate))
        .build()
        .adapter<String>()

    assertEquals("delegate.nonNull()", adapter.toString())
    assertEquals("value", adapter.fromJson("\"value\""))
    val exception = assertThrows(JsonDataException::class.java) { adapter.fromJson("null") }
    assertEquals("Unexpected null at $", exception.message)
  }
}
