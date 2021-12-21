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

import com.squareup.moshi.JsonScope.CLOSED

import com.squareup.moshi.JsonValueReader.JsonIterator
import kotlin.Throws
import java.math.BigDecimal
import okio.Buffer
import okio.BufferedSource
import kotlin.IllegalStateException
import kotlin.UnsupportedOperationException
import okio.IOException

/**
 * This class reads a JSON document by traversing a Java object comprising maps, lists, and JSON
 * primitives. It does depth-first traversal keeping a stack starting with the root object. During
 * traversal a stack tracks the current position in the document:
 *
 *  * The next element to act upon is on the top of the stack.
 *  * When the top of the stack is a [List], calling [.beginArray] replaces the
 * list with a [JsonIterator]. The first element of the iterator is pushed on top of the
 * iterator.
 *  * Similarly, when the top of the stack is a [Map], calling [.beginObject]
 * replaces the map with an [JsonIterator] of its entries. The first element of the
 * iterator is pushed on top of the iterator.
 *  * When the top of the stack is a [Map.Entry], calling [.nextName] returns the
 * entry's key and replaces the entry with its value on the stack.
 *  * When an element is consumed it is popped. If the new top of the stack has a non-exhausted
 * iterator, the next element of that iterator is pushed.
 *  * If the top of the stack is an exhausted iterator, calling [.endArray] or [       ][.endObject] will pop it.
 *
 */
internal class JsonValueReader : JsonReader {
  private var stack: Array<Any?>

  constructor(root: Any?) {
    scopes[stackSize] = JsonScope.NONEMPTY_DOCUMENT
    stack = arrayOfNulls(32)
    stack[stackSize++] = root
  }

  /** Copy-constructor makes a deep copy for peeking.  */
  constructor(copyFrom: JsonValueReader) : super(copyFrom) {
    stack = copyFrom.stack.clone()
    for (i in 0 until stackSize) {
      if (stack[i] is JsonIterator) {
        stack[i] = (stack[i] as JsonIterator).clone()
      }
    }
  }

  @Throws(IOException::class)
  override fun beginArray() {
    val peeked = require(List::class.java, Token.BEGIN_ARRAY)!!

    val iterator = JsonIterator(Token.END_ARRAY, peeked.toTypedArray(), 0)
    stack[stackSize - 1] = iterator
    scopes[stackSize - 1] = JsonScope.EMPTY_ARRAY
    pathIndices[stackSize - 1] = 0

    // If the iterator isn't empty push its first value onto the stack.
    if (iterator.hasNext()) {
      push(iterator.next())
    }
  }

  @Throws(IOException::class)
  override fun endArray() {
    val peeked = require(JsonIterator::class.java, Token.END_ARRAY)!!
    if (peeked.endToken != Token.END_ARRAY || peeked.hasNext()) {
      throw typeMismatch(peeked, Token.END_ARRAY)
    }
    remove()
  }

  @Throws(IOException::class)
  override fun beginObject() {
    val peeked = require(Map::class.java, Token.BEGIN_OBJECT)!!

    val iterator = JsonIterator(Token.END_OBJECT, peeked.entries.toTypedArray(), 0)
    stack[stackSize - 1] = iterator
    scopes[stackSize - 1] = JsonScope.EMPTY_OBJECT

    // If the iterator isn't empty push its first value onto the stack.
    if (iterator.hasNext()) {
      push(iterator.next())
    }
  }

  @Throws(IOException::class)
  override fun endObject() {
    val peeked = require(JsonIterator::class.java, Token.END_OBJECT)!!
    if (peeked.endToken !== Token.END_OBJECT || peeked.hasNext()) {
      throw typeMismatch(peeked, Token.END_OBJECT)
    }
    pathNames[stackSize - 1] = null
    remove()
  }

  @Throws(IOException::class)
  override fun hasNext(): Boolean {
    if (stackSize == 0) return false

    val peeked = stack[stackSize - 1]
    return peeked !is Iterator<*> || peeked.hasNext()
  }

  @Throws(IOException::class)
  override fun peek(): Token {
    if (stackSize == 0) return Token.END_DOCUMENT

    // If the top of the stack is an iterator, take its first element and push it on the stack.
    val peeked = stack[stackSize - 1]
    if (peeked is JsonIterator) return peeked.endToken
    if (peeked is List<*>) return Token.BEGIN_ARRAY
    if (peeked is Map<*, *>) return Token.BEGIN_OBJECT
    if (peeked is Map.Entry<*, *>) return Token.NAME
    if (peeked is String) return Token.STRING
    if (peeked is Boolean) return Token.BOOLEAN
    if (peeked is Number) return Token.NUMBER
    if (peeked == null) return Token.NULL
    if (peeked == JSON_READER_CLOSED) throw IllegalStateException("JsonReader is closed")

    throw typeMismatch(peeked, "a JSON value")
  }

  @Throws(IOException::class)
  override fun nextName(): String {
    val peeked = require(Map.Entry::class.java, Token.NAME)!!

    // Swap the Map.Entry for its value on the stack and return its key.
    val result = stringKey(peeked)
    stack[stackSize - 1] = peeked.value
    pathNames[stackSize - 2] = result
    return result
  }

  @Throws(IOException::class)
  override fun selectName(options: Options): Int {
    val peeked = require(Map.Entry::class.java, Token.NAME)!!
    val name = stringKey(peeked)
    var i = 0
    val length = options.strings.size
    while (i < length) {
      // Swap the Map.Entry for its value on the stack and return its key.
      if (options.strings[i] == name) {
        stack[stackSize - 1] = peeked.value
        pathNames[stackSize - 2] = name
        return i
      }
      i++
    }
    return -1
  }

  @Throws(IOException::class)
  override fun skipName() {
    if (failOnUnknown()) {
      // Capture the peeked value before nextName() since it will reset its value.
      val peeked = peek()
      nextName() // Move the path forward onto the offending name.
      throw JsonDataException("Cannot skip unexpected " + peeked + " at " + getPath())
    }

    val peeked = require(Map.Entry::class.java, Token.NAME)!!

    // Swap the Map.Entry for its value on the stack.
    stack[stackSize - 1] = peeked.value
    pathNames[stackSize - 2] = "null"
  }

  @Throws(IOException::class)
  override fun nextString(): String? {
    val peeked = if (stackSize != 0) stack[stackSize - 1] else null
    if (peeked is String) {
      remove()
      return peeked
    }
    if (peeked is Number) {
      remove()
      return peeked.toString()
    }
    if (peeked == JSON_READER_CLOSED) {
      throw IllegalStateException("JsonReader is closed")
    }
    throw typeMismatch(peeked, Token.STRING)
  }

  @Throws(IOException::class)
  override fun selectString(options: Options): Int {
    val peeked = if (stackSize != 0) stack[stackSize - 1] else null

    if (peeked !is String) {
      if (peeked == JSON_READER_CLOSED) {
        throw IllegalStateException("JsonReader is closed")
      }
      return -1
    }

    var i = 0
    val length = options.strings.size
    while (i < length) {
      if (options.strings[i] == peeked) {
        remove()
        return i
      }
      i++
    }
    return -1
  }

  @Throws(IOException::class)
  override fun nextBoolean(): Boolean {
    val peeked = require(Boolean::class.javaObjectType, Token.BOOLEAN)!!
    remove()
    return peeked
  }

  @Throws(IOException::class)
  override fun <T> nextNull(): T? {
    require(Unit::class.java, Token.NULL)
    remove()
    return null
  }

  @Throws(IOException::class)
  override fun nextDouble(): Double {
    val peeked = require(Any::class.java, Token.NUMBER)!!

    val result = when (peeked) {
      is Number -> {
        peeked.toDouble()
      }
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
    if (!isLenient() && (result.isNaN() || result.isInfinite())) {
      throw JsonEncodingException(
        "JSON forbids NaN and infinities: " + result + " at path " + getPath()
      )
    }
    remove()
    return result
  }

  @Throws(IOException::class)
  override fun nextLong(): Long {
    val peeked = require(Any::class.java, Token.NUMBER)!!

    var result: Long
    when (peeked) {
      is Number -> {
        result = peeked.toLong()
      }
      is String -> {
        try {
          result = peeked.toLong()
        } catch (e: NumberFormatException) {
          try {
            val asDecimal = BigDecimal(peeked)
            result = asDecimal.longValueExact()
          } catch (e2: NumberFormatException) {
            throw typeMismatch(peeked, Token.NUMBER)
          }
        }
      }
      else -> {
        throw typeMismatch(peeked, Token.NUMBER)
      }
    }
    remove()
    return result
  }

  @Throws(IOException::class)
  override fun nextInt(): Int {
    val peeked = require(Any::class.java, Token.NUMBER)!!

    var result: Int
    when (peeked) {
      is Number -> {
        result = peeked.toInt()
      }
      is String -> {
        try {
          result = peeked.toInt()
        } catch (e: NumberFormatException) {
          result = try {
            val asDecimal = BigDecimal(peeked)
            asDecimal.intValueExact()
          } catch (e2: NumberFormatException) {
            throw typeMismatch(peeked, Token.NUMBER)
          }
        }
      }
      else -> {
        throw typeMismatch(peeked, Token.NUMBER)
      }
    }
    remove()
    return result
  }

  @Throws(IOException::class)
  override fun skipValue() {
    if (failOnUnknown()) {
      throw JsonDataException("Cannot skip unexpected " + peek() + " at " + getPath())
    }

    // If this element is in an object clear out the key.
    if (stackSize > 1) {
      pathNames[stackSize - 2] = "null"
    }

    val skipped = if (stackSize != 0) stack[stackSize - 1] else null

    if (skipped is JsonIterator) {
      throw JsonDataException("Expected a value but was " + peek() + " at path " + getPath())
    }
    if (skipped is Map.Entry<*, *>) {
      // We're skipping a name. Promote the map entry's value.
      val entry = stack[stackSize - 1] as Map.Entry<*, *>?
      stack[stackSize - 1] = entry!!.value
    } else if (stackSize > 0) {
      // We're skipping a value.
      remove()
    } else {
      throw JsonDataException("Expected a value but was " + peek() + " at path " + getPath())
    }
  }

  @Throws(IOException::class)
  override fun nextSource(): BufferedSource? {
    val value = readJsonValue()
    val result = Buffer()
    JsonWriter.of(result).use { jsonWriter -> jsonWriter.jsonValue(value) }
    return result
  }

  override fun peekJson(): JsonReader {
    return JsonValueReader(this)
  }

  @Throws(IOException::class)
  override fun promoteNameToValue() {
    if (hasNext()) {
      val name = nextName()
      push(name)
    }
  }

  @Throws(IOException::class)
  override fun close() {
    stack.fill(null, 0, stackSize)
    stack[0] = JSON_READER_CLOSED
    scopes[0] = CLOSED
    stackSize = 1
  }

  private fun push(newTop: Any?) {
    if (stackSize == stack.size) {
      if (stackSize == 256) {
        throw JsonDataException("Nesting too deep at " + getPath())
      }
      scopes = scopes.copyOf(scopes.size * 2)
      pathNames = pathNames.copyOf(pathNames.size * 2)
      pathIndices = pathIndices.copyOf(pathIndices.size * 2)
      stack = stack.copyOf(stack.size * 2)
    }
    stack[stackSize++] = newTop
  }

  /**
   * Returns the top of the stack which is required to be a `type`. Throws if this reader is
   * closed, or if the type isn't what was expected.
   */
  @Throws(IOException::class)
  private fun <T> require(type: Class<T>, expected: Token): T? {
    val peeked = if (stackSize != 0) stack[stackSize - 1] else null

    if (type.isInstance(peeked)) {
      return type.cast(peeked)
    }
    if (peeked == null && expected == Token.NULL) {
      return null
    }
    if (peeked == JSON_READER_CLOSED) {
      throw IllegalStateException("JsonReader is closed")
    }
    throw typeMismatch(peeked, expected)
  }

  private fun stringKey(entry: Map.Entry<*, *>): String {
    val name = entry.key
    if (name is String) return name
    throw typeMismatch(name, Token.NAME)
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
  ) : MutableIterator<Any?>, Cloneable {
    override fun hasNext(): Boolean {
      return next < array.size
    }

    override fun next(): Any? {
      return array[next++]
    }

    override fun remove() {
      throw UnsupportedOperationException()
    }

    public override fun clone(): JsonIterator {
      // No need to copy the array; it's read-only.
      return JsonIterator(endToken, array, next)
    }
  }

  companion object {
    /** Sentinel object pushed on [.stack] when the reader is closed.  */
    private val JSON_READER_CLOSED = Any()
  }
}
