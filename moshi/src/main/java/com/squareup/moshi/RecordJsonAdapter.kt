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
package com.squareup.moshi

import java.lang.reflect.Type

/**
 * This is just a simple shim for linking in [StandardJsonAdapters] and swapped with a real
 * implementation in Java 16 via MR Jar.
 */
internal class RecordJsonAdapter<T> : JsonAdapter<T>() {
  override fun fromJson(reader: JsonReader) = throw AssertionError()

  override fun toJson(writer: JsonWriter, value: T?) = throw AssertionError()

  companion object Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? = null
  }
}
