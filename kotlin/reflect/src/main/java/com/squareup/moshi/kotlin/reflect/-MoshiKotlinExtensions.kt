package com.squareup.moshi.kotlin.reflect

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

/**
 * @return a [JsonAdapter] for [T], creating it if necessary. Note that while nullability of [T]
 *         itself is handled, nested types (such as in generics) are not resolved.
 */
@ExperimentalStdlibApi
inline fun <reified T> Moshi.adapter(): JsonAdapter<T> {
  val ktype = typeOf<T>()
  val adapter = adapter<T>(ktype.javaType)
  return if (ktype.isMarkedNullable) {
    adapter.nullSafe()
  } else {
    adapter.nonNull()
  }
}

@ExperimentalStdlibApi
inline fun <reified T> Moshi.Builder.addAdapter(adapter: JsonAdapter<T>) = add(typeOf<T>().javaType, adapter)
