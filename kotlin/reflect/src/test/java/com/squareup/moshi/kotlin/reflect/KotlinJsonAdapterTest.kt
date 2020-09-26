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

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class KotlinJsonAdapterTest {
  @JsonClass(generateAdapter = true)
  class Data

  @Test
  fun fallsBackToReflectiveAdapterWithoutCodegen() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val adapter = moshi.adapter(Data::class.java)
    assertThat(adapter.toString()).isEqualTo(
      "KotlinJsonAdapter(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest.Data).nullSafe()"
    )
  }
}
