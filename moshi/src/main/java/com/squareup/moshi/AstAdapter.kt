/*
 * Copyright (C) 2024 Square, Inc.
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

import com.squareup.moshi.ast.JArray
import com.squareup.moshi.ast.JBoolean
import com.squareup.moshi.ast.JDouble
import com.squareup.moshi.ast.JField
import com.squareup.moshi.ast.JInt
import com.squareup.moshi.ast.JNothing
import com.squareup.moshi.ast.JNull
import com.squareup.moshi.ast.JObject
import com.squareup.moshi.ast.JString
import com.squareup.moshi.ast.JValue

internal class AstAdapter : JsonAdapter<JValue<*>>() {
  override fun fromJson(reader: JsonReader): JValue<*> {
    try {
      return when (reader.peek()) {
        JsonReader.Token.BEGIN_ARRAY -> parseArray(reader)
        // parseArray should have consumed this token, invalid document
        JsonReader.Token.END_ARRAY -> JNothing
        JsonReader.Token.BEGIN_OBJECT -> parseObject(reader)
        // parseObject should have consumed this token, invalid document
        JsonReader.Token.END_OBJECT -> JNothing
        // parseObject should have consumed this token, invalid document
        JsonReader.Token.NAME -> JNothing
        JsonReader.Token.STRING -> parseString(reader)
        JsonReader.Token.NUMBER -> parseNumber(reader)
        JsonReader.Token.BOOLEAN -> parseBoolean(reader)
        JsonReader.Token.NULL -> JNull
        // reader.hasNext() should have returned false, invalid document
        JsonReader.Token.END_DOCUMENT -> JNothing
      }
    } catch (e: Exception) {
      return JNothing
    }
  }

  private fun parseArray(reader: JsonReader): JArray {
    val values = mutableListOf<JValue<*>>()
    reader.beginArray()
    while (reader.hasNext()) {
      values.add(fromJson(reader))
    }
    reader.endArray()
    return JArray(values)
  }

  private fun parseObject(reader: JsonReader): JObject {
    val fields = mutableListOf<JField>()
    reader.beginObject()
    while (reader.hasNext()) {
      val name = reader.nextName()
      val value = fromJson(reader)
      fields.add(JField(name, value))
    }
    reader.endObject()
    return JObject(fields)
  }

  private fun parseString(reader: JsonReader): JString {
    return JString(reader.nextString())
  }

  private fun parseNumber(reader: JsonReader): JValue<*> {
    val value = reader.nextString()
    return if (value.contains('.')) {
      JDouble(value.toBigDecimal())
    } else {
      JInt(value.toBigInteger())
    }
  }

  private fun parseBoolean(reader: JsonReader): JBoolean {
    return JBoolean(reader.nextBoolean())
  }

  override fun toJson(writer: JsonWriter, value: JValue<*>?) {
    when (value) {
      // not writing anything since this value is invalid by definition
      is JNothing -> {}
      is JNull, null -> writer.nullValue()
      is JString -> writer.value(value.value)
      is JDouble -> writer.value(value.value)
      is JInt -> writer.value(value.value)
      is JBoolean -> writer.value(value.value)
      is JArray -> {
        writer.beginArray()
        value.values.forEach {
          toJson(writer, it)
        }
        writer.endArray()
      }
      is JObject -> {
        writer.beginObject()
        value.fields.forEach {
          writer.name(it.name)
          toJson(writer, it.value)
        }
        writer.endObject()
      }
    }
  }

  override fun toString(): String = "AstAdapter"
}
