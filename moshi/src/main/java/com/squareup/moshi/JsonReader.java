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
package com.squareup.moshi;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

/**
 * Reads a JSON (<a href="http://www.ietf.org/rfc/rfc4627.txt">RFC 4627</a>)
 * encoded value as a stream of tokens. This stream includes both literal
 * values (strings, numbers, booleans, and nulls) as well as the begin and
 * end delimiters of objects and arrays. The tokens are traversed in
 * depth-first order, the same order that they appear in the JSON document.
 * Within JSON objects, name/value pairs are represented by a single token.
 *
 * <h3>Parsing JSON</h3>
 * To create a recursive descent parser for your own JSON streams, first create
 * an entry point method that creates a {@code JsonReader}.
 *
 * <p>Next, create handler methods for each structure in your JSON text. You'll
 * need a method for each object type and for each array type.
 * <ul>
 *   <li>Within <strong>array handling</strong> methods, first call {@link
 *       #beginArray} to consume the array's opening bracket. Then create a
 *       while loop that accumulates values, terminating when {@link #hasNext}
 *       is false. Finally, read the array's closing bracket by calling {@link
 *       #endArray}.
 *   <li>Within <strong>object handling</strong> methods, first call {@link
 *       #beginObject} to consume the object's opening brace. Then create a
 *       while loop that assigns values to local variables based on their name.
 *       This loop should terminate when {@link #hasNext} is false. Finally,
 *       read the object's closing brace by calling {@link #endObject}.
 * </ul>
 * <p>When a nested object or array is encountered, delegate to the
 * corresponding handler method.
 *
 * <p>When an unknown name is encountered, strict parsers should fail with an
 * exception. Lenient parsers should call {@link #skipValue()} to recursively
 * skip the value's nested tokens, which may otherwise conflict.
 *
 * <p>If a value may be null, you should first check using {@link #peek()}.
 * Null literals can be consumed using either {@link #nextNull()} or {@link
 * #skipValue()}.
 *
 * <h3>Example</h3>
 * Suppose we'd like to parse a stream of messages such as the following: <pre> {@code
 * [
 *   {
 *     "id": 912345678901,
 *     "text": "How do I read a JSON stream in Java?",
 *     "geo": null,
 *     "user": {
 *       "name": "json_newb",
 *       "followers_count": 41
 *      }
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
 * ]}</pre>
 * This code implements the parser for the above structure: <pre>   {@code
 *
 *   public List<Message> readJsonStream(Source source) throws IOException {
 *     JsonReader reader = new JsonReader(source);
 *     try {
 *       return readMessagesArray(reader);
 *     } finally {
 *       reader.close();
 *     }
 *   }
 *
 *   public List<Message> readMessagesArray(JsonReader reader) throws IOException {
 *     List<Message> messages = new ArrayList<Message>();
 *
 *     reader.beginArray();
 *     while (reader.hasNext()) {
 *       messages.add(readMessage(reader));
 *     }
 *     reader.endArray();
 *     return messages;
 *   }
 *
 *   public Message readMessage(JsonReader reader) throws IOException {
 *     long id = -1;
 *     String text = null;
 *     User user = null;
 *     List<Double> geo = null;
 *
 *     reader.beginObject();
 *     while (reader.hasNext()) {
 *       String name = reader.nextName();
 *       if (name.equals("id")) {
 *         id = reader.nextLong();
 *       } else if (name.equals("text")) {
 *         text = reader.nextString();
 *       } else if (name.equals("geo") && reader.peek() != JsonToken.NULL) {
 *         geo = readDoublesArray(reader);
 *       } else if (name.equals("user")) {
 *         user = readUser(reader);
 *       } else {
 *         reader.skipValue();
 *       }
 *     }
 *     reader.endObject();
 *     return new Message(id, text, user, geo);
 *   }
 *
 *   public List<Double> readDoublesArray(JsonReader reader) throws IOException {
 *     List<Double> doubles = new ArrayList<Double>();
 *
 *     reader.beginArray();
 *     while (reader.hasNext()) {
 *       doubles.add(reader.nextDouble());
 *     }
 *     reader.endArray();
 *     return doubles;
 *   }
 *
 *   public User readUser(JsonReader reader) throws IOException {
 *     String username = null;
 *     int followersCount = -1;
 *
 *     reader.beginObject();
 *     while (reader.hasNext()) {
 *       String name = reader.nextName();
 *       if (name.equals("name")) {
 *         username = reader.nextString();
 *       } else if (name.equals("followers_count")) {
 *         followersCount = reader.nextInt();
 *       } else {
 *         reader.skipValue();
 *       }
 *     }
 *     reader.endObject();
 *     return new User(username, followersCount);
 *   }}</pre>
 *
 * <h3>Number Handling</h3>
 * This reader permits numeric values to be read as strings and string values to
 * be read as numbers. For example, both elements of the JSON array {@code
 * [1, "1"]} may be read using either {@link #nextInt} or {@link #nextString}.
 * This behavior is intended to prevent lossy numeric conversions: double is
 * JavaScript's only numeric type and very large values like {@code
 * 9007199254740993} cannot be represented exactly on that platform. To minimize
 * precision loss, extremely large values should be written and read as strings
 * in JSON.
 *
 * <p>Each {@code JsonReader} may be used to read a single JSON stream. Instances
 * of this class are not thread safe.
 */
public final class JsonReader implements Closeable {
  private static final long MIN_INCOMPLETE_INTEGER = Long.MIN_VALUE / 10;

  private static final ByteString SINGLE_QUOTE_OR_SLASH = ByteString.encodeUtf8("'\\");
  private static final ByteString DOUBLE_QUOTE_OR_SLASH = ByteString.encodeUtf8("\"\\");
  private static final ByteString UNQUOTED_STRING_TERMINALS
      = ByteString.encodeUtf8("{}[]:, \n\t\r\f/\\;#=");
  private static final ByteString LINEFEED_OR_CARRIAGE_RETURN = ByteString.encodeUtf8("\n\r");

  private static final int PEEKED_NONE = 0;
  private static final int PEEKED_BEGIN_OBJECT = 1;
  private static final int PEEKED_END_OBJECT = 2;
  private static final int PEEKED_BEGIN_ARRAY = 3;
  private static final int PEEKED_END_ARRAY = 4;
  private static final int PEEKED_TRUE = 5;
  private static final int PEEKED_FALSE = 6;
  private static final int PEEKED_NULL = 7;
  private static final int PEEKED_SINGLE_QUOTED = 8;
  private static final int PEEKED_DOUBLE_QUOTED = 9;
  private static final int PEEKED_UNQUOTED = 10;
  /** When this is returned, the string value is stored in peekedString. */
  private static final int PEEKED_BUFFERED = 11;
  private static final int PEEKED_SINGLE_QUOTED_NAME = 12;
  private static final int PEEKED_DOUBLE_QUOTED_NAME = 13;
  private static final int PEEKED_UNQUOTED_NAME = 14;
  /** When this is returned, the integer value is stored in peekedLong. */
  private static final int PEEKED_LONG = 15;
  private static final int PEEKED_NUMBER = 16;
  private static final int PEEKED_EOF = 17;

  /* State machine when parsing numbers */
  private static final int NUMBER_CHAR_NONE = 0;
  private static final int NUMBER_CHAR_SIGN = 1;
  private static final int NUMBER_CHAR_DIGIT = 2;
  private static final int NUMBER_CHAR_DECIMAL = 3;
  private static final int NUMBER_CHAR_FRACTION_DIGIT = 4;
  private static final int NUMBER_CHAR_EXP_E = 5;
  private static final int NUMBER_CHAR_EXP_SIGN = 6;
  private static final int NUMBER_CHAR_EXP_DIGIT = 7;

  /** True to accept non-spec compliant JSON */
  private boolean lenient = false;

  /** The input JSON. */
  private final BufferedSource source;
  private final Buffer buffer;

  private int peeked = PEEKED_NONE;

  /**
   * A peeked value that was composed entirely of digits with an optional
   * leading dash. Positive values may not have a leading 0.
   */
  private long peekedLong;

  /**
   * The number of characters in a peeked number literal. Increment 'pos' by
   * this after reading a number.
   */
  private int peekedNumberLength;

  /**
   * A peeked string that should be parsed on the next double, long or string.
   * This is populated before a numeric value is parsed and used if that parsing
   * fails.
   */
  private String peekedString;

  /*
   * The nesting stack. Using a manual array rather than an ArrayList saves 20%.
   */
  private int[] stack = new int[32];
  private int stackSize = 0;
  {
    stack[stackSize++] = JsonScope.EMPTY_DOCUMENT;
  }

  /*
   * The path members. It corresponds directly to stack: At indices where the
   * stack contains an object (EMPTY_OBJECT, DANGLING_NAME or NONEMPTY_OBJECT),
   * pathNames contains the name at this scope. Where it contains an array
   * (EMPTY_ARRAY, NONEMPTY_ARRAY) pathIndices contains the current index in
   * that array. Otherwise the value is undefined, and we take advantage of that
   * by incrementing pathIndices when doing so isn't useful.
   */
  private String[] pathNames = new String[32];
  private int[] pathIndices = new int[32];

  /**
   * Creates a new instance that reads a JSON-encoded stream from {@code source}.
   */
  public JsonReader(BufferedSource source) {
    if (source == null) {
      throw new NullPointerException("source == null");
    }
    this.source = source;
    this.buffer = source.buffer();
  }

  /**
   * Configure this parser to be  be liberal in what it accepts. By default,
   * this parser is strict and only accepts JSON as specified by <a
   * href="http://www.ietf.org/rfc/rfc4627.txt">RFC 4627</a>. Setting the
   * parser to lenient causes it to ignore the following syntax errors:
   *
   * <ul>
   *   <li>Streams that start with the <a href="#nonexecuteprefix">non-execute
   *       prefix</a>, <code>")]}'\n"</code>.
   *   <li>Streams that include multiple top-level values. With strict parsing,
   *       each stream must contain exactly one top-level value.
   *   <li>Top-level values of any type. With strict parsing, the top-level
   *       value must be an object or an array.
   *   <li>Numbers may be {@link Double#isNaN() NaNs} or {@link
   *       Double#isInfinite() infinities}.
   *   <li>End of line comments starting with {@code //} or {@code #} and
   *       ending with a newline character.
   *   <li>C-style comments starting with {@code /*} and ending with
   *       {@code *}{@code /}. Such comments may not be nested.
   *   <li>Names that are unquoted or {@code 'single quoted'}.
   *   <li>Strings that are unquoted or {@code 'single quoted'}.
   *   <li>Array elements separated by {@code ;} instead of {@code ,}.
   *   <li>Unnecessary array separators. These are interpreted as if null
   *       was the omitted value.
   *   <li>Names and values separated by {@code =} or {@code =>} instead of
   *       {@code :}.
   *   <li>Name/value pairs separated by {@code ;} instead of {@code ,}.
   * </ul>
   */
  public final void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  /**
   * Returns true if this parser is liberal in what it accepts.
   */
  public final boolean isLenient() {
    return lenient;
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * beginning of a new array.
   */
  public void beginArray() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_BEGIN_ARRAY) {
      push(JsonScope.EMPTY_ARRAY);
      pathIndices[stackSize - 1] = 0;
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected BEGIN_ARRAY but was " + peek()
          + " at path " + getPath());
    }
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * end of the current array.
   */
  public void endArray() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_END_ARRAY) {
      stackSize--;
      pathIndices[stackSize - 1]++;
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected END_ARRAY but was " + peek()
          + " at path " + getPath());
    }
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * beginning of a new object.
   */
  public void beginObject() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_BEGIN_OBJECT) {
      push(JsonScope.EMPTY_OBJECT);
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected BEGIN_OBJECT but was " + peek()
          + " at path " + getPath());
    }
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is the
   * end of the current object.
   */
  public void endObject() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_END_OBJECT) {
      stackSize--;
      pathNames[stackSize] = null; // Free the last path name so that it can be garbage collected!
      pathIndices[stackSize - 1]++;
      peeked = PEEKED_NONE;
    } else {
      throw new IllegalStateException("Expected END_OBJECT but was " + peek()
          + " at path " + getPath());
    }
  }

  /**
   * Returns true if the current array or object has another element.
   */
  public boolean hasNext() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY;
  }

  /**
   * Returns the type of the next token without consuming it.
   */
  public Token peek() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    switch (p) {
      case PEEKED_BEGIN_OBJECT:
        return Token.BEGIN_OBJECT;
      case PEEKED_END_OBJECT:
        return Token.END_OBJECT;
      case PEEKED_BEGIN_ARRAY:
        return Token.BEGIN_ARRAY;
      case PEEKED_END_ARRAY:
        return Token.END_ARRAY;
      case PEEKED_SINGLE_QUOTED_NAME:
      case PEEKED_DOUBLE_QUOTED_NAME:
      case PEEKED_UNQUOTED_NAME:
        return Token.NAME;
      case PEEKED_TRUE:
      case PEEKED_FALSE:
        return Token.BOOLEAN;
      case PEEKED_NULL:
        return Token.NULL;
      case PEEKED_SINGLE_QUOTED:
      case PEEKED_DOUBLE_QUOTED:
      case PEEKED_UNQUOTED:
      case PEEKED_BUFFERED:
        return Token.STRING;
      case PEEKED_LONG:
      case PEEKED_NUMBER:
        return Token.NUMBER;
      case PEEKED_EOF:
        return Token.END_DOCUMENT;
      default:
        throw new AssertionError();
    }
  }

  private int doPeek() throws IOException {
    int peekStack = stack[stackSize - 1];
    if (peekStack == JsonScope.EMPTY_ARRAY) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_ARRAY;
    } else if (peekStack == JsonScope.NONEMPTY_ARRAY) {
      // Look for a comma before the next element.
      int c = nextNonWhitespace(true);
      buffer.readByte(); // consume ']' or ','.
      switch (c) {
        case ']':
          return peeked = PEEKED_END_ARRAY;
        case ';':
          checkLenient(); // fall-through
        case ',':
          break;
        default:
          throw syntaxError("Unterminated array");
      }
    } else if (peekStack == JsonScope.EMPTY_OBJECT || peekStack == JsonScope.NONEMPTY_OBJECT) {
      stack[stackSize - 1] = JsonScope.DANGLING_NAME;
      // Look for a comma before the next element.
      if (peekStack == JsonScope.NONEMPTY_OBJECT) {
        int c = nextNonWhitespace(true);
        buffer.readByte(); // Consume '}' or ','.
        switch (c) {
          case '}':
            return peeked = PEEKED_END_OBJECT;
          case ';':
            checkLenient(); // fall-through
          case ',':
            break;
          default:
            throw syntaxError("Unterminated object");
        }
      }
      int c = nextNonWhitespace(true);
      switch (c) {
        case '"':
          buffer.readByte(); // consume the '\"'.
          return peeked = PEEKED_DOUBLE_QUOTED_NAME;
        case '\'':
          buffer.readByte(); // consume the '\''.
          checkLenient();
          return peeked = PEEKED_SINGLE_QUOTED_NAME;
        case '}':
          if (peekStack != JsonScope.NONEMPTY_OBJECT) {
            buffer.readByte(); // consume the '}'.
            return peeked = PEEKED_END_OBJECT;
          } else {
            throw syntaxError("Expected name");
          }
        default:
          checkLenient();
          if (isLiteral((char) c)) {
            return peeked = PEEKED_UNQUOTED_NAME;
          } else {
            throw syntaxError("Expected name");
          }
      }
    } else if (peekStack == JsonScope.DANGLING_NAME) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_OBJECT;
      // Look for a colon before the value.
      int c = nextNonWhitespace(true);
      buffer.readByte(); // Consume ':'.
      switch (c) {
        case ':':
          break;
        case '=':
          checkLenient();
          if (fillBuffer(1) && buffer.getByte(0) == '>') {
            buffer.readByte(); // Consume '>'.
          }
          break;
        default:
          throw syntaxError("Expected ':'");
      }
    } else if (peekStack == JsonScope.EMPTY_DOCUMENT) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_DOCUMENT;
    } else if (peekStack == JsonScope.NONEMPTY_DOCUMENT) {
      int c = nextNonWhitespace(false);
      if (c == -1) {
        return peeked = PEEKED_EOF;
      } else {
        checkLenient();
      }
    } else if (peekStack == JsonScope.CLOSED) {
      throw new IllegalStateException("JsonReader is closed");
    }

    int c = nextNonWhitespace(true);
    switch (c) {
      case ']':
        if (peekStack == JsonScope.EMPTY_ARRAY) {
          buffer.readByte(); // Consume ']'.
          return peeked = PEEKED_END_ARRAY;
        }
        // fall-through to handle ",]"
      case ';':
      case ',':
        // In lenient mode, a 0-length literal in an array means 'null'.
        if (peekStack == JsonScope.EMPTY_ARRAY || peekStack == JsonScope.NONEMPTY_ARRAY) {
          checkLenient();
          return peeked = PEEKED_NULL;
        } else {
          throw syntaxError("Unexpected value");
        }
      case '\'':
        checkLenient();
        buffer.readByte(); // Consume '\''.
        return peeked = PEEKED_SINGLE_QUOTED;
      case '"':
        if (stackSize == 1) {
          checkLenient();
        }
        buffer.readByte(); // Consume '\"'.
        return peeked = PEEKED_DOUBLE_QUOTED;
      case '[':
        buffer.readByte(); // Consume '['.
        return peeked = PEEKED_BEGIN_ARRAY;
      case '{':
        buffer.readByte(); // Consume '{'.
        return peeked = PEEKED_BEGIN_OBJECT;
      default:
    }

    if (stackSize == 1) {
      checkLenient(); // Top-level value isn't an array or an object.
    }

    int result = peekKeyword();
    if (result != PEEKED_NONE) {
      return result;
    }

    result = peekNumber();
    if (result != PEEKED_NONE) {
      return result;
    }

    if (!isLiteral(buffer.getByte(0))) {
      throw syntaxError("Expected value");
    }

    checkLenient();
    return peeked = PEEKED_UNQUOTED;
  }

  private int peekKeyword() throws IOException {
    // Figure out which keyword we're matching against by its first character.
    byte c = buffer.getByte(0);
    String keyword;
    String keywordUpper;
    int peeking;
    if (c == 't' || c == 'T') {
      keyword = "true";
      keywordUpper = "TRUE";
      peeking = PEEKED_TRUE;
    } else if (c == 'f' || c == 'F') {
      keyword = "false";
      keywordUpper = "FALSE";
      peeking = PEEKED_FALSE;
    } else if (c == 'n' || c == 'N') {
      keyword = "null";
      keywordUpper = "NULL";
      peeking = PEEKED_NULL;
    } else {
      return PEEKED_NONE;
    }

    // Confirm that chars [1..length) match the keyword.
    int length = keyword.length();
    for (int i = 1; i < length; i++) {
      if (!fillBuffer(i + 1)) {
        return PEEKED_NONE;
      }
      c = buffer.getByte(i);
      if (c != keyword.charAt(i) && c != keywordUpper.charAt(i)) {
        return PEEKED_NONE;
      }
    }

    if (fillBuffer(length + 1) && isLiteral(buffer.getByte(length))) {
      return PEEKED_NONE; // Don't match trues, falsey or nullsoft!
    }

    // We've found the keyword followed either by EOF or by a non-literal character.
    buffer.skip(length);
    return peeked = peeking;
  }

  private int peekNumber() throws IOException {
    long value = 0; // Negative to accommodate Long.MIN_VALUE more easily.
    boolean negative = false;
    boolean fitsInLong = true;
    int last = NUMBER_CHAR_NONE;

    int i = 0;

    charactersOfNumber:
    for (; true; i++) {
      if (!fillBuffer(i + 1)) {
        break;
      }

      byte c = buffer.getByte(i);
      switch (c) {
        case '-':
          if (last == NUMBER_CHAR_NONE) {
            negative = true;
            last = NUMBER_CHAR_SIGN;
            continue;
          } else if (last == NUMBER_CHAR_EXP_E) {
            last = NUMBER_CHAR_EXP_SIGN;
            continue;
          }
          return PEEKED_NONE;

        case '+':
          if (last == NUMBER_CHAR_EXP_E) {
            last = NUMBER_CHAR_EXP_SIGN;
            continue;
          }
          return PEEKED_NONE;

        case 'e':
        case 'E':
          if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT) {
            last = NUMBER_CHAR_EXP_E;
            continue;
          }
          return PEEKED_NONE;

        case '.':
          if (last == NUMBER_CHAR_DIGIT) {
            last = NUMBER_CHAR_DECIMAL;
            continue;
          }
          return PEEKED_NONE;

        default:
          if (c < '0' || c > '9') {
            if (!isLiteral(c)) {
              break charactersOfNumber;
            }
            return PEEKED_NONE;
          }
          if (last == NUMBER_CHAR_SIGN || last == NUMBER_CHAR_NONE) {
            value = -(c - '0');
            last = NUMBER_CHAR_DIGIT;
          } else if (last == NUMBER_CHAR_DIGIT) {
            if (value == 0) {
              return PEEKED_NONE; // Leading '0' prefix is not allowed (since it could be octal).
            }
            long newValue = value * 10 - (c - '0');
            fitsInLong &= value > MIN_INCOMPLETE_INTEGER
                || (value == MIN_INCOMPLETE_INTEGER && newValue < value);
            value = newValue;
          } else if (last == NUMBER_CHAR_DECIMAL) {
            last = NUMBER_CHAR_FRACTION_DIGIT;
          } else if (last == NUMBER_CHAR_EXP_E || last == NUMBER_CHAR_EXP_SIGN) {
            last = NUMBER_CHAR_EXP_DIGIT;
          }
      }
    }

    // We've read a complete number. Decide if it's a PEEKED_LONG or a PEEKED_NUMBER.
    if (last == NUMBER_CHAR_DIGIT && fitsInLong && (value != Long.MIN_VALUE || negative)) {
      peekedLong = negative ? value : -value;
      buffer.skip(i);
      return peeked = PEEKED_LONG;
    } else if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT
        || last == NUMBER_CHAR_EXP_DIGIT) {
      peekedNumberLength = i;
      return peeked = PEEKED_NUMBER;
    } else {
      return PEEKED_NONE;
    }
  }

  private boolean isLiteral(int c) throws IOException {
    switch (c) {
      case '/':
      case '\\':
      case ';':
      case '#':
      case '=':
        checkLenient(); // fall-through
      case '{':
      case '}':
      case '[':
      case ']':
      case ':':
      case ',':
      case ' ':
      case '\t':
      case '\f':
      case '\r':
      case '\n':
        return false;
      default:
        return true;
    }
  }

  /**
   * Returns the next token, a {@link Token#NAME property name}, and
   * consumes it.
   *
   * @throws java.io.IOException if the next token in the stream is not a property
   *     name.
   */
  public String nextName() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_UNQUOTED_NAME) {
      result = nextUnquotedValue();
    } else if (p == PEEKED_DOUBLE_QUOTED_NAME) {
      result = nextQuotedValue(DOUBLE_QUOTE_OR_SLASH);
    } else if (p == PEEKED_SINGLE_QUOTED_NAME) {
      result = nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
    } else {
      throw new IllegalStateException("Expected a name but was " + peek()
          + " at path " + getPath());
    }
    peeked = PEEKED_NONE;
    pathNames[stackSize - 1] = result;
    return result;
  }

  /**
   * Returns the {@link Token#STRING string} value of the next token,
   * consuming it. If the next token is a number, this method will return its
   * string form.
   *
   * @throws IllegalStateException if the next token is not a string or if
   *     this reader is closed.
   */
  public String nextString() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_UNQUOTED) {
      result = nextUnquotedValue();
    } else if (p == PEEKED_DOUBLE_QUOTED) {
      result = nextQuotedValue(DOUBLE_QUOTE_OR_SLASH);
    } else if (p == PEEKED_SINGLE_QUOTED) {
      result = nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
    } else if (p == PEEKED_BUFFERED) {
      result = peekedString;
      peekedString = null;
    } else if (p == PEEKED_LONG) {
      result = Long.toString(peekedLong);
    } else if (p == PEEKED_NUMBER) {
      result = buffer.readUtf8(peekedNumberLength);
    } else {
      throw new IllegalStateException("Expected a string but was " + peek()
          + " at path " + getPath());
    }
    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  /**
   * Returns the {@link Token#BOOLEAN boolean} value of the next token,
   * consuming it.
   *
   * @throws IllegalStateException if the next token is not a boolean or if
   *     this reader is closed.
   */
  public boolean nextBoolean() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_TRUE) {
      peeked = PEEKED_NONE;
      pathIndices[stackSize - 1]++;
      return true;
    } else if (p == PEEKED_FALSE) {
      peeked = PEEKED_NONE;
      pathIndices[stackSize - 1]++;
      return false;
    }
    throw new IllegalStateException("Expected a boolean but was " + peek()
        + " at path " + getPath());
  }

  /**
   * Consumes the next token from the JSON stream and asserts that it is a
   * literal null. Returns null.
   *
   * @throws IllegalStateException if the next token is not null or if this
   *     reader is closed.
   */
  public <T> T nextNull() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_NULL) {
      peeked = PEEKED_NONE;
      pathIndices[stackSize - 1]++;
      return null;
    } else {
      throw new IllegalStateException("Expected null but was " + peek()
          + " at path " + getPath());
    }
  }

  /**
   * Returns the {@link Token#NUMBER double} value of the next token,
   * consuming it. If the next token is a string, this method will attempt to
   * parse it as a double using {@link Double#parseDouble(String)}.
   *
   * @throws IllegalStateException if the next token is not a literal value.
   * @throws NumberFormatException if the next literal value cannot be parsed
   *     as a double, or is non-finite.
   */
  public double nextDouble() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE;
      pathIndices[stackSize - 1]++;
      return (double) peekedLong;
    }

    if (p == PEEKED_NUMBER) {
      peekedString = buffer.readUtf8(peekedNumberLength);
    } else if (p == PEEKED_DOUBLE_QUOTED) {
      peekedString = nextQuotedValue(DOUBLE_QUOTE_OR_SLASH);
    } else if (p == PEEKED_SINGLE_QUOTED) {
      peekedString = nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
    } else if (p == PEEKED_UNQUOTED) {
      peekedString = nextUnquotedValue();
    } else if (p != PEEKED_BUFFERED) {
      throw new IllegalStateException("Expected a double but was " + peek()
          + " at path " + getPath());
    }

    peeked = PEEKED_BUFFERED;
    double result = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
    if (!lenient && (Double.isNaN(result) || Double.isInfinite(result))) {
      throw new NumberFormatException("JSON forbids NaN and infinities: " + result
          + " at path " + getPath());
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  /**
   * Returns the {@link Token#NUMBER long} value of the next token,
   * consuming it. If the next token is a string, this method will attempt to
   * parse it as a long. If the next token's numeric value cannot be exactly
   * represented by a Java {@code long}, this method throws.
   *
   * @throws IllegalStateException if the next token is not a literal value.
   * @throws NumberFormatException if the next literal value cannot be parsed
   *     as a number, or exactly represented as a long.
   */
  public long nextLong() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    if (p == PEEKED_LONG) {
      peeked = PEEKED_NONE;
      pathIndices[stackSize - 1]++;
      return peekedLong;
    }

    if (p == PEEKED_NUMBER) {
      peekedString = buffer.readUtf8(peekedNumberLength);
    } else if (p == PEEKED_DOUBLE_QUOTED || p == PEEKED_SINGLE_QUOTED) {
      peekedString = p == PEEKED_DOUBLE_QUOTED
          ? nextQuotedValue(DOUBLE_QUOTE_OR_SLASH)
          : nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
      try {
        long result = Long.parseLong(peekedString);
        peeked = PEEKED_NONE;
        pathIndices[stackSize - 1]++;
        return result;
      } catch (NumberFormatException ignored) {
        // Fall back to parse as a double below.
      }
    } else {
      throw new IllegalStateException("Expected a long but was " + peek()
          + " at path " + getPath());
    }

    peeked = PEEKED_BUFFERED;
    double asDouble = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
    long result = (long) asDouble;
    if (result != asDouble) { // Make sure no precision was lost casting to 'long'.
      throw new NumberFormatException("Expected a long but was " + peekedString
          + " at path " + getPath());
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  /**
   * Returns the string up to but not including {@code quote}, unescaping any
   * character escape sequences encountered along the way. The opening quote
   * should have already been read. This consumes the closing quote, but does
   * not include it in the returned string.
   *
   * @throws NumberFormatException if any unicode escape sequences are
   *     malformed.
   */
  private String nextQuotedValue(ByteString runTerminator) throws IOException {
    StringBuilder builder = null;
    while (true) {
      long index = source.indexOfElement(runTerminator);
      if (index == -1L) throw syntaxError("Unterminated string");

      // If we've got an escape character, we're going to need a string builder.
      if (buffer.getByte(index) == '\\') {
        if (builder == null) builder = new StringBuilder();
        builder.append(buffer.readUtf8(index));
        buffer.readByte(); // '\'
        builder.append(readEscapeCharacter());
        continue;
      }

      // If it isn't the escape character, it's the quote. Return the string.
      if (builder == null) {
        String result = buffer.readUtf8(index);
        buffer.readByte(); // Consume the quote character.
        return result;
      } else {
        builder.append(buffer.readUtf8(index));
        buffer.readByte(); // Consume the quote character.
        return builder.toString();
      }
    }
  }

  /** Returns an unquoted value as a string. */
  private String nextUnquotedValue() throws IOException {
    long i = source.indexOfElement(UNQUOTED_STRING_TERMINALS);
    return i != -1 ? buffer.readUtf8(i) : buffer.readUtf8();
  }

  private void skipQuotedValue(ByteString runTerminator) throws IOException {
    while (true) {
      long index = source.indexOfElement(runTerminator);
      if (index == -1L) throw syntaxError("Unterminated string");

      if (buffer.getByte(index) == '\\') {
        buffer.skip(index + 1);
        readEscapeCharacter();
      } else {
        buffer.skip(index + 1);
        return;
      }
    }
  }

  private void skipUnquotedValue() throws IOException {
    long i = source.indexOfElement(UNQUOTED_STRING_TERMINALS);
    buffer.skip(i != -1L ? i : buffer.size());
  }

  /**
   * Returns the {@link Token#NUMBER int} value of the next token,
   * consuming it. If the next token is a string, this method will attempt to
   * parse it as an int. If the next token's numeric value cannot be exactly
   * represented by a Java {@code int}, this method throws.
   *
   * @throws IllegalStateException if the next token is not a literal value.
   * @throws NumberFormatException if the next literal value cannot be parsed
   *     as a number, or exactly represented as an int.
   */
  public int nextInt() throws IOException {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    int result;
    if (p == PEEKED_LONG) {
      result = (int) peekedLong;
      if (peekedLong != result) { // Make sure no precision was lost casting to 'int'.
        throw new NumberFormatException("Expected an int but was " + peekedLong
            + " at path " + getPath());
      }
      peeked = PEEKED_NONE;
      pathIndices[stackSize - 1]++;
      return result;
    }

    if (p == PEEKED_NUMBER) {
      peekedString = buffer.readUtf8(peekedNumberLength);
    } else if (p == PEEKED_DOUBLE_QUOTED || p == PEEKED_SINGLE_QUOTED) {
      peekedString = p == PEEKED_DOUBLE_QUOTED
          ? nextQuotedValue(DOUBLE_QUOTE_OR_SLASH)
          : nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
      try {
        result = Integer.parseInt(peekedString);
        peeked = PEEKED_NONE;
        pathIndices[stackSize - 1]++;
        return result;
      } catch (NumberFormatException ignored) {
        // Fall back to parse as a double below.
      }
    } else {
      throw new IllegalStateException("Expected an int but was " + peek()
          + " at path " + getPath());
    }

    peeked = PEEKED_BUFFERED;
    double asDouble = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
    result = (int) asDouble;
    if (result != asDouble) { // Make sure no precision was lost casting to 'int'.
      throw new NumberFormatException("Expected an int but was " + peekedString
          + " at path " + getPath());
    }
    peekedString = null;
    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  /**
   * Closes this JSON reader and the underlying {@link java.io.Reader}.
   */
  public void close() throws IOException {
    peeked = PEEKED_NONE;
    stack[0] = JsonScope.CLOSED;
    stackSize = 1;
    buffer.clear();
    source.close();
  }

  /**
   * Skips the next value recursively. If it is an object or array, all nested
   * elements are skipped. This method is intended for use when the JSON token
   * stream contains unrecognized or unhandled values.
   */
  public void skipValue() throws IOException {
    int count = 0;
    do {
      int p = peeked;
      if (p == PEEKED_NONE) {
        p = doPeek();
      }

      if (p == PEEKED_BEGIN_ARRAY) {
        push(JsonScope.EMPTY_ARRAY);
        count++;
      } else if (p == PEEKED_BEGIN_OBJECT) {
        push(JsonScope.EMPTY_OBJECT);
        count++;
      } else if (p == PEEKED_END_ARRAY) {
        stackSize--;
        count--;
      } else if (p == PEEKED_END_OBJECT) {
        stackSize--;
        count--;
      } else if (p == PEEKED_UNQUOTED_NAME || p == PEEKED_UNQUOTED) {
        skipUnquotedValue();
      } else if (p == PEEKED_DOUBLE_QUOTED || p == PEEKED_DOUBLE_QUOTED_NAME) {
        skipQuotedValue(DOUBLE_QUOTE_OR_SLASH);
      } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_SINGLE_QUOTED_NAME) {
        skipQuotedValue(SINGLE_QUOTE_OR_SLASH);
      } else if (p == PEEKED_NUMBER) {
        buffer.skip(peekedNumberLength);
      }
      peeked = PEEKED_NONE;
    } while (count != 0);

    pathIndices[stackSize - 1]++;
    pathNames[stackSize - 1] = "null";
  }

  private void push(int newTop) {
    if (stackSize == stack.length) {
      int[] newStack = new int[stackSize * 2];
      int[] newPathIndices = new int[stackSize * 2];
      String[] newPathNames = new String[stackSize * 2];
      System.arraycopy(stack, 0, newStack, 0, stackSize);
      System.arraycopy(pathIndices, 0, newPathIndices, 0, stackSize);
      System.arraycopy(pathNames, 0, newPathNames, 0, stackSize);
      stack = newStack;
      pathIndices = newPathIndices;
      pathNames = newPathNames;
    }
    stack[stackSize++] = newTop;
  }

  /**
   * Returns true once {@code limit - pos >= minimum}. If the data is
   * exhausted before that many characters are available, this returns
   * false.
   */
  private boolean fillBuffer(int minimum) throws IOException {
    return source.request(minimum);
  }

  /**
   * Returns the next character in the stream that is neither whitespace nor a
   * part of a comment. When this returns, the returned character is always at
   * {@code buffer[pos-1]}; this means the caller can always push back the
   * returned character by decrementing {@code pos}.
   */
  private int nextNonWhitespace(boolean throwOnEof) throws IOException {
    /*
     * This code uses ugly local variables 'p' and 'l' representing the 'pos'
     * and 'limit' fields respectively. Using locals rather than fields saves
     * a few field reads for each whitespace character in a pretty-printed
     * document, resulting in a 5% speedup. We need to flush 'p' to its field
     * before any (potentially indirect) call to fillBuffer() and reread both
     * 'p' and 'l' after any (potentially indirect) call to the same method.
     */
    int p = 0;
    while (fillBuffer(p + 1)) {
      int c = buffer.getByte(p++);
      if (c == '\n' || c == ' ' || c == '\r' || c == '\t') {
        continue;
      }

      buffer.skip(p - 1);
      if (c == '/') {
        if (!fillBuffer(2)) {
          return c;
        }

        checkLenient();
        byte peek = buffer.getByte(1);
        switch (peek) {
          case '*':
            // skip a /* c-style comment */
            buffer.readByte(); // '/'
            buffer.readByte(); // '*'
            if (!skipTo("*/")) {
              throw syntaxError("Unterminated comment");
            }
            buffer.readByte(); // '*'
            buffer.readByte(); // '/'
            p = 0;
            continue;

          case '/':
            // skip a // end-of-line comment
            buffer.readByte(); // '/'
            buffer.readByte(); // '/'
            skipToEndOfLine();
            p = 0;
            continue;

          default:
            return c;
        }
      } else if (c == '#') {
        /*
         * Skip a # hash end-of-line comment. The JSON RFC doesn't
         * specify this behaviour, but it's required to parse
         * existing documents. See http://b/2571423.
         */
        checkLenient();
        skipToEndOfLine();
        p = 0;
      } else {
        return c;
      }
    }
    if (throwOnEof) {
      throw new EOFException("End of input");
    } else {
      return -1;
    }
  }

  private void checkLenient() throws IOException {
    if (!lenient) {
      throw syntaxError("Use JsonReader.setLenient(true) to accept malformed JSON");
    }
  }

  /**
   * Advances the position until after the next newline character. If the line
   * is terminated by "\r\n", the '\n' must be consumed as whitespace by the
   * caller.
   */
  private void skipToEndOfLine() throws IOException {
    long index = source.indexOfElement(LINEFEED_OR_CARRIAGE_RETURN);
    buffer.skip(index != -1 ? index + 1 : buffer.size());
  }

  /**
   * @param toFind a string to search for. Must not contain a newline.
   */
  private boolean skipTo(String toFind) throws IOException {
    outer:
    for (; fillBuffer(toFind.length());) {
      for (int c = 0; c < toFind.length(); c++) {
        if (buffer.getByte(c) != toFind.charAt(c)) {
          buffer.readByte();
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  @Override public String toString() {
    return getClass().getSimpleName();
  }

  /**
   * Returns a <a href="http://goessner.net/articles/JsonPath/">JsonPath</a> to
   * the current location in the JSON value.
   */
  public String getPath() {
    StringBuilder result = new StringBuilder().append('$');
    for (int i = 0, size = stackSize; i < size; i++) {
      switch (stack[i]) {
        case JsonScope.EMPTY_ARRAY:
        case JsonScope.NONEMPTY_ARRAY:
          result.append('[').append(pathIndices[i]).append(']');
          break;

        case JsonScope.EMPTY_OBJECT:
        case JsonScope.DANGLING_NAME:
        case JsonScope.NONEMPTY_OBJECT:
          result.append('.');
          if (pathNames[i] != null) {
            result.append(pathNames[i]);
          }
          break;

        case JsonScope.NONEMPTY_DOCUMENT:
        case JsonScope.EMPTY_DOCUMENT:
        case JsonScope.CLOSED:
          break;
      }
    }
    return result.toString();
  }

  /**
   * Unescapes the character identified by the character or characters that
   * immediately follow a backslash. The backslash '\' should have already
   * been read. This supports both unicode escapes "u000A" and two-character
   * escapes "\n".
   *
   * @throws NumberFormatException if any unicode escape sequences are
   *     malformed.
   */
  private char readEscapeCharacter() throws IOException {
    if (!fillBuffer(1)) {
      throw syntaxError("Unterminated escape sequence");
    }

    byte escaped = buffer.readByte();
    switch (escaped) {
      case 'u':
        if (!fillBuffer(4)) {
          throw syntaxError("Unterminated escape sequence");
        }
        // Equivalent to Integer.parseInt(stringPool.get(buffer, pos, 4), 16);
        char result = 0;
        for (int i = 0, end = i + 4; i < end; i++) {
          byte c = buffer.getByte(i);
          result <<= 4;
          if (c >= '0' && c <= '9') {
            result += (c - '0');
          } else if (c >= 'a' && c <= 'f') {
            result += (c - 'a' + 10);
          } else if (c >= 'A' && c <= 'F') {
            result += (c - 'A' + 10);
          } else {
            throw new NumberFormatException("\\u" + buffer.readUtf8(4));
          }
        }
        buffer.skip(4);
        return result;

      case 't':
        return '\t';

      case 'b':
        return '\b';

      case 'n':
        return '\n';

      case 'r':
        return '\r';

      case 'f':
        return '\f';

      case '\n':
      case '\'':
      case '"':
      case '\\':
      default:
        return (char) escaped;
    }
  }

  /**
   * Throws a new IO exception with the given message and a context snippet
   * with this reader's content.
   */
  private IOException syntaxError(String message) throws IOException {
    throw new IOException(message + " at path " + getPath());
  }

  /**
   * A structure, name, or value type in a JSON-encoded string.
   */
  public enum Token {

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
}
