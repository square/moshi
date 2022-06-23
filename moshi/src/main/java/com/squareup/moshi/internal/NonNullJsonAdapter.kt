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
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/**
 * Surprise! This is typed as `T?` to allow us to use it as a nullable type and give clearer error
 * messages, but users of this class should always use it as an assumed non-nullable type and is
 * cast as such in [JsonAdapter.nonNull].
 */
public class NonNullJsonAdapter<T>(public val delegate: JsonAdapter<T>) : NullAwareJsonAdapter<T?>() {
  override fun fromJson(reader: JsonReader): T {
    return if (reader.peek() == JsonReader.Token.NULL) {
      throw JsonDataException("Non-null value was null at ${reader.path}")
    } else {
      knownNotNull(delegate.fromJson(reader))
    }
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    if (value == null) {
      throw JsonDataException("Non-null value was null at ${writer.path}")
    } else {
      delegate.toJson(writer, value)
    }
  }

  override fun toString(): String = "$delegate.nonNull()"
}
