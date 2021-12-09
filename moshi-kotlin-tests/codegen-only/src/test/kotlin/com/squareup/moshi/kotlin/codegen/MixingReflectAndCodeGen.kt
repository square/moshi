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
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test

class MixingReflectAndCodeGen {
  @Test
  fun mixingReflectionAndCodegen() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val generatedAdapter = moshi.adapter<UsesGeneratedAdapter>()
    val reflectionAdapter = moshi.adapter<UsesReflectionAdapter>()

    assertThat(generatedAdapter.toString())
      .isEqualTo("GeneratedJsonAdapter(MixingReflectAndCodeGen.UsesGeneratedAdapter).nullSafe()")
    assertThat(reflectionAdapter.toString())
      .isEqualTo(
        "KotlinJsonAdapter(com.squareup.moshi.kotlin.codegen.MixingReflectAndCodeGen" +
          ".UsesReflectionAdapter).nullSafe()"
      )
  }

  @JsonClass(generateAdapter = true)
  class UsesGeneratedAdapter(var a: Int, var b: Int)

  @JsonClass(generateAdapter = false)
  class UsesReflectionAdapter(var a: Int, var b: Int)
}
