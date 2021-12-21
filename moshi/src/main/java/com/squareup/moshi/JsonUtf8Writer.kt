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

import com.squareup.moshi.JsonScope.DANGLING_NAME
import com.squareup.moshi.JsonScope.EMPTY_ARRAY
import com.squareup.moshi.JsonScope.EMPTY_DOCUMENT
import com.squareup.moshi.JsonScope.EMPTY_OBJECT
import com.squareup.moshi.JsonScope.NONEMPTY_ARRAY
import com.squareup.moshi.JsonScope.NONEMPTY_DOCUMENT
import com.squareup.moshi.JsonScope.NONEMPTY_OBJECT
import com.squareup.moshi.JsonScope.STREAMING_VALUE

import kotlin.Throws
import kotlin.IllegalStateException
import okio.Buffer
import okio.BufferedSink
import okio.IOException
import okio.Sink
import okio.Timeout
import okio.buffer

/** @param sink The output data, containing at most one top-level array or object.  */
internal class JsonUtf8Writer(private val sink: BufferedSink) : JsonWriter() {

  /** The name/value separator; either ":" or ": ".  */
  private var separator = ":"
  private var deferredName: String? = null

  init {
    pushScope(EMPTY_DOCUMENT)
  }

  override fun setIndent(indent: String) {
    super.setIndent(indent)
    separator = if (indent.isNotEmpty()) ": " else ":"
  }

  @Throws(IOException::class)
  override fun beginArray(): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("Array cannot be used as a map key in JSON at path " + getPath())
    }
    writeDeferredName()
    return open(EMPTY_ARRAY, NONEMPTY_ARRAY, '[')
  }

  @Throws(IOException::class)
  override fun endArray(): JsonWriter {
    return close(EMPTY_ARRAY, NONEMPTY_ARRAY, ']')
  }

  @Throws(IOException::class)
  override fun beginObject(): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("Object cannot be used as a map key in JSON at path " + getPath())
    }
    writeDeferredName()
    return open(EMPTY_OBJECT, NONEMPTY_OBJECT, '{')
  }

  @Throws(IOException::class)
  override fun endObject(): JsonWriter {
    promoteValueToName = false
    return close(EMPTY_OBJECT, NONEMPTY_OBJECT, '}')
  }

  /** Enters a new scope by appending any necessary whitespace and the given bracket.  */
  @Throws(IOException::class)
  private fun open(empty: Int, nonempty: Int, openBracket: Char): JsonWriter {
    if (stackSize == flattenStackSize
      && (scopes[stackSize - 1] == empty || scopes[stackSize - 1] == nonempty)) {
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

  /** Closes the current scope by appending any necessary whitespace and the given bracket.  */
  @Throws(IOException::class)
  private fun close(empty: Int, nonempty: Int, closeBracket: Char): JsonWriter {
    val context = peekScope()
    if (context != nonempty && context != empty) {
      throw IllegalStateException("Nesting problem.")
    }
    if (deferredName != null) {
      throw IllegalStateException("Dangling name: $deferredName")
    }
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

  @Throws(IOException::class)
  override fun name(name: String): JsonWriter {
    if (stackSize == 0) {
      throw IllegalStateException("JsonWriter is closed.")
    }
    val context = peekScope()
    if (context != EMPTY_OBJECT && context != NONEMPTY_OBJECT
      || deferredName != null
      || promoteValueToName) {
      throw IllegalStateException("Nesting problem.")
    }
    deferredName = name
    pathNames[stackSize - 1] = name
    return this
  }

  @Throws(IOException::class)
  private fun writeDeferredName() {
    if (deferredName != null) {
      beforeName()
      string(sink, deferredName!!)
      deferredName = null
    }
  }

  @Throws(IOException::class)
  override fun value(value: String?): JsonWriter {
    if (value == null) {
      return nullValue()
    }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value)
    }
    writeDeferredName()
    beforeValue()
    string(sink, value)
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun nullValue(): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("null cannot be used as a map key in JSON at path " + getPath())
    }
    if (deferredName != null) {
      if (getSerializeNulls()) {
        writeDeferredName()
      } else {
        deferredName = null
        return this // skip the name and the value
      }
    }
    beforeValue()
    sink.writeUtf8("null")
    pathIndices[stackSize - 1]++
    return this
  }

  //  @Override
  @Throws(IOException::class)
  fun value(value: Boolean): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("Boolean cannot be used as a map key in JSON at path " + getPath())
    }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(if (value) "true" else "false")
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun value(value: Boolean?): JsonWriter {
    if (value == null) {
      return nullValue()
    }
    return value(value)
  }

  @Throws(IOException::class)
  override fun value(value: Double): JsonWriter {
    if (!isLenient() && (value.isNaN() || value.isInfinite())) {
      throw IllegalArgumentException("Numeric values must be finite, but was $value") }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(value.toString())
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun value(value: Long): JsonWriter {
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(value.toString())
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun value(value: Number?): JsonWriter {
    if (value == null) {
      return nullValue()
    }

    val string = value.toString()
    if (!isLenient()
      && (string == "-Infinity" || string == "Infinity" || string == "NaN")) {
      throw IllegalArgumentException("Numeric values must be finite, but was $value")
    }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(string)
    }
    writeDeferredName()
    beforeValue()
    sink.writeUtf8(string)
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun valueSink(): BufferedSink {
    if (promoteValueToName) {
      throw IllegalStateException("BufferedSink cannot be used as a map key in JSON at path " + getPath())
    }
    writeDeferredName()
    beforeValue()
    pushScope(STREAMING_VALUE)
    return object : Sink {
      override fun write(source: Buffer, byteCount: Long) {
        sink.write(source, byteCount)
      }

      override fun close() {
        if (peekScope() != STREAMING_VALUE) {
          throw AssertionError()
        }
        stackSize-- // Remove STREAMING_VALUE from the stack.
        pathIndices[stackSize - 1]++
      }

      override fun flush() {
        sink.flush()
      }

      override fun timeout(): Timeout {
        return Timeout.NONE
      }
    }.buffer()
  }

  /**
   * Ensures all buffered data is written to the underlying [Sink] and flushes that writer.
   */
  @Throws(IOException::class)
  override fun flush() {
    if (stackSize == 0) {
      throw IllegalStateException("JsonWriter is closed.")
    }
    sink.flush()
  }

  /**
   * Flushes and closes this writer and the underlying [Sink].
   *
   * @throws JsonDataException if the JSON document is incomplete.
   */
  @Throws(IOException::class)
  override fun close() {
    sink.close()

    val size = stackSize
    if (size > 1 || size == 1 && scopes[size - 1] != NONEMPTY_DOCUMENT) {
      throw IOException("Incomplete document")
    }
    stackSize = 0
  }

  @Throws(IOException::class)
  private fun newline() {
    if (getIndent().isEmpty()) {
      return
    }

    sink.writeByte('\n'.code)
    var i = 1
    val size = stackSize
    while (i < size) {
      sink.writeUtf8(getIndent())
      i++
    }
  }

  /**
   * Inserts any necessary separators and whitespace before a name. Also adjusts the stack to expect
   * the name's value.
   */
  @Throws(IOException::class)
  private fun beforeName() {
    val context = peekScope()
    if (context == NONEMPTY_OBJECT) { // first in object
      sink.writeByte(','.code)
    } else if (context != EMPTY_OBJECT) {  // not in an object!
      throw IllegalStateException("Nesting problem.")
    }
    newline()
    replaceTop(DANGLING_NAME)
  }

  /**
   * Inserts any necessary separators and whitespace before a literal value, inline array, or inline
   * object. Also adjusts the stack to expect either a closing bracket or another element.
   */
  @Throws(IOException::class)
  private fun beforeValue() {
    val nextTop: Int
    when (peekScope()) {
      NONEMPTY_DOCUMENT -> {
        if (!isLenient()) {
          throw IllegalStateException("JSON must have only one top-level value.")
        }
        nextTop = NONEMPTY_DOCUMENT
      }
      EMPTY_DOCUMENT -> { // first in document
        nextTop = NONEMPTY_DOCUMENT
      }
      NONEMPTY_ARRAY -> { // another in array
        sink.writeByte(','.code)
        newline()
        nextTop = NONEMPTY_ARRAY
      }
      EMPTY_ARRAY -> { // first in array
        newline()
        nextTop = NONEMPTY_ARRAY
      }
      DANGLING_NAME -> { // value for name
        nextTop = NONEMPTY_OBJECT
        sink.writeUtf8(separator)
      }
      STREAMING_VALUE -> throw IllegalStateException("Sink from valueSink() was not closed")
      else -> throw IllegalStateException("Nesting problem.")
    }
    replaceTop(nextTop)
  }

  companion object {
    /*
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
      REPLACEMENT_CHARS['\u000c'/*\f*/.code] = "\\u000c"
    }

    /**
     * Writes `value` as a string literal to `sink`. This wraps the value in double quotes
     * and escapes those characters that require it.
     */
    @Throws(IOException::class)
    fun string(sink: BufferedSink, value: String) {
      val replacements = REPLACEMENT_CHARS
      sink.writeByte('"'.code)
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
          sink.writeUtf8(value, last, i)
        }
        sink.writeUtf8(replacement)
        last = i + 1
      }
      if (last < length) {
        sink.writeUtf8(value, last, length)
      }
      sink.writeByte('"'.code)
    }
  }
}
