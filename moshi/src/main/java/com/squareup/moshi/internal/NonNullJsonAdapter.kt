/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.moshi.internal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonWriter

public class NonNullJsonAdapter<T>(public val delegate: JsonAdapter<T>) : JsonAdapter<T>() {
  override fun fromJson(reader: JsonReader): T {
    return if (reader.peek() == JsonReader.Token.NULL) {
      throw JsonDataException("Unexpected null at " + reader.path)
    } else {
      delegate.fromJson(reader)!!
    }
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    if (value == null) {
      throw JsonDataException("Unexpected null at " + writer.path)
    } else {
      delegate.toJson(writer, value)
    }
  }

  override fun toString(): String = "$delegate.nonNull()"
}
