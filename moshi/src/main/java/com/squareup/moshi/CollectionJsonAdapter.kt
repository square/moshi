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

import kotlin.Throws
import java.lang.reflect.Type
import okio.IOException

/** Converts collection types to JSON arrays containing their converted contents.  */
internal abstract class CollectionJsonAdapter<C : MutableCollection<T?>, T> private constructor(
  private val elementAdapter: JsonAdapter<T>
) : JsonAdapter<C>() {
  abstract fun newCollection(): C

  @Throws(IOException::class)
  override fun fromJson(reader: JsonReader): C {
    val result = newCollection()
    reader.beginArray()
    while (reader.hasNext()) {
      result.add(elementAdapter.fromJson(reader))
    }
    reader.endArray()
    return result
  }

  @Throws(IOException::class)
  override fun toJson(writer: JsonWriter, value: C?) {
    value?.let {
      writer.beginArray()
      for (element in value) {
        elementAdapter.toJson(writer, element)
      }
      writer.endArray()
    }
  }

  override fun toString(): String {
    return "$elementAdapter.collection()"
  }

  companion object {
    @JvmField
    val FACTORY = object : Factory {
      override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        val rawType = Types.getRawType(type)
        if (annotations.isNotEmpty()) return null
        if (rawType == MutableList::class.java || rawType == MutableCollection::class.java) {
          return newArrayListAdapter<Any>(type, moshi).nullSafe()
        } else if (rawType == MutableSet::class.java) {
          return newLinkedHashSetAdapter<Any>(type, moshi).nullSafe()
        }
        return null
      }
    }

    private fun <T> newArrayListAdapter(type: Type, moshi: Moshi): JsonAdapter<MutableCollection<T?>> {
      val elementType = Types.collectionElementType(type, MutableCollection::class.java)
      val elementAdapter = moshi.adapter<T>(elementType)
      return object : CollectionJsonAdapter<MutableCollection<T?>, T>(elementAdapter) {
        override fun newCollection(): MutableCollection<T?> {
          return mutableListOf()
        }
      }
    }

    private fun <T> newLinkedHashSetAdapter(type: Type, moshi: Moshi): JsonAdapter<MutableSet<T?>> {
      val elementType = Types.collectionElementType(type, MutableCollection::class.java)
      val elementAdapter = moshi.adapter<T>(elementType)
      return object : CollectionJsonAdapter<MutableSet<T?>, T>(elementAdapter) {
        override fun newCollection(): MutableSet<T?> {
          return LinkedHashSet()
        }
      }
    }
  }
}
