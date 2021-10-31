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
package com.squareup.moshi.kotlin.reflect

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.adapters.OptionalJsonAdapter
import org.junit.Test
import java.util.Optional

@OptIn(ExperimentalStdlibApi::class)
class KotlinJsonAdapterTest {

  private val moshi = Moshi.Builder()
    .add(OptionalJsonAdapter.FACTORY)
    .addLast(KotlinJsonAdapterFactory())
    .build()

  @JsonClass(generateAdapter = true)
  class Data

  @Test
  fun fallsBackToReflectiveAdapterWithoutCodegen() {
    val adapter = moshi.adapter(Data::class.java)
    assertThat(adapter.toString()).isEqualTo(
      "KotlinJsonAdapter(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest.Data).nullSafe()"
    )
  }

  @Test
  fun adapterBehavior() {
    val adapter = moshi.adapter<Optional<String>>()
    assertThat(adapter.fromJson("\"foo\"")).isEqualTo(Optional.of("foo"))
    assertThat(adapter.fromJson("null")).isEqualTo(Optional.empty<Any>())
  }

  @Test
  fun smokeTest() {
    val adapter = moshi.adapter<TestClass>()
    assertThat(adapter.fromJson("{\"optionalString\":\"foo\",\"regular\":\"bar\"}"))
      .isEqualTo(TestClass(Optional.of("foo"), "bar"))
    assertThat(adapter.fromJson("{\"optionalString\":null,\"regular\":\"bar\"}"))
      .isEqualTo(TestClass(Optional.empty<String>(), "bar"))
    assertThat(adapter.fromJson("{\"regular\":\"bar\"}"))
      .isEqualTo(TestClass(Optional.empty<String>(), "bar"))
    assertThat(adapter.toJson(TestClass(Optional.of("foo"), "bar")))
      .isEqualTo("{\"optionalString\":\"foo\",\"regular\":\"bar\"}")
    assertThat(adapter.toJson(TestClass(Optional.empty<String>(), "bar")))
      .isEqualTo("{\"regular\":\"bar\"}")
    assertThat(adapter.toJson(TestClass(Optional.empty<String>(), "bar")))
      .isEqualTo("{\"regular\":\"bar\"}")
  }

  data class TestClass(val optionalString: Optional<String>, val regular: String)
}
