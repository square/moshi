/*
 * Copyright (C) 2019 Square, Inc.
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
import org.junit.Test
import java.util.Locale

@ExperimentalStdlibApi
class KotlinApisTest {
  @Test fun adapterInfersType() {
    val moshi = Moshi.Builder()
        .build()
    val jsonAdapter: JsonAdapter<Map<String, Int>> = moshi.adapter()
    val value = jsonAdapter.fromJson("""{"a":5}""")

    assertThat(value).isEqualTo(mapOf("a" to 5))
  }

  @Test fun addInfersType() {
    val moshi = Moshi.Builder()
        .add<String>(object : JsonAdapter<String>() {
          override fun fromJson(reader: JsonReader) =
              reader.nextString().toUpperCase(Locale.US)

          override fun toJson(writer: JsonWriter, value: String?) = error("unexpected")
        })
        .build()

    val jsonAdapter = moshi.adapter<String>()
    val value = jsonAdapter.fromJson(""""a"""")

    assertThat(value).isEqualTo("A")
  }
}
