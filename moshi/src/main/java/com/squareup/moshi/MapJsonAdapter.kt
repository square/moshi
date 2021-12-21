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

import com.squareup.moshi.Types.getRawType
import com.squareup.moshi.Types.mapKeyAndValueTypes
import kotlin.Throws
import java.lang.reflect.Type
import okio.IOException

/**
 * Converts maps with string keys to JSON objects.
 *
 * TODO: support maps with other key types and convert to/from strings.
 */
internal class MapJsonAdapter<K, V>(moshi: Moshi, keyType: Type, valueType: Type) : JsonAdapter<Map<K, V>>() {
  private val keyAdapter: JsonAdapter<K>
  private val valueAdapter: JsonAdapter<V>

  init {
    keyAdapter = moshi.adapter(keyType)
    valueAdapter = moshi.adapter(valueType)
  }

  @Throws(IOException::class)
  override fun toJson(writer: JsonWriter, map: Map<K, V>?) {
    writer.beginObject()
    if (map != null) {
      for ((key, value) in map) {
        if (key == null) {
          throw JsonDataException("Map key is null at " + writer.getPath())
        }
        writer.promoteValueToName()
        keyAdapter.toJson(writer, key)
        valueAdapter.toJson(writer, value)
      }
    }
    writer.endObject()
  }

  @Throws(IOException::class)
  override fun fromJson(reader: JsonReader): Map<K, V> {
    val result = LinkedHashTreeMap<K, V>()
    reader.beginObject()
    while (reader.hasNext()) {
      reader.promoteNameToValue()
      val name = keyAdapter.fromJson(reader)
      val value = valueAdapter.fromJson(reader)
      val replaced = result.put(name, value)
      if (replaced != null) {
        throw JsonDataException(
          "Map key '"
            + name
            + "' has multiple values at path "
            + reader.getPath()
            + ": "
            + replaced
            + " and "
            + value
        )
      }
    }
    reader.endObject()
    return result
  }

  override fun toString(): String {
    return "JsonAdapter($keyAdapter=$valueAdapter)"
  }

  companion object {
    @JvmField
    val FACTORY = object : Factory {
      override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        val rawType = getRawType(type)
        if (rawType != MutableMap::class.java) return null
        val keyAndValue = mapKeyAndValueTypes(type, rawType)
        return MapJsonAdapter<Any?, Any>(moshi, keyAndValue[0], keyAndValue[1]).nullSafe()
      }
    }
  }
}
