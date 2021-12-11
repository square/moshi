/*
 * Copyright (C) 2018 Square, Inc.
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
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Options
import com.squareup.moshi.JsonReader.Token.STRING
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.internal.Util.jsonName
import java.io.IOException
import java.lang.NoSuchFieldException

/**
 * A JsonAdapter for enums that allows having a fallback enum value when a deserialized string does
 * not match any enum value. To use, add this as an adapter for your enum type on your [ ]:
 *
 * ```
 * Moshi moshi = new Moshi.Builder()
 *   .add(CurrencyCode.class, EnumJsonAdapter.create(CurrencyCode.class)
 *   .withUnknownFallback(CurrencyCode.USD))
 *   .build();
 * ```
 */
public class EnumJsonAdapter<T : Enum<T>> internal constructor(
  private val enumType: Class<T>,
  private val fallbackValue: T?,
  private val useFallbackValue: Boolean,
) : JsonAdapter<T>() {

  private val constants: Array<T>
  private var options: Options
  private val nameStrings: Array<String>

  init {
    try {
      constants = enumType.enumConstants
      nameStrings = Array(constants.size) { i ->
        val constantName = constants[i].name
        jsonName(constantName, enumType.getField(constantName))
      }
      options = Options.of(*nameStrings)
    } catch (e: NoSuchFieldException) {
      throw AssertionError("Missing field in " + enumType.name, e)
    }
  }

  /**
   * Create a new adapter for this enum with a fallback value to use when the JSON string does not
   * match any of the enum's constants. Note that this value will not be used when the JSON value is
   * null, absent, or not a string. Also, the string values are case-sensitive, and this fallback
   * value will be used even on case mismatches.
   */
  public fun withUnknownFallback(fallbackValue: T?): EnumJsonAdapter<T> {
    return EnumJsonAdapter(enumType, fallbackValue, true)
  }

  @Throws(IOException::class)
  override fun fromJson(reader: JsonReader): T? {
    val index = reader.selectString(options)
    if (index != -1) return constants[index]
    val path = reader.path
    if (!useFallbackValue) {
      val name = reader.nextString()
      throw JsonDataException(
        "Expected one of ${nameStrings.toList()} but was $name at path $path"
      )
    }
    if (reader.peek() != STRING) {
      throw JsonDataException(
        "Expected a string but was " + reader.peek() + " at path " + path
      )
    }
    reader.skipValue()
    return fallbackValue
  }

  @Throws(IOException::class)
  override fun toJson(writer: JsonWriter, value: T?) {
    if (value == null) {
      throw NullPointerException(
        "value was null! Wrap in .nullSafe() to write nullable values."
      )
    }
    writer.value(nameStrings[value.ordinal])
  }

  override fun toString(): String = "EnumJsonAdapter(" + enumType.name + ")"

  public companion object {
    @JvmStatic
    public fun <T : Enum<T>> create(enumType: Class<T>): EnumJsonAdapter<T> {
      return EnumJsonAdapter(enumType, null, false)
    }
  }
}
