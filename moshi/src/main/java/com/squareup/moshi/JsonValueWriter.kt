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

import okio.buffer
import kotlin.Throws
import java.math.BigDecimal
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import kotlin.IllegalStateException
import okio.IOException

/** Writes JSON by building a Java object comprising maps, lists, and JSON primitives.  */
internal class JsonValueWriter : JsonWriter() {
  @JvmField
  var stack = arrayOfNulls<Any>(32)
  private var deferredName: String? = null

  init {
    pushScope(EMPTY_DOCUMENT)
  }

  fun root(): Any? {
    val size = stackSize
    if(size > 1 || size == 1 && scopes[size - 1] != NONEMPTY_DOCUMENT) {
      throw IllegalStateException("Incomplete document")
    }
    return stack[0]
  }

  @Throws(IOException::class)
  override fun beginArray(): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("Array cannot be used as a map key in JSON at path ${getPath()}")
    }
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

  @Throws(IOException::class)
  override fun endArray(): JsonWriter {
    if (peekScope() != EMPTY_ARRAY) {
      throw IllegalStateException("Nesting problem.")
    }
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

  @Throws(IOException::class)
  override fun beginObject(): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("Object cannot be used as a map key in JSON at path ${getPath()}")
    }
    if (stackSize == flattenStackSize && scopes[stackSize - 1] == EMPTY_OBJECT) {
      // Cancel this open. Invert the flatten stack size until this is closed.
      flattenStackSize = flattenStackSize.inv()
      return this
    }
    checkStack()
    val map = sortedMapOf<String, Any>()
    add(map)
    stack[stackSize] = map
    pushScope(EMPTY_OBJECT)
    return this
  }

  @Throws(IOException::class)
  override fun endObject(): JsonWriter {
    if (peekScope() != EMPTY_OBJECT) {
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
    promoteValueToName = false
    stackSize--
    stack[stackSize] = null
    pathNames[stackSize] = null // Free the last path name so that it can be garbage collected!
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun name(name: String): JsonWriter {
    if (stackSize == 0) {
      throw IllegalStateException("JsonWriter is closed.")
    }
    if (peekScope() != EMPTY_OBJECT || deferredName != null || promoteValueToName) {
      throw IllegalStateException("Nesting problem.")
    }
    deferredName = name
    pathNames[stackSize - 1] = name
    return this
  }

  @Throws(IOException::class)
  override fun value(value: String?): JsonWriter {
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value!!)
    }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun nullValue(): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("null cannot be used as a map key in JSON at path ${getPath()}")
    }
    add(null)
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun value(value: Boolean?): JsonWriter {
    if (promoteValueToName) {
      throw IllegalStateException("Boolean cannot be used as a map key in JSON at path ${getPath()}") }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

//  @Throws(IOException::class)
//  override fun value(value: Boolean): JsonWriter {
//    if (promoteValueToName) {
//      throw IllegalStateException("Boolean cannot be used as a map key in JSON at path $path") }
//    add(value)
//    pathIndices[stackSize - 1]++
//    return this
//  }

  @Throws(IOException::class)
  override fun value(value: Double): JsonWriter {
    if((!isLenient()
        && (java.lang.Double.isNaN(value) || value == Double.NEGATIVE_INFINITY || value == Double.POSITIVE_INFINITY))
    ) {
      throw IllegalArgumentException("Numeric values must be finite, but was $value")
    }
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun value(value: Long): JsonWriter {
    if (promoteValueToName) {
      promoteValueToName = false
      return name(value.toString())
    }
    add(value)
    pathIndices[stackSize - 1]++
    return this
  }

  @Throws(IOException::class)
  override fun value(value: Number?): JsonWriter {
    // If it's trivially converted to a long, do that.
    if (value is Byte
      || value is Short
      || value is Int
      || value is Long
    ) {
      return value(value.toLong())
    }

    // If it's trivially converted to a double, do that.
    if (value is Float || value is Double) {
      return value(value.toDouble())
    }

    if (value == null) {
      return nullValue()
    }

    // Everything else gets converted to a BigDecimal.
    val bigDecimalValue = if (value is BigDecimal) value else BigDecimal(value.toString())
    if (promoteValueToName) {
      promoteValueToName = false
      return name(bigDecimalValue.toString())
    }
    add(bigDecimalValue)
    pathIndices[stackSize - 1]++
    return this
  }

  override fun valueSink(): BufferedSink {
    if (promoteValueToName) {
      throw IllegalStateException("BufferedSink cannot be used as a map key in JSON at path ${getPath()}")
    }
    if (peekScope() == STREAMING_VALUE) {
      throw IllegalStateException("Sink from valueSink() was not closed")
    }
    pushScope(STREAMING_VALUE)

    val buffer = Buffer()
    return object : ForwardingSink(buffer) {
      override fun close() {
        if (peekScope() != STREAMING_VALUE || stack[stackSize] != null) {
          throw AssertionError()
        }
        stackSize-- // Remove STREAMING_VALUE from the stack.

        val value = JsonReader.of(buffer).readJsonValue()
        val serializeNulls = this@JsonValueWriter.getSerializeNulls()
        this@JsonValueWriter.setSerializeNulls(true)
        try {
          add(value)
        } finally {
          this@JsonValueWriter.setSerializeNulls(serializeNulls)
        }
        pathIndices[stackSize - 1]++
      }
    }.buffer()
  }

  @Throws(IOException::class)
  override fun close() {
    val size = stackSize
    if (size > 1 || size == 1 && scopes[size - 1] != NONEMPTY_DOCUMENT) {
      throw IOException("Incomplete document")
    }
    stackSize = 0
  }

  @Throws(IOException::class)
  override fun flush() {
    if (stackSize == 0) {
      throw IllegalStateException("JsonWriter is closed.")
    }
  }

  private fun add(newTop: Any?): JsonValueWriter {
    val scope = peekScope()

    if (stackSize == 1) {
      if (scope != EMPTY_DOCUMENT) {
        throw IllegalStateException("JSON must have only one top-level value.")
      }
      scopes[stackSize - 1] = NONEMPTY_DOCUMENT
      stack[stackSize - 1] = newTop

    } else if (scope == EMPTY_OBJECT && deferredName != null) {
      if (newTop != null || getSerializeNulls()) {
        @Suppress("UNCHECKED_CAST")
        val map = stack[stackSize - 1] as MutableMap<String, Any?> // Our maps always have string keys and object values.
        val replaced = map.put(deferredName!!, newTop)
        if (replaced != null) {
          throw IllegalArgumentException("Map key '"
            + deferredName
            + "' has multiple values at path "
            + getPath()
            + ": "
            + replaced
            + " and "
            + newTop)
        }
      }
      deferredName = null

    } else if (scope == EMPTY_ARRAY) {
      @Suppress("UNCHECKED_CAST")
      val list = stack[stackSize - 1] as MutableList<Any?> // Our lists always have object values.
      list.add(newTop)

    } else if (scope == STREAMING_VALUE) {
      throw IllegalStateException("Sink from valueSink() was not closed")

    } else {
      throw IllegalStateException("Nesting problem.")
    }

    return this
  }
}
