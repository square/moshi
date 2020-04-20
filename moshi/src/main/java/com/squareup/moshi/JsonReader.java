package com.squareup.moshi;

import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;

public interface JsonReader extends Closeable {
    void setLenient(boolean lenient);

    @CheckReturnValue
    boolean isLenient();

    void setFailOnUnknown(boolean failOnUnknown);

    @CheckReturnValue
    boolean failOnUnknown();

    /**
     * Consumes the next token from the JSON stream and asserts that it is the beginning of a new
     * array.
     */
    void beginArray() throws IOException;

    /**
     * Consumes the next token from the JSON stream and asserts that it is the
     * end of the current array.
     */
    void endArray() throws IOException;

    /**
     * Consumes the next token from the JSON stream and asserts that it is the beginning of a new
     * object.
     */
    void beginObject() throws IOException;

    /**
     * Consumes the next token from the JSON stream and asserts that it is the end of the current
     * object.
     */
    void endObject() throws IOException;

    /**
     * Returns true if the current array or object has another element.
     */
    @CheckReturnValue
    boolean hasNext() throws IOException;

    /**
     * Returns the type of the next token without consuming it.
     */
    @CheckReturnValue
    Token peek() throws IOException;

    /**
     * Returns the next token, a {@linkplain Token#NAME property name}, and consumes it.
     *
     * @throws JsonDataException if the next token in the stream is not a property name.
     */
    @CheckReturnValue
    String nextName() throws IOException;

    /**
     * If the next token is a {@linkplain Token#NAME property name} that's in {@code options}, this
     * consumes it and returns its index. Otherwise this returns -1 and no name is consumed.
     */
    @CheckReturnValue
    int selectName(Options options) throws IOException;

    /**
     * Skips the next token, consuming it. This method is intended for use when the JSON token stream
     * contains unrecognized or unhandled names.
     *
     * <p>This throws a {@link JsonDataException} if this parser has been configured to {@linkplain
     * #failOnUnknown fail on unknown} names.
     */
    void skipName() throws IOException;

    /**
     * Returns the {@linkplain Token#STRING string} value of the next token, consuming it. If the next
     * token is a number, this method will return its string form.
     *
     * @throws JsonDataException if the next token is not a string or if this reader is closed.
     */
    String nextString() throws IOException;

    /**
     * If the next token is a {@linkplain Token#STRING string} that's in {@code options}, this
     * consumes it and returns its index. Otherwise this returns -1 and no string is consumed.
     */
    @CheckReturnValue
    int selectString(Options options) throws IOException;

    /**
     * Returns the {@linkplain Token#BOOLEAN boolean} value of the next token, consuming it.
     *
     * @throws JsonDataException if the next token is not a boolean or if this reader is closed.
     */
    boolean nextBoolean() throws IOException;

    /**
     * Consumes the next token from the JSON stream and asserts that it is a literal null. Returns
     * null.
     *
     * @throws JsonDataException if the next token is not null or if this reader is closed.
     */
    @Nullable
    <T> T nextNull() throws IOException;

    /**
     * Returns the {@linkplain Token#NUMBER double} value of the next token, consuming it. If the next
     * token is a string, this method will attempt to parse it as a double using {@link
     * Double#parseDouble(String)}.
     *
     * @throws JsonDataException if the next token is not a literal value, or if the next literal
     *     value cannot be parsed as a double, or is non-finite.
     */
    double nextDouble() throws IOException;

    /**
     * Returns the {@linkplain Token#NUMBER long} value of the next token, consuming it. If the next
     * token is a string, this method will attempt to parse it as a long. If the next token's numeric
     * value cannot be exactly represented by a Java {@code long}, this method throws.
     *
     * @throws JsonDataException if the next token is not a literal value, if the next literal value
     *     cannot be parsed as a number, or exactly represented as a long.
     */
    long nextLong() throws IOException;

    /**
     * Returns the {@linkplain Token#NUMBER int} value of the next token, consuming it. If the next
     * token is a string, this method will attempt to parse it as an int. If the next token's numeric
     * value cannot be exactly represented by a Java {@code int}, this method throws.
     *
     * @throws JsonDataException if the next token is not a literal value, if the next literal value
     *     cannot be parsed as a number, or exactly represented as an int.
     */
    int nextInt() throws IOException;

    /**
     * Skips the next value recursively. If it is an object or array, all nested elements are skipped.
     * This method is intended for use when the JSON token stream contains unrecognized or unhandled
     * values.
     *
     * <p>This throws a {@link JsonDataException} if this parser has been configured to {@linkplain
     * #failOnUnknown fail on unknown} values.
     */
    void skipValue() throws IOException;

    /**
     * Changes the reader to treat the next name as a string value. This is useful for map adapters so
     * that arbitrary type adapters can use {@link #nextString} to read a name value.
     */
    void promoteNameToValue() throws IOException;

    /** Returns a new instance that reads UTF-8 encoded JSON from {@code source}. */
    @CheckReturnValue static JsonReader of(BufferedSource source) {
        return new JsonUtf8Reader(source);
    }

    @Nullable Object readJsonValue() throws IOException;

    /**
     * Returns a new {@code JsonReader} that can read data from this {@code JsonReader} without
     * consuming it. The returned reader becomes invalid once this one is next read or closed.
     *
     * <p>For example, we can use {@code peekJson()} to lookahead and read the same data multiple
     * times.
     *
     * <pre> {@code
     *
     *   Buffer buffer = new Buffer();
     *   buffer.writeUtf8("[123, 456, 789]")
     *
     *   JsonReader jsonReader = JsonReader.of(buffer);
     *   jsonReader.beginArray();
     *   jsonReader.nextInt(); // Returns 123, reader contains 456, 789 and ].
     *
     *   JsonReader peek = reader.peekJson();
     *   peek.nextInt() // Returns 456.
     *   peek.nextInt() // Returns 789.
     *   peek.endArray()
     *
     *   jsonReader.nextInt() // Returns 456, reader contains 789 and ].
     * }</pre>
     */
    @CheckReturnValue
    JsonReader peekJson();

    @CheckReturnValue
    String getPath();

    /**
     * A structure, name, or value type in a JSON-encoded string.
     */
    enum Token {

      /**
       * The opening of a JSON array. Written using {@link JsonWriter#beginArray}
       * and read using {@link JsonReader#beginArray}.
       */
      BEGIN_ARRAY,

      /**
       * The closing of a JSON array. Written using {@link JsonWriter#endArray}
       * and read using {@link JsonReader#endArray}.
       */
      END_ARRAY,

      /**
       * The opening of a JSON object. Written using {@link JsonWriter#beginObject}
       * and read using {@link JsonReader#beginObject}.
       */
      BEGIN_OBJECT,

      /**
       * The closing of a JSON object. Written using {@link JsonWriter#endObject}
       * and read using {@link JsonReader#endObject}.
       */
      END_OBJECT,

      /**
       * A JSON property name. Within objects, tokens alternate between names and
       * their values. Written using {@link JsonWriter#name} and read using {@link
       * JsonReader#nextName}
       */
      NAME,

      /**
       * A JSON string.
       */
      STRING,

      /**
       * A JSON number represented in this API by a Java {@code double}, {@code
       * long}, or {@code int}.
       */
      NUMBER,

      /**
       * A JSON {@code true} or {@code false}.
       */
      BOOLEAN,

      /**
       * A JSON {@code null}.
       */
      NULL,

      /**
       * The end of the JSON stream. This sentinel value is returned by {@link
       * JsonReader#peek()} to signal that the JSON-encoded value has no more
       * tokens.
       */
      END_DOCUMENT
    }

    /**
     * A set of strings to be chosen with {@link #selectName} or {@link #selectString}. This prepares
     * the encoded values of the strings so they can be read directly from the input source.
     */
    final class Options {
      final String[] strings;
      final okio.Options doubleQuoteSuffix;

      private Options(String[] strings, okio.Options doubleQuoteSuffix) {
        this.strings = strings;
        this.doubleQuoteSuffix = doubleQuoteSuffix;
      }

      @CheckReturnValue
      public static Options of(String... strings) {
        try {
          ByteString[] result = new ByteString[strings.length];
          Buffer buffer = new Buffer();
          for (int i = 0; i < strings.length; i++) {
            JsonUtf8Writer.string(buffer, strings[i]);
            buffer.readByte(); // Skip the leading double quote (but leave the trailing one).
            result[i] = buffer.readByteString();
          }
          return new Options(strings.clone(), okio.Options.of(result));
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }
}
