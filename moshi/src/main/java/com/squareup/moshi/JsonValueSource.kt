/*
 * Copyright (C) 2020 Square, Inc.
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

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.encodeUtf8
import okio.EOFException
import okio.Source
import kotlin.jvm.JvmOverloads
import kotlin.math.min

/**
 * This source reads a prefix of another source as a JSON value and then terminates. It can read
 * top-level arrays, objects, or strings only.
 *
 * It implements [lenient parsing][JsonReader.setLenient] and has no mechanism to
 * enforce strict parsing. If the input is not valid or lenient JSON the behavior of this source is
 * unspecified.
 */
internal class JsonValueSource @JvmOverloads constructor(
  private val source: BufferedSource,
  /** If non-empty, data from this should be returned before data from [source]. */
  private val prefix: Buffer = Buffer(),
  /**
   * The state indicates what kind of data is readable at [limit]. This also serves
   * double-duty as the type of bytes we're interested in while in this state.
   */
  private var state: ByteString = STATE_JSON,
  /**
   * The level of nesting of arrays and objects. When the end of string, array, or object is
   * reached, this should be compared against 0. If it is zero, then we've read a complete value and
   * this source is exhausted.
   */
  private var stackSize: Int = 0
) : Source {
  private val buffer: Buffer = source.buffer

  /** The number of bytes immediately returnable to the caller. */
  private var limit: Long = 0
  private var closed = false

  /**
   * Advance [limit] until any of these conditions are met:
   *  * Limit is at least `byteCount`. We can satisfy the caller's request!
   *  * The JSON value is complete. This stream is exhausted.
   *  * We have some data to return and returning more would require reloading the buffer. We prefer to return some
   *  data immediately when more data requires blocking.
   *
   * @throws EOFException if the stream is exhausted before the JSON object completes.
   */
  private fun advanceLimit(byteCount: Long) {
    while (limit < byteCount) {
      // If we've finished the JSON object, we're done.
      if (state === STATE_END_OF_JSON) {
        return
      }

      // If we can't return any bytes without more data in the buffer, grow the buffer.
      if (limit == buffer.size) {
        if (limit > 0L) return
        source.require(1L)
      }

      // Find the next interesting character for the current state. If the buffer doesn't have one,
      // then we can read the entire buffer.
      val index = buffer.indexOfElement(state, limit)
      if (index == -1L) {
        limit = buffer.size
        continue
      }
      val b = buffer[index]
      when {
        state === STATE_JSON -> when (b.toInt().toChar()) {
          '[', '{' -> {
            stackSize++
            limit = index + 1
          }
          ']', '}' -> {
            stackSize--
            if (stackSize == 0) state = STATE_END_OF_JSON
            limit = index + 1
          }
          '\"' -> {
            state = STATE_DOUBLE_QUOTED
            limit = index + 1
          }
          '\'' -> {
            state = STATE_SINGLE_QUOTED
            limit = index + 1
          }
          '/' -> {
            source.require(index + 2)
            when (buffer[index + 1]) {
              '/'.code.toByte() -> {
                state = STATE_END_OF_LINE_COMMENT
                limit = index + 2
              }
              '*'.code.toByte() -> {
                state = STATE_C_STYLE_COMMENT
                limit = index + 2
              }
              else -> {
                limit = index + 1
              }
            }
          }
          '#' -> {
            state = STATE_END_OF_LINE_COMMENT
            limit = index + 1
          }
        }
        state === STATE_SINGLE_QUOTED || state === STATE_DOUBLE_QUOTED -> {
          if (b == '\\'.code.toByte()) {
            source.require(index + 2)
            limit = index + 2
          } else {
            state = if (stackSize > 0) STATE_JSON else STATE_END_OF_JSON
            limit = index + 1
          }
        }
        state === STATE_C_STYLE_COMMENT -> {
          source.require(index + 2)
          if (buffer[index + 1] == '/'.code.toByte()) {
            limit = index + 2
            state = STATE_JSON
          } else {
            limit = index + 1
          }
        }
        state === STATE_END_OF_LINE_COMMENT -> {
          limit = index + 1
          state = STATE_JSON
        }
        else -> {
          throw AssertionError()
        }
      }
    }
  }

  /**
   * Discards any remaining JSON data in this source that was left behind after it was closed. It is
   * an error to call [read] after calling this method.
   */
  fun discard() {
    closed = true
    while (state !== STATE_END_OF_JSON) {
      advanceLimit(8192)
      source.skip(limit)
    }
  }

  override fun read(sink: Buffer, byteCount: Long): Long {
    var mutableByteCount = byteCount
    check(!closed) { "closed" }
    if (mutableByteCount == 0L) return 0L

    // If this stream has a prefix, consume that first.
    if (!prefix.exhausted()) {
      val prefixResult = prefix.read(sink, mutableByteCount)
      mutableByteCount -= prefixResult
      if (buffer.exhausted()) return prefixResult // Defer a blocking call.
      val suffixResult = read(sink, mutableByteCount)
      return if (suffixResult != -1L) suffixResult + prefixResult else prefixResult
    }
    advanceLimit(mutableByteCount)
    if (limit == 0L) {
      if (state !== STATE_END_OF_JSON) throw AssertionError()
      return -1L
    }
    val result = min(mutableByteCount, limit)
    sink.write(buffer, result)
    limit -= result
    return result
  }

  override fun timeout() = source.timeout()

  override fun close() {
    // Note that this does not close the underlying source; that's the creator's responsibility.
    closed = true
  }

  companion object {
    val STATE_JSON: ByteString = "[]{}\"'/#".encodeUtf8()
    val STATE_SINGLE_QUOTED: ByteString = "'\\".encodeUtf8()
    val STATE_DOUBLE_QUOTED: ByteString = "\"\\".encodeUtf8()
    val STATE_END_OF_LINE_COMMENT: ByteString = "\r\n".encodeUtf8()
    val STATE_C_STYLE_COMMENT: ByteString = "*".encodeUtf8()
    val STATE_END_OF_JSON: ByteString = EMPTY
  }
}
