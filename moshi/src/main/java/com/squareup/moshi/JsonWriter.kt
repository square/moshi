/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi

import com.squareup.moshi.JsonScope.EMPTY_ARRAY
import com.squareup.moshi.JsonScope.EMPTY_OBJECT
import com.squareup.moshi.JsonScope.NONEMPTY_ARRAY
import com.squareup.moshi.JsonScope.NONEMPTY_OBJECT
import java.io.Flushable

import kotlin.Throws
import java.lang.IllegalArgumentException
import okio.BufferedSink
import okio.BufferedSource
import javax.annotation.CheckReturnValue
import okio.Closeable
import okio.IOException

/**
 * Writes a JSON ([RFC 7159](http://www.ietf.org/rfc/rfc7159.txt)) encoded value to a
 * stream, one token at a time. The stream includes both literal values (strings, numbers, booleans
 * and nulls) as well as the begin and end delimiters of objects and arrays.
 *
 * <h1>Encoding JSON</h1>
 *
 * To encode your data as JSON, create a new `JsonWriter`. Each JSON document must contain one
 * top-level array or object. Call methods on the writer as you walk the structure's contents,
 * nesting arrays and objects as necessary:
 *
 *  * To write **arrays**, first call [.beginArray]. Write each of the
 * array's elements with the appropriate [.value] methods or by nesting other arrays and
 * objects. Finally close the array using [.endArray].
 *  * To write **objects**, first call [.beginObject]. Write each of the
 * object's properties by alternating calls to [.name] with the property's value. Write
 * property values with the appropriate [.value] method or by nesting other objects or
 * arrays. Finally close the object using [.endObject].
 *
 * <h1>Example</h1>
 *
 * Suppose we'd like to encode a stream of messages such as the following:
 *
 * <pre>`[
 * {
 * "id": 912345678901,
 * "text": "How do I stream JSON in Java?",
 * "geo": null,
 * "user": {
 * "name": "json_newb",
 * "followers_count": 41
 * }
 * },
 * {
 * "id": 912345678902,
 * "text": "@json_newb just use JsonWriter!",
 * "geo": [50.454722, -104.606667],
 * "user": {
 * "name": "jesse",
 * "followers_count": 2
 * }
 * }
 * ]
`</pre> *
 *
 * This code encodes the above structure:
 *
 * <pre>`public void writeJsonStream(BufferedSink sink, List<Message> messages) throws IOException {
 * JsonWriter writer = JsonWriter.of(sink);
 * writer.setIndent("  ");
 * writeMessagesArray(writer, messages);
 * writer.close();
 * }
 *
 * public void writeMessagesArray(JsonWriter writer, List<Message> messages) throws IOException {
 * writer.beginArray();
 * for (Message message : messages) {
 * writeMessage(writer, message);
 * }
 * writer.endArray();
 * }
 *
 * public void writeMessage(JsonWriter writer, Message message) throws IOException {
 * writer.beginObject();
 * writer.name("id").value(message.getId());
 * writer.name("text").value(message.getText());
 * if (message.getGeo() != null) {
 * writer.name("geo");
 * writeDoublesArray(writer, message.getGeo());
 * } else {
 * writer.name("geo").nullValue();
 * }
 * writer.name("user");
 * writeUser(writer, message.getUser());
 * writer.endObject();
 * }
 *
 * public void writeUser(JsonWriter writer, User user) throws IOException {
 * writer.beginObject();
 * writer.name("name").value(user.getName());
 * writer.name("followers_count").value(user.getFollowersCount());
 * writer.endObject();
 * }
 *
 * public void writeDoublesArray(JsonWriter writer, List<Double> doubles) throws IOException {
 * writer.beginArray();
 * for (Double value : doubles) {
 * writer.value(value);
 * }
 * writer.endArray();
 * }
`</pre> *
 *
 * Each `JsonWriter` may be used to write a single JSON stream. Instances of this class are
 * not thread safe. Calls that would result in a malformed JSON string will fail with an [ ].
 */
public abstract class JsonWriter internal constructor() : Closeable, Flushable {
  // The nesting stack. Using a manual array rather than an ArrayList saves 20%. This stack will
  // grow itself up to 256 levels of nesting including the top-level document. Deeper nesting is
  // prone to trigger StackOverflowErrors.
  @JvmField
  public var stackSize: Int = 0
  @JvmField
  public var scopes: IntArray = IntArray(32)
  @JvmField
  public var pathNames: Array<String?> = arrayOfNulls<String>(32)
  @JvmField
  public var pathIndices: IntArray = IntArray(32)

  /**
   * A string containing a full set of spaces for a single level of indentation, or null for no
   * pretty printing.
   */
  private var indent: String? = null

  private var lenient = false
  private var serializeNulls = false
  @JvmField
  protected var promoteValueToName: Boolean = false

  /**
   * Controls the deepest stack size that has begin/end pairs flattened:
   *
   *  * If -1, no begin/end pairs are being suppressed.
   *  * If positive, this is the deepest stack size whose begin/end pairs are eligible to be
   * flattened.
   *  * If negative, it is the bitwise inverse (~) of the deepest stack size whose begin/end
   * pairs have been flattened.
   *
   *
   * We differentiate between what layer would be flattened (positive) from what layer is being
   * flattened (negative) so that we don't double-flatten.
   *
   * To accommodate nested flattening we require callers to track the previous state when they
   * provide a new state. The previous state is returned from [.beginFlatten] and restored
   * with [.endFlatten].
   */
  @JvmField
  public var flattenStackSize: Int = -1

  private var tags: MutableMap<Class<*>, Any>? = null

  /** Returns the scope on the top of the stack.  */
  protected fun peekScope(): Int {
    if (stackSize == 0) {
      throw IllegalStateException("JsonWriter is closed.")
    }
    return scopes[stackSize - 1]
  }

  /** Before pushing a value on the stack this confirms that the stack has capacity.  */
  protected fun checkStack(): Boolean {
    if (stackSize != scopes.size) return false

    if (stackSize == 256) {
      throw JsonDataException("Nesting too deep at ${getPath()}: circular reference?")
    }

    scopes = scopes.copyOf(scopes.size * 2)
    pathNames = pathNames.copyOf(pathNames.size * 2)
    pathIndices = pathIndices.copyOf(pathIndices.size * 2)
    if (this is JsonValueWriter) {
      this.stack = this.stack.copyOf(this.stack.size * 2)
    }

    return true
  }

  protected fun pushScope(newTop: Int) {
    scopes[stackSize++] = newTop
  }

  /** Replace the value on the top of the stack with the given value.  */
  protected fun replaceTop(topOfStack: Int) {
    scopes[stackSize - 1] = topOfStack
  }

  /**
   * Sets the indentation string to be repeated for each level of indentation in the encoded
   * document. If `indent.isEmpty()` the encoded document will be compact. Otherwise the
   * encoded document will be more human-readable.
   *
   * @param indent a string containing only whitespace.
   */
  public open fun setIndent(indent: String) {
    this.indent = indent.ifEmpty { null }
  }

  /**
   * Returns a string containing only whitespace, used for each level of indentation. If empty, the
   * encoded document will be compact.
   */
  @CheckReturnValue
  public fun getIndent(): String {
    return indent ?: ""
  }

  /**
   * Configure this writer to relax its syntax rules. By default, this writer only emits well-formed
   * JSON as specified by [RFC 7159](http://www.ietf.org/rfc/rfc7159.txt). Setting the
   * writer to lenient permits the following:
   *
   *
   *  * Top-level values of any type. With strict writing, the top-level value must be an object
   * or an array.
   *  * Numbers may be [NaNs][Double.isNaN] or [       infinities][Double.isInfinite].
   */
  public fun setLenient(lenient: Boolean) {
    this.lenient = lenient
  }

  /** Returns true if this writer has relaxed syntax rules.  */
  @CheckReturnValue
  public fun isLenient(): Boolean {
    return lenient
  }

  /**
   * Sets whether object members are serialized when their value is null. This has no impact on
   * array elements. The default is false.
   */
  public fun setSerializeNulls(serializeNulls: Boolean) {
    this.serializeNulls = serializeNulls
  }

  /**
   * Returns true if object members are serialized when their value is null. This has no impact on
   * array elements. The default is false.
   */
  @CheckReturnValue
  public fun getSerializeNulls(): Boolean {
    return serializeNulls
  }

  /**
   * Begins encoding a new array. Each call to this method must be paired with a call to [ ][.endArray].
   *
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun beginArray(): JsonWriter

  /**
   * Ends encoding the current array.
   *
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun endArray(): JsonWriter

  /**
   * Begins encoding a new object. Each call to this method must be paired with a call to [ ][.endObject].
   *
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun beginObject(): JsonWriter

  /**
   * Ends encoding the current object.
   *
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun endObject(): JsonWriter

  /**
   * Encodes the property name.
   *
   * @param name the name of the forthcoming value. Must not be null.
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun name(name: String): JsonWriter

  /**
   * Encodes `value`.
   *
   * @param value the literal string value, or null to encode a null literal.
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun value(value: String?): JsonWriter

  /**
   * Encodes `null`.
   *
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun nullValue(): JsonWriter

  //  /**
  //   * Encodes {@code value}.
  //   *
  //   * @return this writer.
  //   */
  //  public abstract JsonWriter value(boolean value) throws IOException;

  /**
   * Encodes `value`.
   *
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun value(value: Boolean?): JsonWriter

  /**
   * Encodes `value`.
   *
   * @param value a finite value. May not be [NaNs][Double.isNaN] or [     ][Double.isInfinite].
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun value(value: Double): JsonWriter

  /**
   * Encodes `value`.
   *
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun value(value: Long): JsonWriter

  /**
   * Encodes `value`.
   *
   * @param value a finite value. May not be [NaNs][Double.isNaN] or [     ][Double.isInfinite].
   * @return this writer.
   */
  @Throws(IOException::class)
  public abstract fun value(value: Number?): JsonWriter

  /**
   * Writes `source` directly without encoding its contents. Equivalent to `try
   * (BufferedSink sink = writer.valueSink()) { source.readAll(sink): }`
   *
   * @see .valueSink
   */
  @Throws(IOException::class)
  public fun value(source: BufferedSource): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("BufferedSource cannot be used as a map key in JSON at path ${getPath()}")
    }
    valueSink().use { sink -> source.readAll(sink) }
    return this
  }

  /**
   * Returns a [BufferedSink] into which arbitrary data can be written without any additional
   * encoding. You **must** call [BufferedSink.close] before interacting with this `JsonWriter` instance again.
   *
   * Since no validation is performed, options like [.setSerializeNulls] and other writer
   * configurations are not respected.
   */
  @CheckReturnValue
  @Throws(IOException::class)
  public abstract fun valueSink(): BufferedSink

  /**
   * Encodes the value which may be a string, number, boolean, null, map, or list.
   *
   * @return this writer.
   * @see JsonReader.readJsonValue
   */
  @Throws(IOException::class)
  public fun jsonValue(value: Any?): JsonWriter {
    when (value) {
      is Map<*, *> -> {
        beginObject()
        for ((key, value1) in value) {
          if (key !is String) {
            throw IllegalArgumentException(
              if (key == null) "Map keys must be non-null" else "Map keys must be of type String: " + key.javaClass.name)
          }
          name(key)
          jsonValue(value1)
        }
        endObject()
      }
      is List<*> -> {
        beginArray()
        for (element in value) {
          jsonValue(element)
        }
        endArray()
      }
      is String -> {
        value(value)
      }
      is Boolean -> {
        value(value)
      }
      is Double -> {
        value(value)
      }
      is Long -> {
        value(value)
      }
      is Number -> {
        value(value)
      }
      null -> {
        nullValue()
      }
      else -> {
        throw IllegalArgumentException("Unsupported type: " + value.javaClass.name)
      }
    }
    return this
  }

  /**
   * Changes the writer to treat the next value as a string name. This is useful for map adapters so
   * that arbitrary type adapters can use [.value] to write a name value.
   *
   * In this example, calling this method allows two sequential calls to [.value]
   * to produce the object, `{"a": "b"}`.
   *
   * <pre>`JsonWriter writer = JsonWriter.of(...);
   * writer.beginObject();
   * writer.promoteValueToName();
   * writer.value("a");
   * writer.value("b");
   * writer.endObject();
  `</pre> *
   */
  @Throws(IOException::class)
  public fun promoteValueToName() {
    val context = peekScope()
    if ((context != NONEMPTY_OBJECT && context != EMPTY_OBJECT)) {
      throw IllegalStateException("Nesting problem.")
    }
    promoteValueToName = true
  }

  /**
   * Cancels immediately-nested calls to [.beginArray] or [.beginObject] and their
   * matching calls to [.endArray] or [.endObject]. Use this to compose JSON adapters
   * without nesting.
   *
   * For example, the following creates JSON with nested arrays: `[1,[2,3,4],5]`.
   *
   * <pre>`JsonAdapter<List<Integer>> integersAdapter = ...
   *
   * public void writeNumbers(JsonWriter writer) {
   * writer.beginArray();
   * writer.value(1);
   * integersAdapter.toJson(writer, Arrays.asList(2, 3, 4));
   * writer.value(5);
   * writer.endArray();
   * }
  `</pre> *
   *
   * With flattening we can create JSON with a single array `[1,2,3,4,5]`:
   *
   * <pre>`JsonAdapter<List<Integer>> integersAdapter = ...
   *
   * public void writeNumbers(JsonWriter writer) {
   * writer.beginArray();
   * int token = writer.beginFlatten();
   * writer.value(1);
   * integersAdapter.toJson(writer, Arrays.asList(2, 3, 4));
   * writer.value(5);
   * writer.endFlatten(token);
   * writer.endArray();
   * }
  `</pre> *
   *
   * This method flattens arrays within arrays:
   *
   * <pre>`Emit:       [1, [2, 3, 4], 5]
   * To produce: [1, 2, 3, 4, 5]
  `</pre> *
   *
   * It also flattens objects within objects. Do not call [.name] before writing a flattened
   * object.
   *
   * <pre>`Emit:       {"a": 1, {"b": 2}, "c": 3}
   * To Produce: {"a": 1, "b": 2, "c": 3}
  `</pre> *
   *
   * Other combinations are permitted but do not perform flattening. For example, objects inside of
   * arrays are not flattened:
   *
   * <pre>`Emit:       [1, {"b": 2}, 3, [4, 5], 6]
   * To Produce: [1, {"b": 2}, 3, 4, 5, 6]
  `</pre> *
   *
   *
   * This method returns an opaque token. Callers must match all calls to this method with a call
   * to [.endFlatten] with the matching token.
   */
  @CheckReturnValue
  public fun beginFlatten(): Int {
    val context = peekScope()
    if (context != NONEMPTY_OBJECT
      && context != EMPTY_OBJECT
      && context != NONEMPTY_ARRAY
      && context != EMPTY_ARRAY) { throw IllegalStateException("Nesting problem.") }
    val token = flattenStackSize
    flattenStackSize = stackSize
    return token
  }

  /** Ends nested call flattening created by [.beginFlatten].  */
  public fun endFlatten(token: Int) {
    flattenStackSize = token
  }

  /**
   * Returns a [JsonPath](http://goessner.net/articles/JsonPath/) to the current location
   * in the JSON value.
   */
  @CheckReturnValue
  public fun getPath(): String {
    return JsonScope.getPath(stackSize, scopes, pathNames, pathIndices)
  }

  /** Returns the tag value for the given class key.  */
  @Suppress("UNCHECKED_CAST")
  @CheckReturnValue
  public fun <T> tag(clazz: Class<T>): T? {
    return if (tags == null) {
      null
    } else tags!![clazz] as T?
  }

  /** Assigns the tag value using the given class key and value.  */
  public fun <T> setTag(clazz: Class<T>, value: T) {
    if (!clazz.isAssignableFrom(value!!.javaClass)) {
      throw IllegalArgumentException("Tag value must be of type " + clazz.name)
    }
    if (tags == null) {
      tags = LinkedHashMap()
    }
    tags!![clazz] = value
  }

  public companion object {
    /** Returns a new instance that writes UTF-8 encoded JSON to `sink`.  */
    @JvmStatic
    @CheckReturnValue
    public fun of(sink: BufferedSink): JsonWriter {
      return JsonUtf8Writer(sink)
    }
  }
}
