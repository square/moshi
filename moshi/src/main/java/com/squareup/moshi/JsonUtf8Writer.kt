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

import okio.Buffer
import okio.BufferedSink
import okio.Sink
import okio.Timeout
import okio.buffer
import java.io.IOException
import kotlin.Array
import kotlin.AssertionError
import kotlin.Boolean
import kotlin.Char
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.Long
import kotlin.Number
import kotlin.String
import kotlin.arrayOfNulls
import kotlin.check
import kotlin.code
import kotlin.require

internal class JsonUtf8Writer(
  /** The output data, containing at most one top-level array or object. */
  private val sink: BufferedSink
) : JsonWriter() {

  /** The name/value separator; either ":" or ": ". */
  private var separator = ":"
  private var deferredName: String? = null

  override var indent: String
    get() = super.indent
    set(value) {
      super.indent = value
      separator = if (value.isNotEmpty()) ": " else ":"
    }

  init {
    pushScope(JsonScope.EMPTY_DOCUMENT)
  }

  override fun beginArray(): JsonWriter {
    check(!promoteValueToName) { "Array cannot be used as a map key in JSON at path $path" }
    writeDeferredName()
    return open(JsonScope.EMPTY_ARRAY, JsonScope.NONEMPTY_ARRAY, '[')
  }

  override fun endArray() = close(JsonScope.EMPTY_ARRAY, JsonScope.NONEMPTY_ARRAY, ']')

  override fun beginObject(): JsonWriter {
    check(!promoteValueToName) { "Object cannot be used as a map key in JSON at path $path" }
    writeDeferredName()
    return open(JsonScope.EMPTY_OBJECT, JsonScope.NONEMPTY_OBJECT, '{')
  }

  override fun endObject(): JsonWriter {
    promoteValueToName = false
    return close(JsonScope.EMPTY_OBJECT, JsonScope.NONEMPTY_OBJECT, '}')
  }

  /** Enters a new scope by appending any necessary whitespace and the given bracket. */
  private fun open(empty: Int, nonempty: Int, openBracket: Char): JsonWriter {
    val shouldCancelOpen = stackSize == flattenStackSize &&
      (scopes[stackSize - 1] == empty || scopes[stackSize - 1] == nonempty)
    if (shouldCancelOpen) {
      // Cancel this open. Invert the flatten stack size until this is closed.
      flattenStackSize = flattenStackSize.inv()
      return this
    }
    beforeValue()
    checkStack()
    pushScope(empty)
    pathIndices[stackSize - 1] = 0
    sink.writeByte(openBracket.code)
    return this
  }

  /** Closes the current scope by appending any necessary whitespace and the given bracket. */
  private fun close(empty: Int, nonempty: Int, closeBracket: Char): JsonWriter {
    val context = peekScope()
    check(context == nonempty || context == empty) { "Nesting problem." }
    check(deferredName == null) { "Dangling name: $deferredName" }
    if (stackSize == flattenStackSize.inv()) {
      // Cancel this close. Restore the flattenStackSize so we're ready to flatten again!
      flattenStackSize = flattenStackSize.inv()
      return this
    }
    stackSize--
    pathNames[stackSize] = null // Free the last path name so that it can be garbage collected!
    pathIndices[stackSize - 1]++
    if (context == nonempty) {
      newline()
    }
    sink.writeByte(closeBracket.code)
    return this
  }

  override fun name(name: String): JsonWriter {
    check(stackSize != 0) { "JsonWriter is closed." }
    val context = peekScope()
    val isWritingObject = !(
      context != JsonScope.EMPTY_OBJECT && context != JsonScope.NONEMPTY_OBJECT ||
        deferredName != null || promoteValueToName
      )
    check(isWritingObject) { "Nesting problem." }
    deferredName = name
    pathNames[stackSize - 1] = name
    return this
  }

  private fun writeDeferredName() {
    deferredName?.let { deferredName ->
      beforeName()
      sink.string(deferredName)
      this.deferredName = null
    }
  }

  override fun value(value: String?): JsonWriter = apply {
    if (value == null) {
      return nullValue()
    }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value)
    }
    writeDeferredName()
    beforeValue()
    sink.string(value)
    pathIndices[stackSize - 1]++
  }

  override fun nullValue(): JsonWriter = apply {
    check(!promoteValueToName) { "null cannot be used as a map key in JSON at path $path" }
    if (deferredName != null) {
      if (serializeNulls) {
        writeDeferredName()
      } else {
        deferredName = null
        return this // skip the name and the value
      }
    }
    beforeValue()
    sink.writeUtf8("null")
    pathIndices[stackSize - 1]++
  }

  override fun value(value: Boolean): JsonWriter = apply {
    check(!promoteValueToName) { "Boolean cannot be used as a map key in JSON at path $path" }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(if (value) "true" else "false")
    pathIndices[stackSize - 1]++
  }

  override fun value(value: Boolean?): JsonWriter {
    return value?.let(::value) ?: nullValue()
  }

  override fun value(value: Double): JsonWriter = apply {
    require(isLenient || !value.isNaN() && !value.isInfinite()) {
      "Numeric values must be finite, but was $value"
    }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(value.toString())
    pathIndices[stackSize - 1]++
  }

  override fun value(value: Long): JsonWriter = apply {
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(value.toString())
    pathIndices[stackSize - 1]++
  }

  override fun value(value: Number?): JsonWriter = apply {
    if (value == null) {
      return nullValue()
    }
    val string = value.toString()
    val isFinite = isLenient || string != "-Infinity" && string != "Infinity" && string != "NaN"
    require(isFinite) { "Numeric values must be finite, but was $value" }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(string)
    }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(string)
    pathIndices[stackSize - 1]++
  }

  override fun valueSink(): BufferedSink {
    check(!promoteValueToName) { "BufferedSink cannot be used as a map key in JSON at path $path" }
    writeDeferredName()
    beforeValue()
    pushScope(JsonScope.STREAMING_VALUE)
    return object : Sink {
      override fun write(source: Buffer, byteCount: Long) {
        sink.write(source, byteCount)
      }

      override fun close() {
        if (peekScope() != JsonScope.STREAMING_VALUE) {
          throw AssertionError()
        }
        stackSize-- // Remove STREAMING_VALUE from the stack.
        pathIndices[stackSize - 1]++
      }

      override fun flush() = sink.flush()

      override fun timeout() = Timeout.NONE
    }.buffer()
  }

  /** Ensures all buffered data is written to the underlying [Sink] and flushes that writer. */
  override fun flush() {
    check(stackSize != 0) { "JsonWriter is closed." }
    sink.flush()
  }

  /**
   * Flushes and closes this writer and the underlying [Sink].
   *
   * @throws JsonDataException if the JSON document is incomplete.
   */
  override fun close() {
    sink.close()
    val size = stackSize
    if (size > 1 || size == 1 && scopes[0] != JsonScope.NONEMPTY_DOCUMENT) {
      throw IOException("Incomplete document")
    }
    stackSize = 0
  }

  private fun newline() {
    if (_indent == null) {
      return
    }
    sink.writeByte('\n'.code)
    var i = 1
    val size = stackSize
    while (i < size) {
      sink.writeUtf8(indent)
      i++
    }
  }

  /**
   * Inserts any necessary separators and whitespace before a name. Also adjusts the stack to expect
   * the name's value.
   */
  private fun beforeName() {
    val context = peekScope()
    if (context == JsonScope.NONEMPTY_OBJECT) { // first in object
      sink.writeByte(','.code)
    } else {
      check(context == JsonScope.EMPTY_OBJECT) { // not in an object!
        "Nesting problem."
      }
    }
    newline()
    replaceTop(JsonScope.DANGLING_NAME)
  }

  /**
   * Inserts any necessary separators and whitespace before a literal value, inline array, or inline
   * object. Also adjusts the stack to expect either a closing bracket or another element.
   */
  private fun beforeValue() {
    val nextTop: Int
    when (peekScope()) {
      JsonScope.NONEMPTY_DOCUMENT -> {
        if (!isLenient) {
          throw IllegalStateException("JSON must have only one top-level value.")
        }
        nextTop = JsonScope.NONEMPTY_DOCUMENT
      }
      JsonScope.EMPTY_DOCUMENT -> nextTop = JsonScope.NONEMPTY_DOCUMENT
      JsonScope.NONEMPTY_ARRAY -> {
        sink.writeByte(','.code)
        newline()
        nextTop = JsonScope.NONEMPTY_ARRAY
      }
      JsonScope.EMPTY_ARRAY -> {
        newline()
        nextTop = JsonScope.NONEMPTY_ARRAY
      }
      JsonScope.DANGLING_NAME -> {
        nextTop = JsonScope.NONEMPTY_OBJECT
        sink.writeUtf8(separator)
      }
      JsonScope.STREAMING_VALUE -> throw IllegalStateException("Sink from valueSink() was not closed")
      else -> throw IllegalStateException("Nesting problem.")
    }
    replaceTop(nextTop)
  }

  companion object {
    /**
     * From RFC 7159, "All Unicode characters may be placed within the
     * quotation marks except for the characters that must be escaped:
     * quotation mark, reverse solidus, and the control characters
     * (U+0000 through U+001F)."
     *
     * We also escape '\u2028' and '\u2029', which JavaScript interprets as
     * newline characters. This prevents eval() from failing with a syntax
     * error. http://code.google.com/p/google-gson/issues/detail?id=341
     */
    private val REPLACEMENT_CHARS: Array<String?> = arrayOfNulls(128)

    init {
      for (i in 0..0x1f) {
        REPLACEMENT_CHARS[i] = String.format("\\u%04x", i)
      }
      REPLACEMENT_CHARS['"'.code] = "\\\""
      REPLACEMENT_CHARS['\\'.code] = "\\\\"
      REPLACEMENT_CHARS['\t'.code] = "\\t"
      REPLACEMENT_CHARS['\b'.code] = "\\b"
      REPLACEMENT_CHARS['\n'.code] = "\\n"
      REPLACEMENT_CHARS['\r'.code] = "\\r"
      // Kotlin does not support '\f' so we have to use unicode escape
      REPLACEMENT_CHARS['\u000C'.code] = "\\f"
    }

    /**
     * Writes `value` as a string literal to `sink`. This wraps the value in double quotes
     * and escapes those characters that require it.
     */
    @JvmStatic
    fun BufferedSink.string(value: String) {
      val replacements = REPLACEMENT_CHARS
      writeByte('"'.code)
      var last = 0
      val length = value.length
      for (i in 0 until length) {
        val c = value[i]
        var replacement: String?
        if (c.code < 128) {
          replacement = replacements[c.code]
          if (replacement == null) {
            continue
          }
        } else if (c == '\u2028') {
          replacement = "\\u2028"
        } else if (c == '\u2029') {
          replacement = "\\u2029"
        } else {
          continue
        }
        if (last < i) {
          writeUtf8(value, last, i)
        }
        writeUtf8(replacement)
        last = i + 1
      }
      if (last < length) {
        writeUtf8(value, last, length)
      }
      writeByte('"'.code)
    }
  }
}
