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

import com.squareup.moshi.JsonValueReader.JsonIterator
import com.squareup.moshi.internal.knownNotNull
import okio.Buffer
import okio.BufferedSource
import java.math.BigDecimal

/** Sentinel object pushed on [JsonValueReader.stack] when the reader is closed. */
private val JSON_READER_CLOSED = Any()

/**
 * This class reads a JSON document by traversing a Java object comprising maps, lists, and JSON
 * primitives. It does depth-first traversal keeping a stack starting with the root object. During
 * traversal a stack tracks the current position in the document:
 *  * The next element to act upon is on the top of the stack.
 *  * When the top of the stack is a [List], calling [beginArray] replaces the list with a [JsonIterator]. The first
 *  element of the iterator is pushed on top of the iterator.
 *  * Similarly, when the top of the stack is a [Map], calling [beginObject] replaces the map with an [JsonIterator]
 *  of its entries. The first element of the iterator is pushed on top of the iterator.
 *  * When the top of the stack is a [Map.Entry], calling [nextName] returns the entry's key and replaces the entry
 *  with its value on the stack.
 *  * When an element is consumed it is popped. If the new top of the stack has a non-exhausted iterator, the next
 *  element of that iterator is pushed.
 *  * If the top of the stack is an exhausted iterator, calling [endArray] or [endObject] will pop it.
 */
internal class JsonValueReader : JsonReader {
  private var stack: Array<Any?>

  constructor(root: Any?) {
    scopes[stackSize] = JsonScope.NONEMPTY_DOCUMENT
    stack = arrayOfNulls(32)
    stack[stackSize++] = root
  }

  /** Copy-constructor makes a deep copy for peeking. */
  constructor(copyFrom: JsonValueReader) : super(copyFrom) {
    stack = copyFrom.stack.clone()
    for (i in 0 until stackSize) {
      val element = stack[i]
      if (element is JsonIterator) {
        stack[i] = element.clone()
      }
    }
  }

  override fun beginArray() {
    val peeked = require<List<*>>(Token.BEGIN_ARRAY)
    val iterator = JsonIterator(Token.END_ARRAY, peeked.toTypedArray(), 0)
    stack[stackSize - 1] = iterator
    scopes[stackSize - 1] = JsonScope.EMPTY_ARRAY
    pathIndices[stackSize - 1] = 0

    // If the iterator isn't empty push its first value onto the stack.
    if (iterator.hasNext()) {
      push(iterator.next())
    }
  }

  override fun endArray() {
    val peeked = require<JsonIterator>(Token.END_ARRAY)
    if (peeked.endToken != Token.END_ARRAY || peeked.hasNext()) {
      throw typeMismatch(peeked, Token.END_ARRAY)
    }
    remove()
  }

  override fun beginObject() {
    val peeked = require<Map<*, *>>(Token.BEGIN_OBJECT)
    val iterator = JsonIterator(Token.END_OBJECT, peeked.entries.toTypedArray(), 0)
    stack[stackSize - 1] = iterator
    scopes[stackSize - 1] = JsonScope.EMPTY_OBJECT

    // If the iterator isn't empty push its first value onto the stack.
    if (iterator.hasNext()) {
      push(iterator.next())
    }
  }

  override fun endObject() {
    val peeked = require<JsonIterator>(Token.END_OBJECT)
    if (peeked.endToken != Token.END_OBJECT || peeked.hasNext()) {
      throw typeMismatch(peeked, Token.END_OBJECT)
    }
    pathNames[stackSize - 1] = null
    remove()
  }

  override fun hasNext(): Boolean {
    if (stackSize == 0) return false
    val peeked = stack[stackSize - 1]
    return peeked !is Iterator<*> || peeked.hasNext()
  }

  override fun peek(): Token {
    if (stackSize == 0) return Token.END_DOCUMENT

    // If the top of the stack is an iterator, take its first element and push it on the stack.
    return when (val peeked = stack[stackSize - 1]) {
      is JsonIterator -> peeked.endToken
      is List<*> -> Token.BEGIN_ARRAY
      is Map<*, *> -> Token.BEGIN_OBJECT
      is Map.Entry<*, *> -> Token.NAME
      is String -> Token.STRING
      is Boolean -> Token.BOOLEAN
      is Number -> Token.NUMBER
      null -> Token.NULL
      else -> ifNotClosed(peeked) {
        throw typeMismatch(peeked, "a JSON value")
      }
    }
  }

  override fun nextName(): String {
    val peeked = require<Map.Entry<*, *>>(Token.NAME)

    // Swap the Map.Entry for its value on the stack and return its key.
    val result = stringKey(peeked)
    stack[stackSize - 1] = peeked.value
    pathNames[stackSize - 2] = result
    return result
  }

  override fun selectName(options: Options): Int {
    val peeked = require<Map.Entry<*, *>>(Token.NAME)
    val name = stringKey(peeked)
    for (i in options.strings.indices) {
      // Swap the Map.Entry for its value on the stack and return its key.
      if (options.strings[i] == name) {
        stack[stackSize - 1] = peeked.value
        pathNames[stackSize - 2] = name
        return i
      }
    }
    return -1
  }

  override fun skipName() {
    if (failOnUnknown) {
      // Capture the peeked value before nextName() since it will reset its value.
      val peeked = peek()
      nextName() // Move the path forward onto the offending name.
      throw JsonDataException("Cannot skip unexpected $peeked at $path")
    }
    val (_, value) = require<Map.Entry<*, *>>(Token.NAME)

    // Swap the Map.Entry for its value on the stack.
    stack[stackSize - 1] = value
    pathNames[stackSize - 2] = "null"
  }

  override fun nextString(): String {
    return when (val peeked = if (stackSize != 0) stack[stackSize - 1] else null) {
      is String -> {
        remove()
        peeked
      }
      is Number -> {
        remove()
        peeked.toString()
      }
      else -> ifNotClosed(peeked) {
        throw typeMismatch(peeked, Token.STRING)
      }
    }
  }

  override fun selectString(options: Options): Int {
    val peeked = if (stackSize != 0) stack[stackSize - 1] else null
    if (peeked !is String) {
      ifNotClosed(peeked) {
        -1
      }
    }
    for (i in options.strings.indices) {
      if (options.strings[i] == peeked) {
        remove()
        return i
      }
    }
    return -1
  }

  override fun nextBoolean(): Boolean {
    val peeked = require<Boolean>(Token.BOOLEAN)
    remove()
    return peeked
  }

  override fun <T> nextNull(): T? {
    requireNull()
    remove()
    return null
  }

  override fun nextDouble(): Double {
    val result = when (val peeked = require<Any>(Token.NUMBER)) {
      is Number -> peeked.toDouble()
      is String -> {
        try {
          peeked.toDouble()
        } catch (e: NumberFormatException) {
          throw typeMismatch(peeked, Token.NUMBER)
        }
      }
      else -> {
        throw typeMismatch(peeked, Token.NUMBER)
      }
    }
    if (!lenient && (result.isNaN() || result.isInfinite())) {
      throw JsonEncodingException("JSON forbids NaN and infinities: $result at path $path")
    }
    remove()
    return result
  }

  override fun nextLong(): Long {
    val result: Long = when (val peeked = require<Any>(Token.NUMBER)) {
      is Number -> peeked.toLong()
      is String -> try {
        peeked.toLong()
      } catch (e: NumberFormatException) {
        try {
          BigDecimal(peeked).longValueExact()
        } catch (e2: NumberFormatException) {
          throw typeMismatch(peeked, Token.NUMBER)
        }
      }
      else -> throw typeMismatch(peeked, Token.NUMBER)
    }
    remove()
    return result
  }

  override fun nextInt(): Int {
    val result = when (val peeked = require<Any>(Token.NUMBER)) {
      is Number -> peeked.toInt()
      is String -> try {
        peeked.toInt()
      } catch (e: NumberFormatException) {
        try {
          BigDecimal(peeked).intValueExact()
        } catch (e2: NumberFormatException) {
          throw typeMismatch(peeked, Token.NUMBER)
        }
      }
      else -> throw typeMismatch(peeked, Token.NUMBER)
    }
    remove()
    return result
  }

  override fun skipValue() {
    if (failOnUnknown) {
      throw JsonDataException("Cannot skip unexpected ${peek()} at $path")
    }

    // If this element is in an object clear out the key.
    if (stackSize > 1) {
      pathNames[stackSize - 2] = "null"
    }

    val skipped = if (stackSize != 0) stack[stackSize - 1] else null
    if (skipped is JsonIterator) {
      throw JsonDataException("Expected a value but was ${peek()} at path $path")
    }
    if (skipped is Map.Entry<*, *>) {
      // We're skipping a name. Promote the map entry's value.
      val entry = stack[stackSize - 1] as Map.Entry<*, *>
      stack[stackSize - 1] = entry.value
    } else if (stackSize > 0) {
      // We're skipping a value.
      remove()
    } else {
      throw JsonDataException("Expected a value but was ${peek()} at path $path")
    }
  }

  override fun nextSource(): BufferedSource {
    val value = readJsonValue()
    val result = Buffer()
    JsonWriter.of(result).use { jsonWriter -> jsonWriter.jsonValue(value) }
    return result
  }

  override fun peekJson(): JsonReader = JsonValueReader(this)

  override fun promoteNameToValue() {
    if (hasNext()) {
      val name = nextName()
      push(name)
    }
  }

  override fun close() {
    stack.fill(null, 0, stackSize)
    stack[0] = JSON_READER_CLOSED
    scopes[0] = JsonScope.CLOSED
    stackSize = 1
  }

  private fun push(newTop: Any?) {
    if (stackSize == stack.size) {
      if (stackSize == 256) {
        throw JsonDataException("Nesting too deep at $path")
      }
      scopes = scopes.copyOf(scopes.size * 2)
      pathNames = pathNames.copyOf(pathNames.size * 2)
      pathIndices = pathIndices.copyOf(pathIndices.size * 2)
      stack = stack.copyOf(stack.size * 2)
    }
    stack[stackSize++] = newTop
  }

  private inline fun <reified T> require(expected: Token): T = knownNotNull(require(T::class.java, expected))

  private fun requireNull() = require(Void::class.java, Token.NULL)

  /**
   * Returns the top of the stack which is required to be a `type`. Throws if this reader is
   * closed, or if the type isn't what was expected.
   */
  private fun <T> require(type: Class<T>, expected: Token): T? {
    val peeked = if (stackSize != 0) stack[stackSize - 1] else null
    if (type.isInstance(peeked)) {
      return type.cast(peeked)
    }
    if (peeked == null && expected == Token.NULL) {
      return null
    }
    ifNotClosed(peeked) {
      throw typeMismatch(peeked, expected)
    }
  }

  private fun stringKey(entry: Map.Entry<*, *>): String {
    val name = entry.key
    if (name is String) return name
    throw typeMismatch(name, Token.NAME)
  }

  private inline fun <T> ifNotClosed(peeked: Any?, body: () -> T): T {
    check(peeked !== JSON_READER_CLOSED) { "JsonReader is closed" }
    return body()
  }

  /**
   * Removes a value and prepares for the next. If we're iterating a map or list this advances the
   * iterator.
   */
  private fun remove() {
    stackSize--
    stack[stackSize] = null
    scopes[stackSize] = 0

    // If we're iterating an array or an object push its next element on to the stack.
    if (stackSize > 0) {
      pathIndices[stackSize - 1]++
      val parent = stack[stackSize - 1]
      if (parent is Iterator<*> && parent.hasNext()) {
        push(parent.next())
      }
    }
  }

  internal class JsonIterator(
    val endToken: Token,
    val array: Array<Any?>,
    var next: Int
  ) : Iterator<Any?>, Cloneable {
    override fun hasNext() = next < array.size

    override fun next() = array[next++]

    // No need to copy the array; it's read-only.
    public override fun clone() = JsonIterator(endToken, array, next)
  }
}
