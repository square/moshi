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

import com.squareup.moshi.internal.NO_ANNOTATIONS
import com.squareup.moshi.internal.generatedAdapter
import com.squareup.moshi.internal.jsonName
import com.squareup.moshi.internal.knownNotNull
import java.lang.reflect.Type

internal object StandardJsonAdapters : JsonAdapter.Factory {

  private const val ERROR_FORMAT = "Expected %s but was %s at path %s"

  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (annotations.isNotEmpty()) return null
    if (type === Boolean::class.javaPrimitiveType) return BOOLEAN_JSON_ADAPTER
    if (type === Byte::class.javaPrimitiveType) return BYTE_JSON_ADAPTER
    if (type === Char::class.javaPrimitiveType) return CHARACTER_JSON_ADAPTER
    if (type === Double::class.javaPrimitiveType) return DOUBLE_JSON_ADAPTER
    if (type === Float::class.javaPrimitiveType) return FLOAT_JSON_ADAPTER
    if (type === Int::class.javaPrimitiveType) return INTEGER_JSON_ADAPTER
    if (type === Long::class.javaPrimitiveType) return LONG_JSON_ADAPTER
    if (type === Short::class.javaPrimitiveType) return SHORT_JSON_ADAPTER
    if (type === Boolean::class.javaObjectType) return BOOLEAN_JSON_ADAPTER.nullSafe()
    if (type === Byte::class.javaObjectType) return BYTE_JSON_ADAPTER.nullSafe()
    if (type === Char::class.javaObjectType) return CHARACTER_JSON_ADAPTER.nullSafe()
    if (type === Double::class.javaObjectType) return DOUBLE_JSON_ADAPTER.nullSafe()
    if (type === Float::class.javaObjectType) return FLOAT_JSON_ADAPTER.nullSafe()
    if (type === Int::class.javaObjectType) return INTEGER_JSON_ADAPTER.nullSafe()
    if (type === Long::class.javaObjectType) return LONG_JSON_ADAPTER.nullSafe()
    if (type === Short::class.javaObjectType) return SHORT_JSON_ADAPTER.nullSafe()
    if (type === String::class.java) return STRING_JSON_ADAPTER.nullSafe()
    if (type === Any::class.java) return ObjectJsonAdapter(moshi).nullSafe()
    val rawType = type.rawType
    val generatedAdapter = moshi.generatedAdapter(type, rawType)
    if (generatedAdapter != null) {
      return generatedAdapter
    }
    return if (rawType.isEnum) {
      @Suppress("UNCHECKED_CAST")
      EnumJsonAdapter(rawType as Class<out Enum<*>>).nullSafe()
    } else {
      null
    }
  }

  fun rangeCheckNextInt(reader: JsonReader, typeMessage: String?, min: Int, max: Int): Int {
    val value = reader.nextInt()
    if (value !in min..max) {
      throw JsonDataException(ERROR_FORMAT.format(typeMessage, value, reader.path))
    }
    return value
  }

  val BOOLEAN_JSON_ADAPTER: JsonAdapter<Boolean> = object : JsonAdapter<Boolean>() {
    override fun fromJson(reader: JsonReader) = reader.nextBoolean()

    override fun toJson(writer: JsonWriter, value: Boolean?) {
      writer.value(knownNotNull(value))
    }

    override fun toString() = "JsonAdapter(Boolean)"
  }

  private val BYTE_JSON_ADAPTER: JsonAdapter<Byte> = object : JsonAdapter<Byte>() {
    override fun fromJson(reader: JsonReader): Byte {
      return rangeCheckNextInt(reader, "a byte", Byte.MIN_VALUE.toInt(), 0xff).toByte()
    }

    override fun toJson(writer: JsonWriter, value: Byte?) {
      writer.value((knownNotNull(value).toInt() and 0xff).toLong())
    }

    override fun toString() = "JsonAdapter(Byte)"
  }

  private val CHARACTER_JSON_ADAPTER: JsonAdapter<Char> = object : JsonAdapter<Char>() {
    override fun fromJson(reader: JsonReader): Char {
      val value = reader.nextString()
      if (value.length > 1) {
        throw JsonDataException(ERROR_FORMAT.format("a char", "\"$value\"", reader.path))
      }
      return value[0]
    }

    override fun toJson(writer: JsonWriter, value: Char?) {
      writer.value(knownNotNull(value).toString())
    }

    override fun toString() = "JsonAdapter(Character)"
  }

  private val DOUBLE_JSON_ADAPTER: JsonAdapter<Double> = object : JsonAdapter<Double>() {
    override fun fromJson(reader: JsonReader): Double {
      return reader.nextDouble()
    }

    override fun toJson(writer: JsonWriter, value: Double?) {
      writer.value(knownNotNull(value).toDouble())
    }

    override fun toString() = "JsonAdapter(Double)"
  }

  private val FLOAT_JSON_ADAPTER: JsonAdapter<Float> = object : JsonAdapter<Float>() {
    override fun fromJson(reader: JsonReader): Float {
      val value = reader.nextDouble().toFloat()
      // Double check for infinity after float conversion; many doubles > Float.MAX
      if (!reader.lenient && value.isInfinite()) {
        throw JsonDataException(
          "JSON forbids NaN and infinities: $value at path ${reader.path}",
        )
      }
      return value
    }

    override fun toJson(writer: JsonWriter, value: Float?) {
      // Manual null pointer check for the float.class adapter.
      if (value == null) {
        throw NullPointerException()
      }
      // Use the Number overload so we write out float precision instead of double precision.
      writer.value(value)
    }

    override fun toString() = "JsonAdapter(Float)"
  }

  private val INTEGER_JSON_ADAPTER: JsonAdapter<Int> = object : JsonAdapter<Int>() {
    override fun fromJson(reader: JsonReader): Int {
      return reader.nextInt()
    }

    override fun toJson(writer: JsonWriter, value: Int?) {
      writer.value(knownNotNull(value).toInt().toLong())
    }

    override fun toString() = "JsonAdapter(Integer)"
  }

  private val LONG_JSON_ADAPTER: JsonAdapter<Long> = object : JsonAdapter<Long>() {
    override fun fromJson(reader: JsonReader): Long {
      return reader.nextLong()
    }

    override fun toJson(writer: JsonWriter, value: Long?) {
      writer.value(knownNotNull(value).toLong())
    }

    override fun toString() = "JsonAdapter(Long)"
  }

  private val SHORT_JSON_ADAPTER: JsonAdapter<Short> = object : JsonAdapter<Short>() {
    override fun fromJson(reader: JsonReader): Short {
      return rangeCheckNextInt(reader, "a short", Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    override fun toJson(writer: JsonWriter, value: Short?) {
      writer.value(knownNotNull(value).toInt().toLong())
    }

    override fun toString() = "JsonAdapter(Short)"
  }

  private val STRING_JSON_ADAPTER: JsonAdapter<String> = object : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String? {
      return reader.nextString()
    }

    override fun toJson(writer: JsonWriter, value: String?) {
      writer.value(value)
    }

    override fun toString() = "JsonAdapter(String)"
  }

  internal class EnumJsonAdapter<T : Enum<T>>(private val enumType: Class<T>) : JsonAdapter<T>() {
    private val constants: Array<T> = enumType.enumConstants
    private val nameStrings: Array<String> = Array(constants.size) { i ->
      val constant = constants[i]
      val constantName = constant.name
      try {
        enumType.getField(constantName).jsonName(constantName)
      } catch (e: NoSuchFieldException) {
        throw AssertionError("Missing field in ${enumType.name}", e)
      }
    }
    private var options: JsonReader.Options = JsonReader.Options.of(*nameStrings)

    override fun fromJson(reader: JsonReader): T {
      val index = reader.selectString(options)
      if (index != -1) return constants[index]

      // We can consume the string safely, we are terminating anyway.
      val path = reader.path
      val name = reader.nextString()
      throw JsonDataException(
        "Expected one of ${nameStrings.toList()} but was $name at path $path",
      )
    }

    override fun toJson(writer: JsonWriter, value: T?) {
      writer.value(nameStrings[knownNotNull(value).ordinal])
    }

    override fun toString() = "JsonAdapter(${enumType.name})"
  }

  /**
   * This adapter is used when the declared type is [Any]. Typically the runtime
   * type is something else, and when encoding JSON this delegates to the runtime type's adapter.
   * For decoding (where there is no runtime type to inspect), this uses maps and lists.
   *
   * This adapter needs a Moshi instance to look up the appropriate adapter for runtime types as
   * they are encountered.
   */
  internal class ObjectJsonAdapter(private val moshi: Moshi) : JsonAdapter<Any>() {
    private val listJsonAdapter: JsonAdapter<List<*>> = moshi.adapter(List::class.java)
    private val mapAdapter: JsonAdapter<Map<*, *>> = moshi.adapter(Map::class.java)
    private val stringAdapter: JsonAdapter<String> = moshi.adapter(String::class.java)
    private val doubleAdapter: JsonAdapter<Double> = moshi.adapter(Double::class.java)
    private val booleanAdapter: JsonAdapter<Boolean> = moshi.adapter(Boolean::class.java)

    override fun fromJson(reader: JsonReader): Any? {
      return when (reader.peek()) {
        JsonReader.Token.BEGIN_ARRAY -> listJsonAdapter.fromJson(reader)

        JsonReader.Token.BEGIN_OBJECT -> mapAdapter.fromJson(reader)

        JsonReader.Token.STRING -> stringAdapter.fromJson(reader)

        JsonReader.Token.NUMBER -> doubleAdapter.fromJson(reader)

        JsonReader.Token.BOOLEAN -> booleanAdapter.fromJson(reader)

        JsonReader.Token.NULL -> reader.nextNull()

        else -> throw IllegalStateException(
          "Expected a value but was ${reader.peek()} at path ${reader.path}",
        )
      }
    }

    override fun toJson(writer: JsonWriter, value: Any?) {
      val valueClass: Class<*> = knownNotNull(value).javaClass
      if (valueClass == Any::class.java) {
        // Don't recurse infinitely when the runtime type is also Object.class.
        writer.beginObject()
        writer.endObject()
      } else {
        moshi.adapter<Any>(toJsonType(valueClass), NO_ANNOTATIONS).toJson(writer, value)
      }
    }

    /**
     * Returns the type to look up a type adapter for when writing `value` to JSON. Without
     * this, attempts to emit standard types like `LinkedHashMap` would fail because Moshi doesn't
     * provide built-in adapters for implementation types. It knows how to **write**
     * those types, but lacks a mechanism to read them because it doesn't know how to find the
     * appropriate constructor.
     */
    private fun toJsonType(valueClass: Class<*>): Class<*> {
      if (Map::class.java.isAssignableFrom(valueClass)) return Map::class.java
      return if (Collection::class.java.isAssignableFrom(valueClass)) Collection::class.java else valueClass
    }

    override fun toString() = "JsonAdapter(Object)"
  }
}
