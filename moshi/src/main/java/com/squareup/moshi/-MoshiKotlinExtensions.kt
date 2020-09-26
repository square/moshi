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
package com.squareup.moshi

import com.squareup.moshi.internal.NonNullJsonAdapter
import com.squareup.moshi.internal.NullSafeJsonAdapter
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * @return a [JsonAdapter] for [T], creating it if necessary. Note that while nullability of [T]
 *         itself is handled, nested types (such as in generics) are not resolved.
 */
@ExperimentalStdlibApi
inline fun <reified T> Moshi.adapter(): JsonAdapter<T> = adapter(typeOf<T>())

@ExperimentalStdlibApi
inline fun <reified T> Moshi.Builder.addAdapter(adapter: JsonAdapter<T>): Moshi.Builder = add(typeOf<T>().javaType, adapter)

/**
 * @return a [JsonAdapter] for [ktype], creating it if necessary. Note that while nullability of
 *         [ktype] itself is handled, nested types (such as in generics) are not resolved.
 */
@ExperimentalStdlibApi
fun <T> Moshi.adapter(ktype: KType): JsonAdapter<T> {
  val adapter = adapter<T>(ktype.javaType)
  return if (adapter is NullSafeJsonAdapter || adapter is NonNullJsonAdapter) {
    // TODO CR - Assume that these know what they're doing? Or should we defensively avoid wrapping for matching nullability?
    adapter
  } else if (ktype.isMarkedNullable) {
    adapter.nullSafe()
  } else {
    adapter.nonNull()
  }
}
