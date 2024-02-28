/*
 * Copyright (C) 2017 Square, Inc.
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

import com.squareup.moshi.JsonScope.EMPTY_ARRAY
import com.squareup.moshi.JsonScope.EMPTY_DOCUMENT
import com.squareup.moshi.JsonScope.EMPTY_OBJECT
import com.squareup.moshi.JsonScope.NONEMPTY_DOCUMENT
import com.squareup.moshi.JsonScope.STREAMING_VALUE
import com.squareup.moshi.internal.knownNotNull
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.IOException
import okio.buffer
import java.math.BigDecimal

/** Writes JSON by building a Java object comprising maps, lists, and JSON primitives. */
internal class JsonValueWriter : JsonWriter() {
  var stack = arrayOfNulls<Any>(32)
  private var deferredName: String? = null

  init {
    pushScope(EMPTY_DOCUMENT)
  }

  fun root(): Any? {
    val size = stackSize
    check(size <= 1 && (size != 1 || scopes[0] == NONEMPTY_DOCUMENT)) { "Incomplete document" }
    return stack[0]
  }

  override fun beginArray(): JsonWriter {
    check(!promoteValueToName) { "Array cannot be used as a map key in JSON at path $path" }
    if (stackSize == flattenStackSize && scopes[stackSize - 1] == EMPTY_ARRAY) {
      // Cancel this open. Invert the flatten stack size until this is closed.
      flattenStackSize = flattenStackSize.inv()
      return this
    }
    checkStack()
    val list = mutableListOf<Any>()
    add(list)
    stack[stackSize] = list
    pathIndices[stackSize] = 0
    pushScope(EMPTY_ARRAY)
    return this
  }

  override fun endArray(): JsonWriter {
    check(peekScope() == EMPTY_ARRAY) { "Nesting problem." }
    if (stackSize == flattenStackSize.inv()) {
      // Cancel this close. Restore the flattenStackSize so we're ready to flatten again!
      flattenStackSize = flattenStackSize.inv()
      return this
    }
    stackSize--
    stack[stackSize] = null
    pathIndices[stackSize - 1]++
    return this
  }

  override fun beginObject(): JsonWriter {
    check(!promoteValueToName) { "Object cannot be used as a map key in JSON at path $path" }
    if (stackSize == flattenStackSize && scopes[stackSize - 1] == EMPTY_OBJECT) {
      // Cancel this open. Invert the flatten stack size until this is closed.
      flattenStackSize = flattenStackSize.inv()
      return this
    }
    checkStack()
    val map = LinkedHashTreeMap<String, Any>()
    add(map)
    stack[stackSize] = map
    pushScope(EMPTY_OBJECT)
    return this
  }

  override fun endObject(): JsonWriter {
    check(peekScope() == EMPTY_OBJECT) { "Nesting problem." }
    check(deferredName == null) { "Dangling name: $deferredName" }
    if (stackSize == flattenStackSize.inv()) {
      // Cancel this close. Restore the flattenStackSize so we're ready to flatten again!
      flattenStackSize = flattenStackSize.inv()
      return this
    }
    promoteValueToName = false
    stackSize--
    stack[stackSize] = null
    pathNames[stackSize] = null // Free the last path name so that it can be garbage collected!
    pathIndices[stackSize - 1]++
    return this
  }

  override fun name(name: String): JsonWriter {
    check(stackSize != 0) { "JsonWriter is closed." }
    check(peekScope() == EMPTY_OBJECT && deferredName == null && !promoteValueToName) { "Nesting problem." }
    deferredName = name
    pathNames[stackSize - 1] = name
    return this
  }

  override fun value(value: String?): JsonWriter {
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value!!)
    }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  override fun nullValue(): JsonWriter {
    check(!promoteValueToName) { "null cannot be used as a map key in JSON at path $path" }
    add(null)
    pathIndices[stackSize - 1]++
    return this
  }

  override fun value(value: Boolean): JsonWriter {
    check(!promoteValueToName) { "Boolean cannot be used as a map key in JSON at path $path" }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  override fun value(value: Boolean?): JsonWriter {
    check(!promoteValueToName) { "Boolean cannot be used as a map key in JSON at path $path" }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  override fun value(value: Double): JsonWriter {
    require(isLenient || !value.isNaN() && value != Double.NEGATIVE_INFINITY && value != Double.POSITIVE_INFINITY) {
      "Numeric values must be finite, but was $value"
    }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  override fun value(value: Long): JsonWriter {
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  override fun value(value: Number?): JsonWriter = apply {
    when (value) {
      null -> nullValue()

      // If it's trivially converted to a long, do that.
      is Byte, is Short, is Int, is Long -> value(value.toLong())

      // If it's trivially converted to a double, do that.
      is Float, is Double -> value(value.toDouble())

      else -> {
        // Everything else gets converted to a BigDecimal.
        val bigDecimalValue = if (value is BigDecimal) value else BigDecimal(value.toString())
        if (promoteValueToName) {
          promoteValueToName = false
          return name(bigDecimalValue.toString())
        }
        add(bigDecimalValue)
        pathIndices[stackSize - 1]++
      }
    }
  }

  override fun valueSink(): BufferedSink {
    check(!promoteValueToName) { "BufferedSink cannot be used as a map key in JSON at path $path" }
    check(peekScope() != STREAMING_VALUE) { "Sink from valueSink() was not closed" }
    pushScope(STREAMING_VALUE)
    val buffer = Buffer()
    return object : ForwardingSink(buffer) {
      override fun close() {
        if (peekScope() != STREAMING_VALUE || stack[stackSize] != null) {
          throw AssertionError()
        }
        stackSize-- // Remove STREAMING_VALUE from the stack.
        val value = JsonReader.of(buffer).readJsonValue()
        val serializeNulls = serializeNulls
        this@JsonValueWriter.serializeNulls = true
        try {
          add(value)
        } finally {
          this@JsonValueWriter.serializeNulls = serializeNulls
        }
        pathIndices[stackSize - 1]++
      }
    }.buffer()
  }

  override fun close() {
    val size = stackSize
    if (size > 1 || size == 1 && scopes[0] != NONEMPTY_DOCUMENT) {
      throw IOException("Incomplete document")
    }
    stackSize = 0
  }

  override fun flush() {
    check(stackSize != 0) { "JsonWriter is closed." }
  }

  private fun add(newTop: Any?): JsonValueWriter {
    val scope = peekScope()
    when {
      stackSize == 1 -> {
        check(scope == EMPTY_DOCUMENT) { "JSON must have only one top-level value." }
        scopes[stackSize - 1] = NONEMPTY_DOCUMENT
        stack[stackSize - 1] = newTop
      }

      scope == EMPTY_OBJECT && deferredName != null -> {
        if (newTop != null || serializeNulls) {
          // Our maps always have string keys and object values.
          @Suppress("UNCHECKED_CAST")
          val map = stack[stackSize - 1] as MutableMap<String, Any?>
          // Safe to assume not null as this is single-threaded and smartcast just can't handle it
          val replaced = map.put(knownNotNull(deferredName), newTop)
          require(replaced == null) {
            "Map key '$deferredName' has multiple values at path $path: $replaced and $newTop"
          }
        }
        deferredName = null
      }

      scope == EMPTY_ARRAY -> {
        // Our lists always have object values.
        @Suppress("UNCHECKED_CAST")
        val list = stack[stackSize - 1] as MutableList<Any?>
        list.add(newTop)
      }

      scope == STREAMING_VALUE -> throw IllegalStateException("Sink from valueSink() was not closed")

      else -> throw IllegalStateException("Nesting problem.")
    }
    return this
  }
}
