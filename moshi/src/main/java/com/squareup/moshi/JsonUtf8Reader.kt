/*
 * Copyright (C) 2010 Square, Inc.
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
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import java.io.EOFException
import java.io.IOException
import java.math.BigDecimal
import kotlin.contracts.contract

internal class JsonUtf8Reader : JsonReader {
  /** The input JSON. */
  private val source: BufferedSource
  private val buffer: Buffer
  private var peeked = PEEKED_NONE

  /**
   * A peeked value that was composed entirely of digits with an optional leading dash. Positive
   * values may not have a leading 0.
   */
  private var peekedLong = 0L

  /** The number of characters in a peeked number literal. */
  private var peekedNumberLength = 0

  /**
   * A peeked string that should be parsed on the next double, long or string. This is populated
   * before a numeric value is parsed and used if that parsing fails.
   */
  private var peekedString: String? = null

  /**
   * If non-null, the most recent value read was [nextSource]. The caller may be
   * mid-stream so it is necessary to call [JsonValueSource.discard] to get to the end of the
   * current JSON value before proceeding.
   */
  private var valueSource: JsonValueSource? = null

  constructor(source: BufferedSource) {
    this.source = source
    buffer = source.buffer
    pushScope(JsonScope.EMPTY_DOCUMENT)
  }

  /** Copy-constructor makes a deep copy for peeking. */
  constructor(copyFrom: JsonUtf8Reader) : super(copyFrom) {
    val sourcePeek = copyFrom.source.peek()
    source = sourcePeek
    buffer = sourcePeek.buffer
    peeked = copyFrom.peeked
    peekedLong = copyFrom.peekedLong
    peekedNumberLength = copyFrom.peekedNumberLength
    peekedString = copyFrom.peekedString

    // Make sure our buffer has as many bytes as the source's buffer. This is necessary because
    // JsonUtf8Reader assumes any data it has peeked (like the peekedNumberLength) are buffered.
    try {
      sourcePeek.require(copyFrom.buffer.size)
    } catch (e: IOException) {
      throw AssertionError()
    }
  }

  override fun beginArray() {
    val p = peekIfNone()
    if (p == PEEKED_BEGIN_ARRAY) {
      pushScope(JsonScope.EMPTY_ARRAY)
      pathIndices[stackSize - 1] = 0
      peeked = PEEKED_NONE
    } else {
      throw JsonDataException("Expected BEGIN_ARRAY but was ${peek()} at path $path")
    }
  }

  override fun endArray() {
    val p = peekIfNone()
    if (p == PEEKED_END_ARRAY) {
      stackSize--
      pathIndices[stackSize - 1]++
      peeked = PEEKED_NONE
    } else {
      throw JsonDataException("Expected END_ARRAY but was ${peek()} at path $path")
    }
  }

  override fun beginObject() {
    val p = peekIfNone()
    if (p == PEEKED_BEGIN_OBJECT) {
      pushScope(JsonScope.EMPTY_OBJECT)
      peeked = PEEKED_NONE
    } else {
      throw JsonDataException("Expected BEGIN_OBJECT but was ${peek()} at path $path")
    }
  }

  override fun endObject() {
    val p = peekIfNone()
    if (p == PEEKED_END_OBJECT) {
      stackSize--
      pathNames[stackSize] = null // Free the last path name so that it can be garbage collected!
      pathIndices[stackSize - 1]++
      peeked = PEEKED_NONE
    } else {
      throw JsonDataException("Expected END_OBJECT but was ${peek()} at path $path")
    }
  }

  override fun hasNext(): Boolean {
    val p = peekIfNone()
    return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY && p != PEEKED_EOF
  }

  override fun peek(): Token {
    return when (peekIfNone()) {
      PEEKED_BEGIN_OBJECT -> Token.BEGIN_OBJECT
      PEEKED_END_OBJECT -> Token.END_OBJECT
      PEEKED_BEGIN_ARRAY -> Token.BEGIN_ARRAY
      PEEKED_END_ARRAY -> Token.END_ARRAY
      PEEKED_SINGLE_QUOTED_NAME, PEEKED_DOUBLE_QUOTED_NAME, PEEKED_UNQUOTED_NAME, PEEKED_BUFFERED_NAME -> Token.NAME
      PEEKED_TRUE, PEEKED_FALSE -> Token.BOOLEAN
      PEEKED_NULL -> Token.NULL
      PEEKED_SINGLE_QUOTED, PEEKED_DOUBLE_QUOTED, PEEKED_UNQUOTED, PEEKED_BUFFERED -> Token.STRING
      PEEKED_LONG, PEEKED_NUMBER -> Token.NUMBER
      PEEKED_EOF -> Token.END_DOCUMENT
      else -> throw AssertionError()
    }
  }

  private fun doPeek(): Int {
    val peekStack = scopes[stackSize - 1]
    when (peekStack) {
      JsonScope.EMPTY_ARRAY -> scopes[stackSize - 1] = JsonScope.NONEMPTY_ARRAY
      JsonScope.NONEMPTY_ARRAY -> {
        // Look for a comma before the next element.
        val c = nextNonWhitespace(true).toChar()
        buffer.readByte() // consume ']' or ','.
        when (c) {
          ']' -> {
            return setPeeked(PEEKED_END_ARRAY)
          }
          ';' -> checkLenient()
          ',' -> Unit /*no op*/
          else -> throw syntaxError("Unterminated array")
        }
      }
      JsonScope.EMPTY_OBJECT, JsonScope.NONEMPTY_OBJECT -> {
        scopes[stackSize - 1] = JsonScope.DANGLING_NAME
        // Look for a comma before the next element.
        if (peekStack == JsonScope.NONEMPTY_OBJECT) {
          val c = nextNonWhitespace(true).toChar()
          buffer.readByte() // Consume '}' or ','.
          when (c) {
            '}' -> {
              return setPeeked(PEEKED_END_OBJECT)
            }
            ',' -> Unit /*no op*/
            ';' -> checkLenient()
            else -> throw syntaxError("Unterminated object")
          }
        }
        val next = when (val c = nextNonWhitespace(true).toChar()) {
          '"' -> {
            buffer.readByte() // consume the '\"'.
            PEEKED_DOUBLE_QUOTED_NAME
          }
          '\'' -> {
            buffer.readByte() // consume the '\''.
            checkLenient()
            PEEKED_SINGLE_QUOTED_NAME
          }
          '}' -> if (peekStack != JsonScope.NONEMPTY_OBJECT) {
            buffer.readByte() // consume the '}'.
            PEEKED_END_OBJECT
          } else {
            throw syntaxError("Expected name")
          }
          else -> {
            checkLenient()
            if (isLiteral(c.code)) {
              PEEKED_UNQUOTED_NAME
            } else {
              throw syntaxError("Expected name")
            }
          }
        }
        peeked = next
        return next
      }
      JsonScope.DANGLING_NAME -> {
        scopes[stackSize - 1] = JsonScope.NONEMPTY_OBJECT
        // Look for a colon before the value.
        val c = nextNonWhitespace(true).toChar()
        buffer.readByte() // Consume ':'.
        when (c) {
          ':' -> Unit /*no op*/
          '=' -> {
            checkLenient()
            if (source.request(1) && buffer[0].asChar() == '>') {
              buffer.readByte() // Consume '>'.
            }
          }
          else -> throw syntaxError("Expected ':'")
        }
      }
      JsonScope.EMPTY_DOCUMENT -> scopes[stackSize - 1] = JsonScope.NONEMPTY_DOCUMENT
      JsonScope.NONEMPTY_DOCUMENT -> {
        if (nextNonWhitespace(false) == -1) {
          return setPeeked(PEEKED_EOF)
        } else {
          checkLenient()
        }
      }
      JsonScope.STREAMING_VALUE -> {
        valueSource!!.discard()
        valueSource = null
        stackSize--
        return doPeek()
      }
      else -> check(peekStack != JsonScope.CLOSED) { "JsonReader is closed" }
    }
    // "fallthrough" from previous `when`
    when (nextNonWhitespace(true).toChar()) {
      ']' -> {
        return when (peekStack) {
          JsonScope.EMPTY_ARRAY -> {
            buffer.readByte() // Consume ']'.
            setPeeked(PEEKED_END_ARRAY)
          }
          JsonScope.NONEMPTY_ARRAY -> {
            // In lenient mode, a 0-length literal in an array means 'null'.
            checkLenient()
            setPeeked(PEEKED_NULL)
          }
          else -> throw syntaxError("Unexpected value")
        }
      }
      // In lenient mode, a 0-length literal in an array means 'null'.
      ';', ',' -> return when (peekStack) {
        JsonScope.EMPTY_ARRAY, JsonScope.NONEMPTY_ARRAY -> {
          checkLenient()
          setPeeked(PEEKED_NULL)
        }
        else -> throw syntaxError("Unexpected value")
      }
      '\'' -> {
        checkLenient()
        buffer.readByte() // Consume '\''.
        return setPeeked(PEEKED_SINGLE_QUOTED)
      }
      '"' -> {
        buffer.readByte() // Consume '\"'.
        return setPeeked(PEEKED_DOUBLE_QUOTED)
      }
      '[' -> {
        buffer.readByte() // Consume '['.
        return setPeeked(PEEKED_BEGIN_ARRAY)
      }
      '{' -> {
        buffer.readByte() // Consume '{'.
        return setPeeked(PEEKED_BEGIN_OBJECT)
      }
      else -> Unit /* no-op */
    }
    var result = peekKeyword()
    if (result != PEEKED_NONE) {
      return result
    }
    result = peekNumber()
    if (result != PEEKED_NONE) {
      return result
    }
    if (!isLiteral(buffer[0].toInt())) {
      throw syntaxError("Expected value")
    }
    checkLenient()
    return setPeeked(PEEKED_UNQUOTED)
  }

  private fun peekKeyword(): Int {
    // Figure out which keyword we're matching against by its first character.
    var c = buffer[0].asChar()
    val keyword: String
    val keywordUpper: String
    val peeking: Int
    when (c) {
      't', 'T' -> {
        keyword = "true"
        keywordUpper = "TRUE"
        peeking = PEEKED_TRUE
      }
      'f', 'F' -> {
        keyword = "false"
        keywordUpper = "FALSE"
        peeking = PEEKED_FALSE
      }
      'n', 'N' -> {
        keyword = "null"
        keywordUpper = "NULL"
        peeking = PEEKED_NULL
      }
      else -> return PEEKED_NONE
    }

    // Confirm that chars [1..length) match the keyword.
    val length = keyword.length
    for (i in 1 until length) {
      val iAsLong = i.toLong()
      if (!source.request(iAsLong + 1)) {
        return PEEKED_NONE
      }
      c = buffer[iAsLong].asChar()
      if (c != keyword[i] && c != keywordUpper[i]) {
        return PEEKED_NONE
      }
    }
    if (source.request((length + 1).toLong()) && isLiteral(buffer[length.toLong()].toInt())) {
      return PEEKED_NONE // Don't match trues, falsey or nullsoft!
    }

    // We've found the keyword followed either by EOF or by a non-literal character.
    buffer.skip(length.toLong())
    return setPeeked(peeking)
  }

  private fun peekNumber(): Int {
    var value = 0L // Negative to accommodate Long.MIN_VALUE more easily.
    var negative = false
    var fitsInLong = true
    var last = NUMBER_CHAR_NONE
    var i = 0L
    while (true) {
      if (!source.request(i + 1)) {
        break
      }
      when (val c = buffer[i].asChar()) {
        '-' -> {
          when (last) {
            NUMBER_CHAR_NONE -> {
              negative = true
              last = NUMBER_CHAR_SIGN
              i++
              continue
            }
            NUMBER_CHAR_EXP_E -> {
              last = NUMBER_CHAR_EXP_SIGN
              i++
              continue
            }
          }
          return PEEKED_NONE
        }
        '+' -> {
          if (last == NUMBER_CHAR_EXP_E) {
            last = NUMBER_CHAR_EXP_SIGN
            i++
            continue
          }
          return PEEKED_NONE
        }
        'e', 'E' -> {
          if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT) {
            last = NUMBER_CHAR_EXP_E
            i++
            continue
          }
          return PEEKED_NONE
        }
        '.' -> {
          if (last == NUMBER_CHAR_DIGIT) {
            last = NUMBER_CHAR_DECIMAL
            i++
            continue
          }
          return PEEKED_NONE
        }
        else -> {
          if (c !in '0'..'9') {
            if (!isLiteral(c.code)) break
            return PEEKED_NONE
          }
          when (last) {
            NUMBER_CHAR_SIGN, NUMBER_CHAR_NONE -> {
              value = -(c - '0').toLong()
              last = NUMBER_CHAR_DIGIT
            }
            NUMBER_CHAR_DIGIT -> {
              if (value == 0L) {
                return PEEKED_NONE // Leading '0' prefix is not allowed (since it could be octal).
              }
              val newValue = value * 10 - (c - '0').toLong()
              fitsInLong = fitsInLong and
                (
                  value > MIN_INCOMPLETE_INTEGER ||
                    value == MIN_INCOMPLETE_INTEGER &&
                    newValue < value
                  )
              value = newValue
            }
            NUMBER_CHAR_DECIMAL -> last = NUMBER_CHAR_FRACTION_DIGIT
            NUMBER_CHAR_EXP_E, NUMBER_CHAR_EXP_SIGN -> last = NUMBER_CHAR_EXP_DIGIT
          }
        }
      }
      i++
    }

    // We've read a complete number. Decide if it's a PEEKED_LONG or a PEEKED_NUMBER.
    return when {
      last == NUMBER_CHAR_DIGIT &&
        fitsInLong &&
        (value != Long.MIN_VALUE || negative)
        && (value != 0L || !negative) -> {
        peekedLong = if (negative) value else -value
        buffer.skip(i)
        setPeeked(PEEKED_LONG)
      }
      last == NUMBER_CHAR_DIGIT ||
        last == NUMBER_CHAR_FRACTION_DIGIT
        || last == NUMBER_CHAR_EXP_DIGIT -> {
        peekedNumberLength = i.toInt()
        setPeeked(PEEKED_NUMBER)
      }
      else -> PEEKED_NONE
    }
  }

  @Throws(IOException::class)
  private fun isLiteral(c: Int): Boolean {
    return when (c.toChar()) {
      '/', '\\', ';', '#', '=' -> {
        checkLenient() // fall-through
        false
      }
      '{', '}', '[', ']', ':', ',', ' ', '\t', '\u000C'/*\f*/, '\r', '\n' -> false
      else -> true
    }
  }

  @Throws(IOException::class)
  override fun nextName(): String {
    val result = when (peekIfNone()) {
      PEEKED_UNQUOTED_NAME -> nextUnquotedValue()
      PEEKED_DOUBLE_QUOTED_NAME -> nextQuotedValue(DOUBLE_QUOTE_OR_SLASH)
      PEEKED_SINGLE_QUOTED_NAME -> nextQuotedValue(SINGLE_QUOTE_OR_SLASH)
      PEEKED_BUFFERED_NAME -> {
        val name = peekedString!!
        peekedString = null
        name
      }
      else -> throw JsonDataException("Expected a name but was ${peek()} at path $path")
    }
    peeked = PEEKED_NONE
    pathNames[stackSize - 1] = result
    return result
  }

  @Throws(IOException::class)
  override fun selectName(options: Options): Int {
    val p = peekIfNone()
    if (p < PEEKED_SINGLE_QUOTED_NAME || p > PEEKED_BUFFERED_NAME) {
      return -1
    }
    if (p == PEEKED_BUFFERED_NAME) {
      return findName(peekedString, options)
    }
    var result = source.select(options.doubleQuoteSuffix)
    if (result != -1) {
      peeked = PEEKED_NONE
      pathNames[stackSize - 1] = options.strings[result]
      return result
    }

    // The next name may be unnecessary escaped. Save the last recorded path name, so that we
    // can restore the peek state in case we fail to find a match.
    val lastPathName = pathNames[stackSize - 1]
    val nextName = nextName()
    result = findName(nextName, options)
    if (result == -1) {
      peeked = PEEKED_BUFFERED_NAME
      peekedString = nextName
      // We can't push the path further, make it seem like nothing happened.
      pathNames[stackSize - 1] = lastPathName
    }
    return result
  }

  @Throws(IOException::class)
  override fun skipName() {
    if (failOnUnknown) {
      // Capture the peeked value before nextName() since it will reset its value.
      val peeked = peek()
      nextName() // Move the path forward onto the offending name.
      throw JsonDataException("Cannot skip unexpected $peeked at $path")
    }
    val p = peekIfNone()
    when {
      p == PEEKED_UNQUOTED_NAME -> skipUnquotedValue()
      p == PEEKED_DOUBLE_QUOTED_NAME -> skipQuotedValue(DOUBLE_QUOTE_OR_SLASH)
      p == PEEKED_SINGLE_QUOTED_NAME -> skipQuotedValue(SINGLE_QUOTE_OR_SLASH)
      p != PEEKED_BUFFERED_NAME -> throw JsonDataException("Expected a name but was ${peek()} at path $path")
    }
    peeked = PEEKED_NONE
    pathNames[stackSize - 1] = "null"
  }

  /**
   * If `name` is in `options` this consumes it and returns its index. Otherwise this
   * returns -1 and no name is consumed.
   */
  private fun findName(name: String?, options: Options): Int {
    val i = options.strings.indexOfFirst { it == name }
    return if (i > -1) {
      peeked = PEEKED_NONE
      pathNames[stackSize - 1] = name
      i
    } else -1
  }

  override fun nextString(): String {
    val result = when (peekIfNone()) {
      PEEKED_UNQUOTED -> nextUnquotedValue()
      PEEKED_DOUBLE_QUOTED -> nextQuotedValue(DOUBLE_QUOTE_OR_SLASH)
      PEEKED_SINGLE_QUOTED -> nextQuotedValue(SINGLE_QUOTE_OR_SLASH)
      PEEKED_BUFFERED -> {
        val buffered = peekedString!!
        peekedString = null
        buffered
      }
      PEEKED_LONG -> peekedLong.toString()
      PEEKED_NUMBER -> buffer.readUtf8(peekedNumberLength.toLong())
      else -> throw JsonDataException("Expected a string but was ${peek()} at path $path")
    }
    peeked = PEEKED_NONE
    pathIndices[stackSize - 1]++
    return result
  }

  override fun selectString(options: Options): Int {
    val p = peekIfNone()
    if (p < PEEKED_SINGLE_QUOTED || p > PEEKED_BUFFERED) {
      return -1
    }
    if (p == PEEKED_BUFFERED) {
      return findString(peekedString, options)
    }
    var result = source.select(options.doubleQuoteSuffix)
    if (result != -1) {
      peeked = PEEKED_NONE
      pathIndices[stackSize - 1]++
      return result
    }
    val nextString = nextString()
    result = findString(nextString, options)
    if (result == -1) {
      peeked = PEEKED_BUFFERED
      peekedString = nextString
      pathIndices[stackSize - 1]--
    }
    return result
  }

  /**
   * If `string` is in `options` this consumes it and returns its index. Otherwise this
   * returns -1 and no string is consumed.
   */
  private fun findString(string: String?, options: Options): Int {
    val i = options.strings.indexOfFirst { it == string }
    return if (i > -1) {
      peeked = PEEKED_NONE
      pathIndices[stackSize - 1]++
      i
    } else -1
  }

  override fun nextBoolean(): Boolean {
    return when (peekIfNone()) {
      PEEKED_TRUE -> {
        peeked = PEEKED_NONE
        pathIndices[stackSize - 1]++
        true
      }
      PEEKED_FALSE -> {
        peeked = PEEKED_NONE
        pathIndices[stackSize - 1]++
        false
      }
      else -> throw JsonDataException("Expected a boolean but was ${peek()} at path $path")
    }
  }

  override fun <T> nextNull(): T? {
    val p = peekIfNone()
    return if (p == PEEKED_NULL) {
      peeked = PEEKED_NONE
      pathIndices[stackSize - 1]++
      null
    } else {
      throw JsonDataException("Expected null but was ${peek()} at path $path")
    }
  }

  override fun nextDouble(): Double {
    val p = peekIfNone()
    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE
      pathIndices[stackSize - 1]++
      return peekedLong.toDouble()
    }
    val next = when (p) {
      PEEKED_NUMBER -> buffer.readUtf8(peekedNumberLength.toLong()).also { peekedString = it }
      PEEKED_DOUBLE_QUOTED -> nextQuotedValue(DOUBLE_QUOTE_OR_SLASH).also { peekedString = it }
      PEEKED_SINGLE_QUOTED -> nextQuotedValue(SINGLE_QUOTE_OR_SLASH).also { peekedString = it }
      PEEKED_UNQUOTED -> nextUnquotedValue().also { peekedString = it }
      PEEKED_BUFFERED -> {
        // PEEKED_BUFFERED means the value's been stored in peekedString
        knownNotNull(peekedString)
      }
      else -> throw JsonDataException("Expected a double but was " + peek() + " at path " + path)
    }
    peeked = PEEKED_BUFFERED
    val result = try {
      next.toDouble()
    } catch (e: NumberFormatException) {
      throw JsonDataException("Expected a double but was $next at path $path")
    }
    if (!lenient && (result.isNaN() || result.isInfinite())) {
      throw JsonEncodingException("JSON forbids NaN and infinities: $result at path $path")
    }
    peekedString = null
    peeked = PEEKED_NONE
    pathIndices[stackSize - 1]++
    return result
  }

  override fun nextLong(): Long {
    val p = peekIfNone()
    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE
      pathIndices[stackSize - 1]++
      return peekedLong
    }
    when {
      p == PEEKED_NUMBER -> peekedString = buffer.readUtf8(peekedNumberLength.toLong())
      p == PEEKED_DOUBLE_QUOTED || p == PEEKED_SINGLE_QUOTED -> {
        peekedString = if (p == PEEKED_DOUBLE_QUOTED) nextQuotedValue(DOUBLE_QUOTE_OR_SLASH) else nextQuotedValue(SINGLE_QUOTE_OR_SLASH)
        try {
          val result = peekedString!!.toLong()
          peeked = PEEKED_NONE
          pathIndices[stackSize - 1]++
          return result
        } catch (ignored: NumberFormatException) {
          // Fall back to parse as a BigDecimal below.
        }
      }
      p != PEEKED_BUFFERED -> {
        throw JsonDataException("Expected a long but was " + peek() + " at path " + path)
      }
    }
    peeked = PEEKED_BUFFERED
    val result = try {
      val asDecimal = BigDecimal(peekedString)
      asDecimal.longValueExact()
    } catch (e: NumberFormatException) {
      throw JsonDataException("Expected a long but was $peekedString at path $path")
    } catch (e: ArithmeticException) {
      throw JsonDataException("Expected a long but was $peekedString at path $path")
    }
    peekedString = null
    peeked = PEEKED_NONE
    pathIndices[stackSize - 1]++
    return result
  }

  /**
   * Returns the string up to but not including `quote`, unescaping any character escape
   * sequences encountered along the way. The opening quote should have already been read. This
   * consumes the closing quote, but does not include it in the returned string.
   *
   * @throws IOException if any unicode escape sequences are malformed.
   */
  private fun nextQuotedValue(runTerminator: ByteString): String {
    var builder: StringBuilder? = null
    while (true) {
      val index = source.indexOfElement(runTerminator)
      if (index == -1L) throw syntaxError("Unterminated string")

      // If we've got an escape character, we're going to need a string builder.
      if (buffer[index].asChar() == '\\') {
        if (builder == null) builder = StringBuilder()
        builder.append(buffer.readUtf8(index))
        buffer.readByte() // '\'
        builder.append(readEscapeCharacter())
        continue
      }

      // If it isn't the escape character, it's the quote. Return the string.
      return if (builder == null) {
        buffer.readUtf8(index).also {
          buffer.readByte() // Consume the quote character.
        }
      } else {
        builder.append(buffer.readUtf8(index))
        buffer.readByte() // Consume the quote character.
        builder.toString()
      }
    }
  }

  /** Returns an unquoted value as a string.  */
  private fun nextUnquotedValue(): String {
    val i = source.indexOfElement(UNQUOTED_STRING_TERMINALS)
    return if (i != -1L) buffer.readUtf8(i) else buffer.readUtf8()
  }

  private fun skipQuotedValue(runTerminator: ByteString) {
    while (true) {
      val index = source.indexOfElement(runTerminator)
      if (index == -1L) throw syntaxError("Unterminated string")
      val terminator = buffer[index].asChar()
      buffer.skip(index + 1)
      if (terminator == '\\') {
        readEscapeCharacter()
      } else {
        return
      }
    }
  }

  private fun skipUnquotedValue() {
    val i = source.indexOfElement(UNQUOTED_STRING_TERMINALS)
    buffer.skip(if (i != -1L) i else buffer.size)
  }

  override fun nextInt(): Int {
    val p = peekIfNone()
    if (p == PEEKED_LONG) {
      val result = peekedLong.toInt()
      if (peekedLong != result.toLong()) { // Make sure no precision was lost casting to 'int'.
        throw JsonDataException("Expected an int but was $peekedLong at path $path")
      }
      peeked = PEEKED_NONE
      pathIndices[stackSize - 1]++
      return result
    }
    val next: String = when (p) {
      PEEKED_NUMBER -> {
        buffer.readUtf8(peekedNumberLength.toLong()).also { peekedString = it }
      }
      PEEKED_DOUBLE_QUOTED, PEEKED_SINGLE_QUOTED -> {
        val next = if (p == PEEKED_DOUBLE_QUOTED) {
          nextQuotedValue(DOUBLE_QUOTE_OR_SLASH)
        } else {
          nextQuotedValue(SINGLE_QUOTE_OR_SLASH)
        }
        peekedString = next
        try {
          val result = next.toInt()
          peeked = PEEKED_NONE
          pathIndices[stackSize - 1]++
          return result
        } catch (ignored: NumberFormatException) {
          // Fall back to parse as a double below.
          next
        }
      }
      PEEKED_BUFFERED -> {
        // PEEKED_BUFFERED means the value's been stored in peekedString
        knownNotNull(peekedString)
      }
      else -> throw JsonDataException("Expected an int but was ${peek()} at path $path")
    }
    peeked = PEEKED_BUFFERED
    val asDouble = try {
      next.toDouble()
    } catch (e: NumberFormatException) {
      throw JsonDataException("Expected an int but was $next at path $path")
    }
    val result = asDouble.toInt()
    if (result.toDouble() != asDouble) { // Make sure no precision was lost casting to 'int'.
      throw JsonDataException("Expected an int but was $next at path $path")
    }
    peekedString = null
    peeked = PEEKED_NONE
    pathIndices[stackSize - 1]++
    return result
  }

  override fun close() {
    peeked = PEEKED_NONE
    scopes[0] = JsonScope.CLOSED
    stackSize = 1
    buffer.clear()
    source.close()
  }

  override fun skipValue() {
    if (failOnUnknown) {
      throw JsonDataException("Cannot skip unexpected ${peek()} at $path")
    }
    var count = 0
    do {
      when (peekIfNone()) {
        PEEKED_BEGIN_ARRAY -> {
          pushScope(JsonScope.EMPTY_ARRAY)
          count++
        }
        PEEKED_BEGIN_OBJECT -> {
          pushScope(JsonScope.EMPTY_OBJECT)
          count++
        }
        PEEKED_END_ARRAY -> {
          count--
          if (count < 0) {
            throw JsonDataException("Expected a value but was ${peek()} at path $path")
          }
          stackSize--
        }
        PEEKED_END_OBJECT -> {
          count--
          if (count < 0) {
            throw JsonDataException("Expected a value but was ${peek()} at path $path")
          }
          stackSize--
        }
        PEEKED_UNQUOTED_NAME, PEEKED_UNQUOTED -> skipUnquotedValue()
        PEEKED_DOUBLE_QUOTED, PEEKED_DOUBLE_QUOTED_NAME -> skipQuotedValue(DOUBLE_QUOTE_OR_SLASH)
        PEEKED_SINGLE_QUOTED, PEEKED_SINGLE_QUOTED_NAME -> skipQuotedValue(SINGLE_QUOTE_OR_SLASH)
        PEEKED_NUMBER -> buffer.skip(peekedNumberLength.toLong())
        PEEKED_EOF -> throw JsonDataException("Expected a value but was ${peek()} at path $path")
      }
      peeked = PEEKED_NONE
    } while (count != 0)
    pathIndices[stackSize - 1]++
    pathNames[stackSize - 1] = "null"
  }

  override fun nextSource(): BufferedSource {
    val p = peekIfNone()
    var valueSourceStackSize = 0
    val prefix = Buffer()
    var state = JsonValueSource.STATE_END_OF_JSON
    when (p) {
      PEEKED_BEGIN_ARRAY -> {
        prefix.writeUtf8("[")
        state = JsonValueSource.STATE_JSON
        valueSourceStackSize++
      }
      PEEKED_BEGIN_OBJECT -> {
        prefix.writeUtf8("{")
        state = JsonValueSource.STATE_JSON
        valueSourceStackSize++
      }
      PEEKED_DOUBLE_QUOTED -> {
        prefix.writeUtf8("\"")
        state = JsonValueSource.STATE_DOUBLE_QUOTED
      }
      PEEKED_SINGLE_QUOTED -> {
        prefix.writeUtf8("'")
        state = JsonValueSource.STATE_SINGLE_QUOTED
      }
      PEEKED_NUMBER, PEEKED_LONG, PEEKED_UNQUOTED -> prefix.writeUtf8(nextString())
      PEEKED_TRUE -> prefix.writeUtf8("true")
      PEEKED_FALSE -> prefix.writeUtf8("false")
      PEEKED_NULL -> prefix.writeUtf8("null")
      PEEKED_BUFFERED -> {
        val string = nextString()
        JsonWriter.of(prefix).use { jsonWriter ->
          jsonWriter.value(string)
        }
      }
      else -> throw JsonDataException("Expected a value but was ${peek()} at path $path")
    }

    // Advance the path and clear peeked if we haven't already.
    if (peeked != PEEKED_NONE) {
      pathIndices[stackSize - 1]++
      peeked = PEEKED_NONE
    }
    val nextSource = JsonValueSource(source, prefix, state, valueSourceStackSize)
    valueSource = nextSource
    pushScope(JsonScope.STREAMING_VALUE)
    return nextSource.buffer()
  }

  /**
   * Returns the next character in the stream that is neither whitespace nor a part of a comment.
   * When this returns, the returned character is always at `buffer.getByte(0)`.
   */
  private fun nextNonWhitespace(throwOnEof: Boolean): Int {
    /*
     * This code uses ugly local variable 'p' to represent the 'pos' field.
     * Using locals rather than fields saves a few field reads for each
     * whitespace character in a pretty-printed document, resulting in a
     * 5% speedup. We need to flush 'p' to its field before any
     * (potentially indirect) call to fillBuffer() and reread 'p' after
     * any (potentially indirect) call to the same method.
     */
    var p = 0L
    while (source.request(p + 1)) {
      val c = buffer[p++].asChar()
      when (c) {
        '\n', ' ', '\r', '\t' -> continue
      }
      buffer.skip(p - 1)
      when (c) {
        '/' -> {
          if (!source.request(2)) {
            return c.code
          }
          checkLenient()
          val peek = buffer[1]
          return when (peek.asChar()) {
            '*' -> {
              // skip a /* c-style comment */
              buffer.readByte() // '/'
              buffer.readByte() // '*'
              if (!skipToEndOfBlockComment()) {
                throw syntaxError("Unterminated comment")
              }
              p = 0
              continue
            }
            '/' -> {
              // skip a // end-of-line comment
              buffer.readByte() // '/'
              buffer.readByte() // '/'
              skipToEndOfLine()
              p = 0
              continue
            }
            else -> c.code
          }
        }
        '#' -> {
          // Skip a # hash end-of-line comment. The JSON RFC doesn't specify this behaviour, but it's
          // required to parse existing documents.
          checkLenient()
          skipToEndOfLine()
          p = 0
        }
        else -> return c.code
      }
    }
    if (throwOnEof) {
      throw EOFException("End of input")
    }
    return -1
  }

  private fun checkLenient() {
    if (!lenient) {
      throw syntaxError("Use JsonReader.setLenient(true) to accept malformed JSON")
    }
  }

  /**
   * Advances the position until after the next newline character. If the line is terminated by
   * "\r\n", the '\n' must be consumed as whitespace by the caller.
   */
  private fun skipToEndOfLine() {
    val index = source.indexOfElement(LINEFEED_OR_CARRIAGE_RETURN)
    buffer.skip(if (index != -1L) index + 1 else buffer.size)
  }

  /** Skips through the next closing block comment. */
  private fun skipToEndOfBlockComment(): Boolean {
    val index = source.indexOf(CLOSING_BLOCK_COMMENT)
    val found = index != -1L
    buffer.skip(if (found) index + CLOSING_BLOCK_COMMENT.size else buffer.size)
    return found
  }

  override fun peekJson(): JsonReader = JsonUtf8Reader(this)

  override fun toString(): String = "JsonReader($source)"

  /**
   * Unescapes the character identified by the character or characters that immediately follow a
   * backslash. The backslash '\' should have already been read. This supports both unicode escapes
   * "u000A" and two-character escapes "\n".
   *
   * @throws IOException if any unicode escape sequences are malformed.
   */
  private fun readEscapeCharacter(): Char {
    if (!source.request(1)) {
      throw syntaxError("Unterminated escape sequence")
    }
    return when (val escaped = buffer.readByte().asChar()) {
      'u' -> {
        if (!source.request(4)) {
          throw EOFException("Unterminated escape sequence at path $path")
        }
        // Equivalent to Integer.parseInt(stringPool.get(buffer, pos, 4), 16);
        var result = 0.toChar()
        for (i in 0 until 4) {
          result = (result.code shl 4).toChar()
          result += when (val c = buffer[i.toLong()].asChar()) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw syntaxError("\\u" + buffer.readUtf8(4))
          }
        }
        buffer.skip(4)
        result
      }
      't' -> '\t'
      'b' -> '\b'
      'n' -> '\n'
      'r' -> '\r'
      'f' -> '\u000C' /*\f*/
      '\n', '\'', '"', '\\', '/' -> escaped
      else -> {
        if (!lenient) throw syntaxError("Invalid escape sequence: \\$escaped")
        escaped
      }
    }
  }

  override fun promoteNameToValue() {
    if (hasNext()) {
      peekedString = nextName()
      peeked = PEEKED_BUFFERED
    }
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline fun peekIfNone(): Int {
    val p = peeked
    return if (p == PEEKED_NONE) doPeek() else p
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline fun setPeeked(peekedType: Int): Int {
    peeked = peekedType
    return peekedType
  }

  companion object {
    private const val MIN_INCOMPLETE_INTEGER = Long.MIN_VALUE / 10
    private val SINGLE_QUOTE_OR_SLASH = "'\\".encodeUtf8()
    private val DOUBLE_QUOTE_OR_SLASH = "\"\\".encodeUtf8()
    private val UNQUOTED_STRING_TERMINALS = "{}[]:, \n\t\r\u000C/\\;#=".encodeUtf8()
    private val LINEFEED_OR_CARRIAGE_RETURN = "\n\r".encodeUtf8()
    private val CLOSING_BLOCK_COMMENT = "*/".encodeUtf8()
    private const val PEEKED_NONE = 0
    private const val PEEKED_BEGIN_OBJECT = 1
    private const val PEEKED_END_OBJECT = 2
    private const val PEEKED_BEGIN_ARRAY = 3
    private const val PEEKED_END_ARRAY = 4
    private const val PEEKED_TRUE = 5
    private const val PEEKED_FALSE = 6
    private const val PEEKED_NULL = 7
    private const val PEEKED_SINGLE_QUOTED = 8
    private const val PEEKED_DOUBLE_QUOTED = 9
    private const val PEEKED_UNQUOTED = 10

    /** When this is returned, the string value is stored in peekedString.  */
    private const val PEEKED_BUFFERED = 11
    private const val PEEKED_SINGLE_QUOTED_NAME = 12
    private const val PEEKED_DOUBLE_QUOTED_NAME = 13
    private const val PEEKED_UNQUOTED_NAME = 14
    private const val PEEKED_BUFFERED_NAME = 15

    /** When this is returned, the integer value is stored in peekedLong.  */
    private const val PEEKED_LONG = 16
    private const val PEEKED_NUMBER = 17
    private const val PEEKED_EOF = 18

    /* State machine when parsing numbers */
    private const val NUMBER_CHAR_NONE = 0
    private const val NUMBER_CHAR_SIGN = 1
    private const val NUMBER_CHAR_DIGIT = 2
    private const val NUMBER_CHAR_DECIMAL = 3
    private const val NUMBER_CHAR_FRACTION_DIGIT = 4
    private const val NUMBER_CHAR_EXP_E = 5
    private const val NUMBER_CHAR_EXP_SIGN = 6
    private const val NUMBER_CHAR_EXP_DIGIT = 7
  }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Byte.asChar(): Char = toInt().toChar()

// Sneaky backdoor way of marking a value as non-null to the compiler and skip the null-check intrinsic.
// Safe to use (unstable) contracts since they're gone in the final bytecode
// TODO move this to Util.kt after it's migrated to kotlin
@Suppress("NOTHING_TO_INLINE")
private inline fun <T> markNotNull(value: T?) {
  contract {
    returns() implies (value != null)
  }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun <T> knownNotNull(value: T?): T {
  markNotNull(value)
  return value
}
