/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.moshi.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Token.NULL
import com.squareup.moshi.JsonWriter
import okio.IOException
import java.util.Date

/**
 * Formats dates using [RFC 3339](https://www.ietf.org/rfc/rfc3339.txt), which is
 * formatted like `2015-09-26T18:23:50.250Z`. This adapter is null-safe. To use, add this as
 * an adapter for `Date.class` on your [Moshi.Builder][com.squareup.moshi.Moshi.Builder]:
 *
 * ```
 * Moshi moshi = new Moshi.Builder()
 *   .add(Date.class, new Rfc3339DateJsonAdapter())
 *   .build();
 * ```
 */
public class Rfc3339DateJsonAdapter : JsonAdapter<Date>() {
  @Synchronized
  @Throws(IOException::class)
  override fun fromJson(reader: JsonReader): Date? {
    if (reader.peek() == NULL) {
      return reader.nextNull()
    }
    val string = reader.nextString()
    return string.parseIsoDate()
  }

  @Synchronized
  @Throws(IOException::class)
  override fun toJson(writer: JsonWriter, value: Date?) {
    if (value == null) {
      writer.nullValue()
    } else {
      val string = value.formatIsoDate()
      writer.value(string)
    }
  }
}
