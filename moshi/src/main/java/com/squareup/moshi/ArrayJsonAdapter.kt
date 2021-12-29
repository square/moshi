/*
 * Copyright (C) 2014 Square, Inc.
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
 * Converts arrays to JSON arrays containing their converted contents. This supports both primitive
 * and object arrays.
 */
internal class ArrayJsonAdapter(
  private val elementClass: Class<*>,
  private val elementAdapter: JsonAdapter<Any>
) : JsonAdapter<Any?>() {
  override fun fromJson(reader: JsonReader): Any {
    val list = buildList<Any?> {
      reader.beginArray()
      while (reader.hasNext()) {
        add(elementAdapter.fromJson(reader))
      }
      reader.endArray()
    }
    val array = java.lang.reflect.Array.newInstance(elementClass, list.size)
    list.forEachIndexed { i, item ->
      java.lang.reflect.Array.set(array, i, item)
    }
    return array
  }

  override fun toJson(writer: JsonWriter, value: Any?) {
    writer.beginArray()
    @Suppress("UNCHECKED_CAST")
    when (value) {
      is BooleanArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is ByteArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is CharArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is DoubleArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is FloatArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is IntArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is LongArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is ShortArray -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
      is Array<*> -> {
        val size = value.size
        for (i in 0 until size) {
          elementAdapter.toJson(writer, value[i])
        }
      }
    }
    writer.endArray()
  }

  override fun toString(): String {
    return "$elementAdapter.array()"
  }

  companion object {
    @JvmField
    val FACTORY = object : Factory {
      override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        val elementType = Types.arrayComponentType(type) ?: return null
        if (annotations.isNotEmpty()) return null
        val elementClass = elementType.rawType
        val elementAdapter = moshi.adapter<Any>(elementType)
        return ArrayJsonAdapter(elementClass, elementAdapter).nullSafe()
      }
    }
  }
}
