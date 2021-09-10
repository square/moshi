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

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

// Regression tests specific to Moshi-KSP
class MoshiKspTest {
  private val moshi = Moshi.Builder().build()

  // Regression test for https://github.com/ZacSweers/MoshiX/issues/44
  @Test
  fun onlyInterfaceSupertypes() {
    val adapter = moshi.adapter<SimpleImpl>()
    //language=JSON
    val json = """{"a":"aValue","b":"bValue"}"""
    val expected = SimpleImpl("aValue", "bValue")
    val instance = adapter.fromJson(json)!!
    assertThat(instance).isEqualTo(expected)
    val encoded = adapter.toJson(instance)
    assertThat(encoded).isEqualTo(json)
  }

  interface SimpleInterface {
    val a: String
  }

  // NOTE the Any() superclass is important to test that we're detecting the farthest parent class
  // correct.y
  @JsonClass(generateAdapter = true)
  data class SimpleImpl(override val a: String, val b: String) : Any(), SimpleInterface
}
