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
package com.squareup.moshi

import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import okio.IOException
import java.util.Date

@Deprecated(
  """This class moved to avoid a package name conflict in the Java Platform Module System.
      The new class is com.squareup.moshi.adapters.Rfc3339DateJsonAdapter.""",
  replaceWith = ReplaceWith("com.squareup.moshi.adapters.Rfc3339DateJsonAdapter"),
  level = DeprecationLevel.ERROR
)
public class Rfc3339DateJsonAdapter : JsonAdapter<Date>() {

  private val delegate = Rfc3339DateJsonAdapter()

  @Throws(IOException::class)
  override fun fromJson(reader: JsonReader): Date? {
    return delegate.fromJson(reader)
  }

  @Throws(IOException::class)
  override fun toJson(writer: JsonWriter, value: Date?) {
    delegate.toJson(writer, value)
  }
}
