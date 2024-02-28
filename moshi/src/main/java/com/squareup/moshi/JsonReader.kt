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

import com.squareup.moshi.JsonScope.getPath
import com.squareup.moshi.JsonUtf8Writer.Companion.string
import okio.Buffer
import okio.BufferedSource
import okio.Closeable
import okio.IOException
import javax.annotation.CheckReturnValue
import okio.Options as OkioOptions

/**
 * Reads a JSON ([RFC 7159](http://www.ietf.org/rfc/rfc7159.txt)) encoded value as a
 * stream of tokens. This stream includes both literal values (strings, numbers, booleans, and
 * nulls) as well as the begin and end delimiters of objects and arrays. The tokens are traversed in
 * depth-first order, the same order that they appear in the JSON document. Within JSON objects,
 * name/value pairs are represented by a single token.
 *
 * ## Parsing JSON
 *
 * To create a recursive descent parser for your own JSON streams, first create an entry point
 * method that creates a `JsonReader`.
 *
 * Next, create handler methods for each structure in your JSON text. You'll need a method for
 * each object type and for each array type.
 *  * Within **array handling** methods, first call [beginArray] to consume
 * the array's opening bracket. Then create a `while` loop that accumulates values, terminating
 * when [hasNext] is false. Finally, read the array's closing bracket by calling [endArray].
 *  * Within **object handling** methods, first call [beginObject] to consume
 * the object's opening brace. Then create a `while` loop that assigns values to local variables
 * based on their name. This loop should terminate when [hasNext] is false. Finally,
 * read the object's closing brace by calling [endObject].
 *
 * When a nested object or array is encountered, delegate to the corresponding handler method.
 *
 * When an unknown name is encountered, strict parsers should fail with an exception. Lenient
 * parsers should call [skipValue] to recursively skip the value's nested tokens, which may
 * otherwise conflict.
 *
 * If a value may be null, you should first check using [peek]. Null literals can be
 * consumed using either [nextNull] or [skipValue].
 *
 * ## Example
 *
 * Suppose we'd like to parse a stream of messages such as the following:
 *
 * ```json
 * [
 *   {
 *     "id": 912345678901,
 *     "text": "How do I read a JSON stream in Java?",
 *     "geo": null,
 *     "user": {
 *       "name": "json_newb",
 *       "followers_count": 41
 *     }
 *   },
 *   {
 *     "id": 912345678902,
 *     "text": "@json_newb just use JsonReader!",
 *     "geo": [50.454722, -104.606667],
 *     "user": {
 *       "name": "jesse",
 *       "followers_count": 2
 *     }
 *   }
 * ]
 * ```
 *
 * This code implements the parser for the above structure:
 *
 * ```java
 * public List<Message> readJsonStream(BufferedSource source) throws IOException {
 *   JsonReader reader = JsonReader.of(source);
 *   try {
 *     return readMessagesArray(reader);
 *   } finally {
 *     reader.close();
 *   }
 * }
 *
 * public List<Message> readMessagesArray(JsonReader reader) throws IOException {
 *   List<Message> messages = new ArrayList<Message>();
 *
 *   reader.beginArray();
 *   while (reader.hasNext()) {
 *     messages.add(readMessage(reader));
 *   }
 *   reader.endArray();
 *   return messages;
 * }
 *
 * public Message readMessage(JsonReader reader) throws IOException {
 *   long id = -1;
 *   String text = null;
 *   User user = null;
 *   List<Double> geo = null;
 *
 *   reader.beginObject();
 *   while (reader.hasNext()) {
 *     String name = reader.nextName();
 *     if (name.equals("id")) {
 *       id = reader.nextLong();
 *     } else if (name.equals("text")) {
 *       text = reader.nextString();
 *     } else if (name.equals("geo") && reader.peek() != Token.NULL) {
 *       geo = readDoublesArray(reader);
 *     } else if (name.equals("user")) {
 *       user = readUser(reader);
 *     } else {
 *       reader.skipValue();
 *     }
 *   }
 *   reader.endObject();
 *   return new Message(id, text, user, geo);
 * }
 *
 * public List<Double> readDoublesArray(JsonReader reader) throws IOException {
 *   List<Double> doubles = new ArrayList<Double>();
 *
 *   reader.beginArray();
 *   while (reader.hasNext()) {
 *     doubles.add(reader.nextDouble());
 *   }
 *   reader.endArray();
 *   return doubles;
 * }
 *
 * public User readUser(JsonReader reader) throws IOException {
 *   String username = null;
 *   int followersCount = -1;
 *
 *   reader.beginObject();
 *   while (reader.hasNext()) {
 *     String name = reader.nextName();
 *     if (name.equals("name")) {
 *       username = reader.nextString();
 *     } else if (name.equals("followers_count")) {
 *       followersCount = reader.nextInt();
 *     } else {
 *       reader.skipValue();
 *     }
 *   }
 *   reader.endObject();
 *   return new User(username, followersCount);
 * }
 * ```
 *
 * ## Number Handling
 *
 * This reader permits numeric values to be read as strings and string values to be read as numbers.
 * For example, both elements of the JSON array `[1, "1"]` may be read using either [nextInt] or [nextString]. This behavior is intended to prevent lossy numeric conversions:
 * double is JavaScript's only numeric type and very large values like `9007199254740993`
 * cannot be represented exactly on that platform. To minimize precision loss, extremely large
 * values should be written and read as strings in JSON.
 *
 * Each `JsonReader` may be used to read a single JSON stream. Instances of this class are
 * not thread safe.
 */
public sealed class JsonReader : Closeable {
  // The nesting stack. Using a manual array rather than an ArrayList saves 20%. This stack will
  // grow itself up to 256 levels of nesting including the top-level document. Deeper nesting is
  // prone to trigger StackOverflowErrors.
  @JvmField
  protected var stackSize: Int = 0

  @JvmField
  protected var scopes: IntArray

  @JvmField
  protected var pathNames: Array<String?>

  @JvmField
  protected var pathIndices: IntArray

  /**
   * Returns true if this parser is liberal in what it accepts.
   *
   * ## Getting
   * True to accept non-spec compliant JSON.
   *
   * ## Setting
   * Configure this parser to be liberal in what it accepts. By default this parser is strict and
   * only accepts JSON as specified by [RFC 7159](http://www.ietf.org/rfc/rfc7159.txt).
   * Setting the parser to lenient causes it to ignore the following syntax errors:
   *  * Streams that include multiple top-level values. With strict parsing, each stream must
   * contain exactly one top-level value.
   *  * Numbers may be [NaNs][Double.isNaN] or [infinities][Double.isInfinite].
   *  * End of line comments starting with `//` or `#` and ending with a newline
   * character.
   *  * C-style comments starting with `/ *` and ending with `*``/`. Such
   * comments may not be nested.
   *  * Names that are unquoted or `'single quoted'`.
   *  * Strings that are unquoted or `'single quoted'`.
   *  * Array elements separated by `;` instead of `,`.
   *  * Unnecessary array separators. These are interpreted as if null was the omitted value.
   *  * Names and values separated by `=` or `=>` instead of `:`.
   *  * Name/value pairs separated by `;` instead of `,`.
   */
  @get:CheckReturnValue
  @get:JvmName("isLenient")
  public var lenient: Boolean = false

  /**
   * True to throw a [JsonDataException] on any attempt to call [skipValue].
   *
   * ## Getting
   * Returns true if this parser forbids skipping names and values.
   *
   * ## Setting
   * Configure whether this parser throws a [JsonDataException] when [skipValue] is
   * called. By default this parser permits values to be skipped.
   *
   * Forbid skipping to prevent unrecognized values from being silently ignored. This option is
   * useful in development and debugging because it means a typo like "locatiom" will be detected
   * early. It's potentially harmful in production because it complicates revising a JSON schema.
   */
  @get:JvmName("failOnUnknown")
  public var failOnUnknown: Boolean = false

  private var tags: MutableMap<Class<*>, Any>? = null

  protected constructor() {
    scopes = IntArray(32)
    pathNames = arrayOfNulls(32)
    pathIndices = IntArray(32)
  }

  protected constructor(copyFrom: JsonReader) {
    stackSize = copyFrom.stackSize
    scopes = copyFrom.scopes.clone()
    pathNames = copyFrom.pathNames.clone()
    pathIndices = copyFrom.pathIndices.clone()
    lenient = copyFrom.lenient
    failOnUnknown = copyFrom.failOnUnknown
  }

  protected fun pushScope(newTop: Int) {
    if (stackSize == scopes.size) {
      if (stackSize == 256) {
        throw JsonDataException("Nesting too deep at $path")
      }
      scopes = scopes.copyOf(scopes.size * 2)
      pathNames = pathNames.copyOf(pathNames.size * 2)
      pathIndices = pathIndices.copyOf(pathIndices.size * 2)
    }
    scopes[stackSize++] = newTop
  }

  /**
   * Throws a new IO exception with the given message and a context snippet with this reader's
   * content.
   */
  @Suppress("NOTHING_TO_INLINE")
  @Throws(JsonEncodingException::class)
  protected inline fun syntaxError(message: String): JsonEncodingException {
    throw JsonEncodingException("$message at path $path")
  }

  protected fun typeMismatch(value: Any?, expected: Any): JsonDataException {
    return if (value == null) {
      JsonDataException("Expected $expected but was null at path $path")
    } else {
      JsonDataException("Expected $expected but was $value, a ${value.javaClass.name}, at path $path")
    }
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the beginning of a new
   * array.
   */
  @Throws(IOException::class)
  public abstract fun beginArray()

  /**
   * Consumes the next token from the JSON stream and asserts that it is the end of the current
   * array.
   */
  @Throws(IOException::class)
  public abstract fun endArray()

  /**
   * Consumes the next token from the JSON stream and asserts that it is the beginning of a new
   * object.
   */
  @Throws(IOException::class)
  public abstract fun beginObject()

  /**
   * Consumes the next token from the JSON stream and asserts that it is the end of the current
   * object.
   */
  @Throws(IOException::class)
  public abstract fun endObject()

  /** Returns true if the current array or object has another element. */
  @CheckReturnValue
  @Throws(IOException::class)
  public abstract operator fun hasNext(): Boolean

  /** Returns the type of the next token without consuming it. */
  @CheckReturnValue
  @Throws(IOException::class)
  public abstract fun peek(): Token

  /**
   * Returns the next token, a [property name][Token.NAME], and consumes it.
   *
   * @throws JsonDataException if the next token in the stream is not a property name.
   */
  @CheckReturnValue
  @Throws(IOException::class)
  public abstract fun nextName(): String

  /**
   * If the next token is a [property name][Token.NAME] that's in [options], this
   * consumes it and returns its index. Otherwise, this returns -1 and no name is consumed.
   */
  @CheckReturnValue
  @Throws(IOException::class)
  public abstract fun selectName(options: Options): Int

  /**
   * Skips the next token, consuming it. This method is intended for use when the JSON token stream
   * contains unrecognized or unhandled names.
   *
   * This throws a [JsonDataException] if this parser has been configured to [failOnUnknown] names.
   */
  @Throws(IOException::class)
  public abstract fun skipName()

  /**
   * Returns the [string][Token.STRING] value of the next token, consuming it. If the next
   * token is a number, this method will return its string form.
   *
   * @throws JsonDataException if the next token is not a string or if this reader is closed.
   */
  @Throws(IOException::class)
  public abstract fun nextString(): String

  /**
   * If the next token is a [string][Token.STRING] that's in [options], this
   * consumes it and returns its index. Otherwise, this returns -1 and no string is consumed.
   */
  @CheckReturnValue
  @Throws(IOException::class)
  public abstract fun selectString(options: Options): Int

  /**
   * Returns the [boolean][Token.BOOLEAN] value of the next token, consuming it.
   *
   * @throws JsonDataException if the next token is not a boolean or if this reader is closed.
   */
  @Throws(IOException::class)
  public abstract fun nextBoolean(): Boolean

  /**
   * Consumes the next token from the JSON stream and asserts that it is a literal null. Returns
   * null.
   *
   * @throws JsonDataException if the next token is not null or if this reader is closed.
   */
  @Throws(IOException::class)
  public abstract fun <T> nextNull(): T?

  /**
   * Returns the [double][Token.NUMBER] value of the next token, consuming it. If the next
   * token is a string, this method will attempt to parse it as a double using [java.lang.Double.parseDouble].
   *
   * @throws JsonDataException if the next token is not a literal value, or if the next literal
   * value cannot be parsed as a double, or is non-finite.
   */
  @Throws(IOException::class)
  public abstract fun nextDouble(): Double

  /**
   * Returns the [long][Token.NUMBER] value of the next token, consuming it. If the next
   * token is a string, this method will attempt to parse it as a long. If the next token's numeric
   * value cannot be exactly represented by a Java `long`, this method throws.
   *
   * @throws JsonDataException if the next token is not a literal value, if the next literal value
   * cannot be parsed as a number, or exactly represented as a long.
   */
  @Throws(IOException::class)
  public abstract fun nextLong(): Long

  /**
   * Returns the [int][Token.NUMBER] value of the next token, consuming it. If the next
   * token is a string, this method will attempt to parse it as an int. If the next token's numeric
   * value cannot be exactly represented by a Java `int`, this method throws.
   *
   * @throws JsonDataException if the next token is not a literal value, if the next literal value
   * cannot be parsed as a number, or exactly represented as an int.
   */
  @Throws(IOException::class)
  public abstract fun nextInt(): Int

  /**
   * Returns the next value as a stream of UTF-8 bytes and consumes it.
   *
   * The following program demonstrates how JSON bytes are returned from an enclosing stream as
   * their original bytes, including their original whitespace:
   *
   * ```java
   * String json = "{\"a\": [4,  5  ,6.0, {\"x\":7}, 8], \"b\": 9}";
   * JsonReader reader = JsonReader.of(new Buffer().writeUtf8(json));
   * reader.beginObject();
   * assertThat(reader.nextName()).isEqualTo("a");
   * try (BufferedSource bufferedSource = reader.nextSource()) {
   *   assertThat(bufferedSource.readUtf8()).isEqualTo("[4,  5  ,6.0, {\"x\":7}, 8]");
   * }
   * assertThat(reader.nextName()).isEqualTo("b");
   * assertThat(reader.nextInt()).isEqualTo(9);
   * reader.endObject();
   * ```
   *
   * This reads an entire value: composite objects like arrays and objects are returned in their
   * entirety. The stream starts with the first character of the value (typically `[`, `{` * , or `"`)
   * and ends with the last character of the object (typically `]`, `}`, or `"`).
   *
   * The returned source may not be used after any other method on this `JsonReader` is
   * called. For example, the following code crashes with an exception:
   *
   * ```
   * JsonReader reader = ...
   * reader.beginArray();
   * BufferedSource source = reader.nextSource();
   * reader.endArray();
   * source.readUtf8(); // Crash!
   * ```
   *
   * The returned bytes are not validated. This method assumes the stream is well-formed JSON and
   * only attempts to find the value's boundary in the byte stream. It is the caller's
   * responsibility to check that the returned byte stream is a valid JSON value.
   *
   * Closing the returned source **does not** close this reader.
   */
  @Throws(IOException::class)
  public abstract fun nextSource(): BufferedSource

  /**
   * Skips the next value recursively. If it is an object or array, all nested elements are skipped.
   * This method is intended for use when the JSON token stream contains unrecognized or unhandled
   * values.
   *
   * This throws a [JsonDataException] if this parser has been configured to [failOnUnknown] values.
   */
  @Throws(IOException::class)
  public abstract fun skipValue()

  /**
   * Returns the value of the next token, consuming it. The result may be a string, number, boolean,
   * null, map, or list, according to the JSON structure.
   *
   * @throws JsonDataException if the next token is not a literal value, if a JSON object has a
   * duplicate key.
   * @see JsonWriter.jsonValue
   */
  @Throws(IOException::class)
  public fun readJsonValue(): Any? {
    return when (peek()) {
      Token.BEGIN_ARRAY -> {
        return buildList {
          beginArray()
          while (hasNext()) {
            add(readJsonValue())
          }
          endArray()
        }
      }

      Token.BEGIN_OBJECT -> {
        return buildMap {
          beginObject()
          while (hasNext()) {
            val name = nextName()
            val value = readJsonValue()
            val replaced = put(name, value)
            if (replaced != null) {
              throw JsonDataException("Map key '$name' has multiple values at path $path: $replaced and $value")
            }
          }
          endObject()
        }
      }

      Token.STRING -> nextString()

      Token.NUMBER -> nextDouble()

      Token.BOOLEAN -> nextBoolean()

      Token.NULL -> nextNull<Any>()

      else -> throw IllegalStateException("Expected a value but was ${peek()} at path $path")
    }
  }

  /**
   * Returns a new `JsonReader` that can read data from this `JsonReader` without
   * consuming it. The returned reader becomes invalid once this one is next read or closed.
   *
   * For example, we can use `peekJson()` to lookahead and read the same data multiple
   * times.
   *
   * ```java
   * Buffer buffer = new Buffer();
   * buffer.writeUtf8("[123, 456, 789]")
   *
   * JsonReader jsonReader = JsonReader.of(buffer);
   * jsonReader.beginArray();
   * jsonReader.nextInt(); // Returns 123, reader contains 456, 789 and ].
   *
   * JsonReader peek = reader.peekJson();
   * peek.nextInt() // Returns 456.
   * peek.nextInt() // Returns 789.
   * peek.endArray()
   *
   * jsonReader.nextInt() // Returns 456, reader contains 789 and ].
   * ```
   */
  @CheckReturnValue
  public abstract fun peekJson(): JsonReader

  /**
   * Returns a [JsonPath](http://goessner.net/articles/JsonPath/) to the current location
   * in the JSON value.
   */
  @get:CheckReturnValue
  public val path: String
    get() = getPath(stackSize, scopes, pathNames, pathIndices)

  /** Returns the tag value for the given class key. */
  @CheckReturnValue
  public fun <T : Any> tag(clazz: Class<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return tags?.let { it[clazz] as T? }
  }

  /** Assigns the tag value using the given class key and value. */
  public fun <T : Any> setTag(clazz: Class<T>, value: T) {
    require(clazz.isAssignableFrom(value.javaClass)) { "Tag value must be of type ${clazz.name}" }
    val tagsToUse = tags ?: LinkedHashMap<Class<*>, Any>().also { tags = it }
    tagsToUse[clazz] = value
  }

  /**
   * Changes the reader to treat the next name as a string value. This is useful for map adapters so
   * that arbitrary type adapters can use [nextString] to read a name value.
   *
   * In this example, calling this method allows two sequential calls to [nextString]:
   *
   * ```java
   * JsonReader reader = JsonReader.of(new Buffer().writeUtf8("{\"a\":\"b\"}"));
   * reader.beginObject();
   * reader.promoteNameToValue();
   * assertEquals("a", reader.nextString());
   * assertEquals("b", reader.nextString());
   * reader.endObject();
   * ```
   */
  @Throws(IOException::class)
  public abstract fun promoteNameToValue()

  /**
   * A set of strings to be chosen with [selectName] or [selectString]. This prepares
   * the encoded values of the strings so they can be read directly from the input source.
   */
  public class Options private constructor(
    internal val strings: Array<out String>,
    internal val doubleQuoteSuffix: OkioOptions,
  ) {
    /** Returns a copy of this [Option's][Options] strings. */
    public fun strings(): List<String> {
      return buildList(strings.size) {
        for (string in strings) {
          add(string)
        }
      }
    }

    public companion object {
      @CheckReturnValue
      @JvmStatic
      public fun of(vararg strings: String): Options {
        return try {
          val buffer = Buffer()
          val result = Array(strings.size) { i ->
            buffer.string(strings[i])
            buffer.readByte() // Skip the leading double quote (but leave the trailing one).
            buffer.readByteString()
          }
          Options(strings.clone(), OkioOptions.of(*result))
        } catch (e: IOException) {
          throw AssertionError(e)
        }
      }
    }
  }

  /** A structure, name, or value type in a JSON-encoded string. */
  public enum class Token {
    /**
     * The opening of a JSON array. Written using [JsonWriter.beginArray] and read using
     * [JsonReader.beginArray].
     */
    BEGIN_ARRAY,

    /**
     * The closing of a JSON array. Written using [JsonWriter.endArray] and read using [JsonReader.endArray].
     */
    END_ARRAY,

    /**
     * The opening of a JSON object. Written using [JsonWriter.beginObject] and read using
     * [JsonReader.beginObject].
     */
    BEGIN_OBJECT,

    /**
     * The closing of a JSON object. Written using [JsonWriter.endObject] and read using
     * [JsonReader.endObject].
     */
    END_OBJECT,

    /**
     * A JSON property name. Within objects, tokens alternate between names and their values.
     * Written using [JsonWriter.name] and read using [JsonReader.nextName]
     */
    NAME,

    /** A JSON string. */
    STRING,

    /**
     * A JSON number represented in this API by a Java `double`, `long`, or `int`.
     */
    NUMBER,

    /** A JSON `true` or `false`. */
    BOOLEAN,

    /** A JSON `null`. */
    NULL,

    /**
     * The end of the JSON stream. This sentinel value is returned by [JsonReader.peek] to
     * signal that the JSON-encoded value has no more tokens.
     */
    END_DOCUMENT,
  }

  public companion object {
    /** Returns a new instance that reads UTF-8 encoded JSON from `source`. */
    @CheckReturnValue
    @JvmStatic
    public fun of(source: BufferedSource): JsonReader {
      return JsonUtf8Reader(source)
    }
  }
}
