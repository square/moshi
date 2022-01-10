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

import com.squareup.moshi.internal.NonNullJsonAdapter
import com.squareup.moshi.internal.NullSafeJsonAdapter
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException
import org.intellij.lang.annotations.Language
import java.lang.reflect.Type
import javax.annotation.CheckReturnValue
import kotlin.Throws

/**
 * Converts Java values to JSON, and JSON values to Java.
 *
 * JsonAdapter instances provided by Moshi are thread-safe, meaning multiple threads can safely
 * use a single instance concurrently.
 *
 * Custom JsonAdapter implementations should be designed to be thread-safe.
 */
public abstract class JsonAdapter<T> {
  /**
   * Decodes a nullable instance of type [T] from the given [reader].
   *
   * @throws JsonDataException when the data in a JSON document doesn't match the data expected by
   * the caller.
   */
  @CheckReturnValue
  @Throws(IOException::class)
  public abstract fun fromJson(reader: JsonReader): T?

  /**
   * Decodes a nullable instance of type [T] from the given [source].
   *
   * @throws JsonDataException when the data in a JSON document doesn't match the data expected by
   * the caller.
   */
  @CheckReturnValue
  @Throws(IOException::class)
  public fun fromJson(source: BufferedSource): T? = fromJson(JsonReader.of(source))

  /**
   * Decodes a nullable instance of type [T] from the given `string`.
   *
   * @throws JsonDataException when the data in a JSON document doesn't match the data expected by
   * the caller.
   */
  @CheckReturnValue
  @Throws(IOException::class)
  public fun fromJson(@Language("JSON") string: String): T? {
    val reader = JsonReader.of(Buffer().writeUtf8(string))
    val result = fromJson(reader)
    if (!isLenient && reader.peek() != JsonReader.Token.END_DOCUMENT) {
      throw JsonDataException("JSON document was not fully consumed.")
    }
    return result
  }

  /** Encodes the given [value] with the given [writer]. */
  @Throws(IOException::class)
  public abstract fun toJson(writer: JsonWriter, value: T?)

  @Throws(IOException::class)
  public fun toJson(sink: BufferedSink, value: T?) {
    val writer = JsonWriter.of(sink)
    toJson(writer, value)
  }

  /** Encodes the given [value] into a String and returns it. */
  @CheckReturnValue
  public fun toJson(value: T?): String {
    val buffer = Buffer()
    try {
      toJson(buffer, value)
    } catch (e: IOException) {
      throw AssertionError(e) // No I/O writing to a Buffer.
    }
    return buffer.readUtf8()
  }

  /**
   * Encodes [value] as a Java value object comprised of maps, lists, strings, numbers,
   * booleans, and nulls.
   *
   * Values encoded using `value(double)` or `value(long)` are modeled with the
   * corresponding boxed type. Values encoded using `value(Number)` are modeled as a [Long] for boxed integer types
   * ([Byte], [Short], [Integer], and [Long]), as a [Double] for boxed floating point types ([Float] and [Double]),
   * and as a [java.math.BigDecimal] for all other types.
   */
  @CheckReturnValue
  public fun toJsonValue(value: T?): Any? {
    val writer = JsonValueWriter()
    return try {
      toJson(writer, value)
      writer.root()
    } catch (e: IOException) {
      throw AssertionError(e) // No I/O writing to an object.
    }
  }

  /**
   * Decodes a Java value object from [value], which must be comprised of maps, lists,
   * strings, numbers, booleans and nulls.
   */
  @CheckReturnValue
  public fun fromJsonValue(value: Any?): T? {
    val reader = JsonValueReader(value)
    return try {
      fromJson(reader)
    } catch (e: IOException) {
      throw AssertionError(e) // No I/O reading from an object.
    }
  }

  /**
   * Returns a JSON adapter equal to this JSON adapter, but that serializes nulls when encoding
   * JSON.
   */
  @CheckReturnValue
  public fun serializeNulls(): JsonAdapter<T> {
    val delegate: JsonAdapter<T> = this
    return object : JsonAdapter<T>() {
      override fun fromJson(reader: JsonReader) = delegate.fromJson(reader)

      override fun toJson(writer: JsonWriter, value: T?) {
        val serializeNulls = writer.getSerializeNulls()
        writer.setSerializeNulls(true)
        try {
          delegate.toJson(writer, value)
        } finally {
          writer.setSerializeNulls(serializeNulls)
        }
      }

      override val isLenient: Boolean
        get() = delegate.isLenient

      override fun toString() = "$delegate.serializeNulls()"
    }
  }

  /**
   * Returns a JSON adapter equal to this JSON adapter, but with support for reading and writing
   * nulls.
   */
  @CheckReturnValue
  public fun nullSafe(): JsonAdapter<T> {
    return when (this) {
      is NullSafeJsonAdapter<*> -> this
      else -> NullSafeJsonAdapter(this)
    }
  }

  /**
   * Returns a JSON adapter equal to this JSON adapter, but that refuses null values. If null is
   * read or written this will throw a [JsonDataException].
   *
   * Note that this adapter will not usually be invoked for absent values and so those must be
   * handled elsewhere. This should only be used to fail on explicit nulls.
   */
  @CheckReturnValue
  public fun nonNull(): JsonAdapter<T> {
    return when (this) {
      is NonNullJsonAdapter<*> -> this
      else -> NonNullJsonAdapter(this)
    }
  }

  /** Returns a JSON adapter equal to this, but is lenient when reading and writing. */
  @CheckReturnValue
  public fun lenient(): JsonAdapter<T> {
    val delegate: JsonAdapter<T> = this
    return object : JsonAdapter<T>() {
      override fun fromJson(reader: JsonReader): T? {
        val lenient = reader.isLenient
        reader.isLenient = true
        return try {
          delegate.fromJson(reader)
        } finally {
          reader.isLenient = lenient
        }
      }

      override fun toJson(writer: JsonWriter, value: T?) {
        val lenient = writer.isLenient
        writer.isLenient = true
        try {
          delegate.toJson(writer, value)
        } finally {
          writer.isLenient = lenient
        }
      }

      override val isLenient: Boolean
        get() = true

      override fun toString() = "$delegate.lenient()"
    }
  }

  /**
   * Returns a JSON adapter equal to this, but that throws a [JsonDataException] when
   * [unknown names and values][JsonReader.setFailOnUnknown] are encountered.
   * This constraint applies to both the top-level message handled by this type adapter as well as
   * to nested messages.
   */
  @CheckReturnValue
  public fun failOnUnknown(): JsonAdapter<T> {
    val delegate: JsonAdapter<T> = this
    return object : JsonAdapter<T>() {
      override fun fromJson(reader: JsonReader): T? {
        val skipForbidden = reader.failOnUnknown()
        reader.setFailOnUnknown(true)
        return try {
          delegate.fromJson(reader)
        } finally {
          reader.setFailOnUnknown(skipForbidden)
        }
      }

      override fun toJson(writer: JsonWriter, value: T?) {
        delegate.toJson(writer, value)
      }

      override val isLenient: Boolean
        get() = delegate.isLenient

      override fun toString() = "$delegate.failOnUnknown()"
    }
  }

  /**
   * Return a JSON adapter equal to this, but using `indent` to control how the result is
   * formatted. The `indent` string to be repeated for each level of indentation in the
   * encoded document. If `indent.isEmpty()` the encoded document will be compact. Otherwise
   * the encoded document will be more human-readable.
   *
   * @param indent a string containing only whitespace.
   */
  @CheckReturnValue
  public fun indent(indent: String): JsonAdapter<T> {
    val delegate: JsonAdapter<T> = this
    return object : JsonAdapter<T>() {
      override fun fromJson(reader: JsonReader): T? {
        return delegate.fromJson(reader)
      }

      override fun toJson(writer: JsonWriter, value: T?) {
        val originalIndent = writer.getIndent()
        writer.setIndent(indent)
        try {
          delegate.toJson(writer, value)
        } finally {
          writer.setIndent(originalIndent)
        }
      }

      override val isLenient: Boolean
        get() = delegate.isLenient

      override fun toString() = "$delegate.indent(\"$indent\")"
    }
  }

  public open val isLenient: Boolean
    get() = false

  public fun interface Factory {
    /**
     * Attempts to create an adapter for `type` annotated with `annotations`. This
     * returns the adapter if one was created, or null if this factory isn't capable of creating
     * such an adapter.
     *
     * Implementations may use [Moshi.adapter] to compose adapters of other types, or
     * [Moshi.nextAdapter] to delegate to the underlying adapter of the same type.
     */
    @CheckReturnValue
    public fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>?
  }
}
