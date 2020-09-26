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
package com.squareup.moshi.recipes

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import okio.BufferedSource
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME

@JsonClass(generateAdapter = true)
data class ExampleClass(
  val type: Int,
  @JsonString val rawJson: String
)

@Retention(RUNTIME)
@JsonQualifier
annotation class JsonString

class JsonStringJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (type != String::class.java) return null
    Types.nextAnnotations(annotations, JsonString::class.java) ?: return null
    return JsonStringJsonAdapter().nullSafe()
  }

  private class JsonStringJsonAdapter : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String =
      reader.nextSource().use(BufferedSource::readUtf8)

    override fun toJson(writer: JsonWriter, value: String?) {
      writer.valueSink().use { sink -> sink.writeUtf8(checkNotNull(value)) }
    }
  }
}

fun main() {
  //language=JSON
  val json = "{\"type\":1,\"rawJson\":{\"a\":2,\"b\":3,\"c\":[1,2,3]}}"

  val moshi = Builder()
    .add(JsonStringJsonAdapterFactory())
    .build()

  val example: ExampleClass = moshi.adapter(ExampleClass::class.java).fromJson(json)!!

  check(example.type == 1)

  //language=JSON
  check(example.rawJson == "{\"a\":2,\"b\":3,\"c\":[1,2,3]}")
}
