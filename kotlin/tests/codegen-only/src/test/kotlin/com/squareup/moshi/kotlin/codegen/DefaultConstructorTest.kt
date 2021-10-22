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
package com.squareup.moshi.kotlin.codegen

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

class DefaultConstructorTest {

  @Test fun minimal() {
    val expected = TestClass("requiredClass")
    val json =
      """{"required":"requiredClass"}"""
    val instance = Moshi.Builder().build().adapter<TestClass>()
      .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun allSet() {
    val expected = TestClass("requiredClass", "customOptional", 4, "setDynamic", 5, 6)
    val json =
      """{"required":"requiredClass","optional":"customOptional","optional2":4,"dynamicSelfReferenceOptional":"setDynamic","dynamicOptional":5,"dynamicInlineOptional":6}"""
    val instance = Moshi.Builder().build().adapter<TestClass>()
      .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun customDynamic() {
    val expected = TestClass("requiredClass", "customOptional")
    val json =
      """{"required":"requiredClass","optional":"customOptional"}"""
    val instance = Moshi.Builder().build().adapter<TestClass>()
      .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }
}

@JsonClass(generateAdapter = true)
data class TestClass(
  val required: String,
  val optional: String = "optional",
  val optional2: Int = 2,
  val dynamicSelfReferenceOptional: String = required,
  val dynamicOptional: Int = createInt(),
  val dynamicInlineOptional: Int = createInlineInt()
)

private fun createInt(): Int {
  return 3
}

@Suppress("NOTHING_TO_INLINE")
private inline fun createInlineInt(): Int {
  return 3
}
