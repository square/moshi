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
import java.lang.reflect.Array as JavaArray

/**
 * Converts arrays to JSON arrays containing their converted contents. This supports both primitive
 * and object arrays.
 */
internal class ArrayJsonAdapter(
  private val elementClass: Class<*>,
  private val elementAdapter: JsonAdapter<Any>,
) : JsonAdapter<Any?>() {
  override fun fromJson(reader: JsonReader): Any {
    val list = buildList<Any?> {
      reader.beginArray()
      while (reader.hasNext()) {
        add(elementAdapter.fromJson(reader))
      }
      reader.endArray()
    }
    val array = JavaArray.newInstance(elementClass, list.size)
    list.forEachIndexed { i, item ->
      JavaArray.set(array, i, item)
    }
    return array
  }

  override fun toJson(writer: JsonWriter, value: Any?) {
    writer.beginArray()
    when (value) {
      is BooleanArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is ByteArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is CharArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is DoubleArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is FloatArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is IntArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is LongArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is ShortArray -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }

      is Array<*> -> {
        for (element in value) {
          elementAdapter.toJson(writer, element)
        }
      }
    }
    writer.endArray()
  }

  override fun toString() = "$elementAdapter.array()"

  companion object Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      val elementType = Types.arrayComponentType(type) ?: return null
      if (annotations.isNotEmpty()) return null
      val elementClass = elementType.rawType
      val elementAdapter = moshi.adapter<Any>(elementType)
      return ArrayJsonAdapter(elementClass, elementAdapter).nullSafe()
    }
  }
}
