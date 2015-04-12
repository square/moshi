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

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import okio.Buffer;
import org.junit.Ignore;
import org.junit.Test;

import static com.squareup.moshi.JsonReader.Token.BEGIN_ARRAY;
import static com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT;
import static com.squareup.moshi.JsonReader.Token.BOOLEAN;
import static com.squareup.moshi.JsonReader.Token.END_ARRAY;
import static com.squareup.moshi.JsonReader.Token.END_OBJECT;
import static com.squareup.moshi.JsonReader.Token.NAME;
import static com.squareup.moshi.JsonReader.Token.NULL;
import static com.squareup.moshi.JsonReader.Token.NUMBER;
import static com.squareup.moshi.JsonReader.Token.STRING;
import static com.squareup.moshi.TestUtil.newReader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class JsonReaderTest {
  @Test public void readingDoesNotBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{}{}");

    JsonReader reader1 = new JsonReader(buffer);
    reader1.beginObject();
    reader1.endObject();
    assertEquals(2, buffer.size());

    JsonReader reader2 = new JsonReader(buffer);
    reader2.beginObject();
    reader2.endObject();
    assertEquals(0, buffer.size());
  }

  @Test public void readArray() throws IOException {
    JsonReader reader = newReader("[true, true]");
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    assertEquals(true, reader.nextBoolean());
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void readEmptyArray() throws IOException {
    JsonReader reader = newReader("[]");
    reader.beginArray();
    assertFalse(reader.hasNext());
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void readObject() throws IOException {
    JsonReader reader = newReader("{\"a\": \"android\", \"b\": \"banana\"}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals("android", reader.nextString());
    assertEquals("b", reader.nextName());
    assertEquals("banana", reader.nextString());
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void readObjectBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = new JsonReader(buffer);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals("android", reader.nextString());
    assertEquals("b", reader.nextName());
    assertEquals("banana", reader.nextString());
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void readObjectSource() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = new JsonReader(buffer);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals("android", reader.nextString());
    assertEquals("b", reader.nextName());
    assertEquals("banana", reader.nextString());
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void readEmptyObject() throws IOException {
    JsonReader reader = newReader("{}");
    reader.beginObject();
    assertFalse(reader.hasNext());
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipArray() throws IOException {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();
    assertEquals("b", reader.nextName());
    assertEquals(123, reader.nextInt());
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipArrayAfterPeek() throws Exception {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(BEGIN_ARRAY, reader.peek());
    reader.skipValue();
    assertEquals("b", reader.nextName());
    assertEquals(123, reader.nextInt());
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipTopLevelObject() throws Exception {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.skipValue();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipObject() throws IOException {
    JsonReader reader = newReader(
        "{\"a\": { \"c\": [], \"d\": [true, true, {}] }, \"b\": \"banana\"}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();
    assertEquals("b", reader.nextName());
    reader.skipValue();
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipObjectAfterPeek() throws Exception {
    String json = "{" + "  \"one\": { \"num\": 1 }"
        + ", \"two\": { \"num\": 2 }" + ", \"three\": { \"num\": 3 }" + "}";
    JsonReader reader = newReader(json);
    reader.beginObject();
    assertEquals("one", reader.nextName());
    assertEquals(BEGIN_OBJECT, reader.peek());
    reader.skipValue();
    assertEquals("two", reader.nextName());
    assertEquals(BEGIN_OBJECT, reader.peek());
    reader.skipValue();
    assertEquals("three", reader.nextName());
    reader.skipValue();
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipInteger() throws IOException {
    JsonReader reader = newReader("{\"a\":123456789,\"b\":-123456789}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();
    assertEquals("b", reader.nextName());
    reader.skipValue();
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipDouble() throws IOException {
    JsonReader reader = newReader("{\"a\":-123.456e-789,\"b\":123456789.0}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    reader.skipValue();
    assertEquals("b", reader.nextName());
    reader.skipValue();
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void helloWorld() throws IOException {
    String json = "{\n" +
        "   \"hello\": true,\n" +
        "   \"foo\": [\"world\"]\n" +
        "}";
    JsonReader reader = newReader(json);
    reader.beginObject();
    assertEquals("hello", reader.nextName());
    assertEquals(true, reader.nextBoolean());
    assertEquals("foo", reader.nextName());
    reader.beginArray();
    assertEquals("world", reader.nextString());
    reader.endArray();
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void nullSource() {
    try {
      new JsonReader(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void emptyString() {
    try {
      newReader("").beginArray();
      fail();
    } catch (IOException expected) {
    }
    try {
      newReader("").beginObject();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void noTopLevelObject() {
    try {
      newReader("true").nextBoolean();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void characterUnescaping() throws IOException {
    String json = "[\"a\","
        + "\"a\\\"\","
        + "\"\\\"\","
        + "\":\","
        + "\",\","
        + "\"\\b\","
        + "\"\\f\","
        + "\"\\n\","
        + "\"\\r\","
        + "\"\\t\","
        + "\" \","
        + "\"\\\\\","
        + "\"{\","
        + "\"}\","
        + "\"[\","
        + "\"]\","
        + "\"\\u0000\","
        + "\"\\u0019\","
        + "\"\\u20AC\""
        + "]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals("a", reader.nextString());
    assertEquals("a\"", reader.nextString());
    assertEquals("\"", reader.nextString());
    assertEquals(":", reader.nextString());
    assertEquals(",", reader.nextString());
    assertEquals("\b", reader.nextString());
    assertEquals("\f", reader.nextString());
    assertEquals("\n", reader.nextString());
    assertEquals("\r", reader.nextString());
    assertEquals("\t", reader.nextString());
    assertEquals(" ", reader.nextString());
    assertEquals("\\", reader.nextString());
    assertEquals("{", reader.nextString());
    assertEquals("}", reader.nextString());
    assertEquals("[", reader.nextString());
    assertEquals("]", reader.nextString());
    assertEquals("\0", reader.nextString());
    assertEquals("\u0019", reader.nextString());
    assertEquals("\u20AC", reader.nextString());
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void unescapingInvalidCharacters() throws IOException {
    String json = "[\"\\u000g\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (NumberFormatException expected) {
    }
  }

  @Test public void unescapingTruncatedCharacters() throws IOException {
    String json = "[\"\\u000";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void unescapingTruncatedSequence() throws IOException {
    String json = "[\"\\";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void integersWithFractionalPartSpecified() throws IOException {
    JsonReader reader = newReader("[1.0,1.0,1.0]");
    reader.beginArray();
    assertEquals(1.0, reader.nextDouble(), 0d);
    assertEquals(1, reader.nextInt());
    assertEquals(1L, reader.nextLong());
  }

  @Test public void doubles() throws IOException {
    String json = "[-0.0,"
        + "1.0,"
        + "1.7976931348623157E308,"
        + "4.9E-324,"
        + "0.0,"
        + "-0.5,"
        + "2.2250738585072014E-308,"
        + "3.141592653589793,"
        + "2.718281828459045]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals(-0.0, reader.nextDouble(), 0d);
    assertEquals(1.0, reader.nextDouble(), 0d);
    assertEquals(1.7976931348623157E308, reader.nextDouble(), 0d);
    assertEquals(4.9E-324, reader.nextDouble(), 0d);
    assertEquals(0.0, reader.nextDouble(), 0d);
    assertEquals(-0.5, reader.nextDouble(), 0d);
    assertEquals(2.2250738585072014E-308, reader.nextDouble(), 0d);
    assertEquals(3.141592653589793, reader.nextDouble(), 0d);
    assertEquals(2.718281828459045, reader.nextDouble(), 0d);
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void strictNonFiniteDoubles() throws IOException {
    String json = "[NaN]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictQuotedNonFiniteDoubles() throws IOException {
    String json = "[\"NaN\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (NumberFormatException expected) {
      assertThat(expected).hasMessageContaining("NaN");
    }
  }

  @Test public void lenientNonFiniteDoubles() throws IOException {
    String json = "[NaN, -Infinity, Infinity]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(Double.isNaN(reader.nextDouble()));
    assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble(), 0d);
    assertEquals(Double.POSITIVE_INFINITY, reader.nextDouble(), 0d);
    reader.endArray();
  }

  @Test public void lenientQuotedNonFiniteDoubles() throws IOException {
    String json = "[\"NaN\", \"-Infinity\", \"Infinity\"]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(Double.isNaN(reader.nextDouble()));
    assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble(), 0d);
    assertEquals(Double.POSITIVE_INFINITY, reader.nextDouble(), 0d);
    reader.endArray();
  }

  @Test public void strictNonFiniteDoublesWithSkipValue() throws IOException {
    String json = "[NaN]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void longs() throws IOException {
    String json = "[0,0,0,"
        + "1,1,1,"
        + "-1,-1,-1,"
        + "-9223372036854775808,"
        + "9223372036854775807]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals(0L, reader.nextLong());
    assertEquals(0, reader.nextInt());
    assertEquals(0.0, reader.nextDouble(), 0d);
    assertEquals(1L, reader.nextLong());
    assertEquals(1, reader.nextInt());
    assertEquals(1.0, reader.nextDouble(), 0d);
    assertEquals(-1L, reader.nextLong());
    assertEquals(-1, reader.nextInt());
    assertEquals(-1.0, reader.nextDouble(), 0d);
    try {
      reader.nextInt();
      fail();
    } catch (NumberFormatException expected) {
    }
    assertEquals(Long.MIN_VALUE, reader.nextLong());
    try {
      reader.nextInt();
      fail();
    } catch (NumberFormatException expected) {
    }
    assertEquals(Long.MAX_VALUE, reader.nextLong());
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test @Ignore public void numberWithOctalPrefix() throws IOException {
    String json = "[01]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
    try {
      reader.nextInt();
      fail();
    } catch (IOException expected) {
    }
    try {
      reader.nextLong();
      fail();
    } catch (IOException expected) {
    }
    try {
      reader.nextDouble();
      fail();
    } catch (IOException expected) {
    }
    assertEquals("01", reader.nextString());
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void booleans() throws IOException {
    JsonReader reader = newReader("[true,false]");
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    assertEquals(false, reader.nextBoolean());
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void peekingUnquotedStringsPrefixedWithBooleans() throws IOException {
    JsonReader reader = newReader("[truey]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(STRING, reader.peek());
    try {
      reader.nextBoolean();
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("truey", reader.nextString());
    reader.endArray();
  }

  @Test public void malformedNumbers() throws IOException {
    assertNotANumber("-");
    assertNotANumber(".");

    // exponent lacks digit
    assertNotANumber("e");
    assertNotANumber("0e");
    assertNotANumber(".e");
    assertNotANumber("0.e");
    assertNotANumber("-.0e");

    // no integer
    assertNotANumber("e1");
    assertNotANumber(".e1");
    assertNotANumber("-e1");

    // trailing characters
    assertNotANumber("1x");
    assertNotANumber("1.1x");
    assertNotANumber("1e1x");
    assertNotANumber("1ex");
    assertNotANumber("1.1ex");
    assertNotANumber("1.1e1x");

    // fraction has no digit
    assertNotANumber("0.");
    assertNotANumber("-0.");
    assertNotANumber("0.e1");
    assertNotANumber("-0.e1");

    // no leading digit
    assertNotANumber(".0");
    assertNotANumber("-.0");
    assertNotANumber(".0e1");
    assertNotANumber("-.0e1");
  }

  private void assertNotANumber(String s) throws IOException {
    JsonReader reader = newReader("[" + s + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(JsonReader.Token.STRING, reader.peek());
    assertEquals(s, reader.nextString());
    reader.endArray();
  }

  @Test public void peekingUnquotedStringsPrefixedWithIntegers() throws IOException {
    JsonReader reader = newReader("[12.34e5x]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(STRING, reader.peek());
    try {
      reader.nextInt();
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("12.34e5x", reader.nextString());
  }

  @Test public void peekLongMinValue() throws IOException {
    JsonReader reader = newReader("[-9223372036854775808]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(NUMBER, reader.peek());
    assertEquals(-9223372036854775808L, reader.nextLong());
  }

  @Test public void peekLongMaxValue() throws IOException {
    JsonReader reader = newReader("[9223372036854775807]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(NUMBER, reader.peek());
    assertEquals(9223372036854775807L, reader.nextLong());
  }

  @Test public void longLargerThanMaxLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(NUMBER, reader.peek());
    try {
      reader.nextLong();
      fail();
    } catch (NumberFormatException expected) {
    }
  }

  @Test public void longLargerThanMinLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[-22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(NUMBER, reader.peek());
    try {
      reader.nextLong();
      fail();
    } catch (NumberFormatException expected) {
    }
  }

  /**
   * This test fails because there's no double for 9223372036854775808, and our
   * long parsing uses Double.parseDouble() for fractional values.
   */
  @Test @Ignore public void peekLargerThanLongMaxValue() throws IOException {
    JsonReader reader = newReader("[9223372036854775808]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(NUMBER, reader.peek());
    try {
      reader.nextLong();
      fail();
    } catch (NumberFormatException expected) {
    }
  }

  /**
   * This test fails because there's no double for -9223372036854775809, and our
   * long parsing uses Double.parseDouble() for fractional values.
   */
  @Test @Ignore public void peekLargerThanLongMinValue() throws IOException {
    JsonReader reader = newReader("[-9223372036854775809]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(NUMBER, reader.peek());
    try {
      reader.nextLong();
      fail();
    } catch (NumberFormatException expected) {
    }
    assertEquals(-9223372036854775809d, reader.nextDouble(), 0d);
  }

  /**
   * This test fails because there's no double for 9223372036854775806, and
   * our long parsing uses Double.parseDouble() for fractional values.
   */
  @Test @Ignore public void highPrecisionLong() throws IOException {
    String json = "[9223372036854775806.000]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals(9223372036854775806L, reader.nextLong());
    reader.endArray();
  }

  @Test public void peekMuchLargerThanLongMinValue() throws IOException {
    JsonReader reader = newReader("[-92233720368547758080]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(NUMBER, reader.peek());
    try {
      reader.nextLong();
      fail();
    } catch (NumberFormatException expected) {
    }
    assertEquals(-92233720368547758080d, reader.nextDouble(), 0d);
  }

  @Test public void quotedNumberWithEscape() throws IOException {
    JsonReader reader = newReader("[\"12\u00334\"]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(STRING, reader.peek());
    assertEquals(1234, reader.nextInt());
  }

  @Test public void mixedCaseLiterals() throws IOException {
    JsonReader reader = newReader("[True,TruE,False,FALSE,NULL,nulL]");
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    assertEquals(true, reader.nextBoolean());
    assertEquals(false, reader.nextBoolean());
    assertEquals(false, reader.nextBoolean());
    reader.nextNull();
    reader.nextNull();
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void missingValue() throws IOException {
    JsonReader reader = newReader("{\"a\":}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    try {
      reader.nextString();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void prematureEndOfInput() throws IOException {
    JsonReader reader = newReader("{\"a\":true,");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(true, reader.nextBoolean());
    try {
      reader.nextName();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void prematurelyClosed() throws IOException {
    try {
      JsonReader reader = newReader("{\"a\":[]}");
      reader.beginObject();
      reader.close();
      reader.nextName();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      JsonReader reader = newReader("{\"a\":[]}");
      reader.close();
      reader.beginObject();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      JsonReader reader = newReader("{\"a\":true}");
      reader.beginObject();
      reader.nextName();
      reader.peek();
      reader.close();
      reader.nextBoolean();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void nextFailuresDoNotAdvance() throws IOException {
    JsonReader reader = newReader("{\"a\":true}");
    reader.beginObject();
    try {
      reader.nextString();
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("a", reader.nextName());
    try {
      reader.nextName();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      reader.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      reader.endArray();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      reader.beginObject();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      reader.endObject();
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(true, reader.nextBoolean());
    try {
      reader.nextString();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      reader.nextName();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      reader.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      reader.endArray();
      fail();
    } catch (IllegalStateException expected) {
    }
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
    reader.close();
  }

  @Test public void integerMismatchFailuresDoNotAdvance() throws IOException {
    JsonReader reader = newReader("[1.5]");
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (NumberFormatException expected) {
    }
    assertEquals(1.5d, reader.nextDouble(), 0d);
    reader.endArray();
  }

  @Test public void stringNullIsNotNull() throws IOException {
    JsonReader reader = newReader("[\"null\"]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void nullLiteralIsNotAString() throws IOException {
    JsonReader reader = newReader("[null]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void strictNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    try {
      reader.nextBoolean();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    try {
      reader.nextBoolean();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(true, reader.nextBoolean());

    reader = newReader("{\"a\"=>true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(true, reader.nextBoolean());
  }

  @Test public void strictNameValueSeparatorWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void commentsInStringValue() throws Exception {
    JsonReader reader = newReader("[\"// comment\"]");
    reader.beginArray();
    assertEquals("// comment", reader.nextString());
    reader.endArray();

    reader = newReader("{\"a\":\"#someComment\"}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals("#someComment", reader.nextString());
    reader.endObject();

    reader = newReader("{\"#//a\":\"#some //Comment\"}");
    reader.beginObject();
    assertEquals("#//a", reader.nextName());
    assertEquals("#some //Comment", reader.nextString());
    reader.endObject();
  }

  @Test public void strictComments() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[# comment \n true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[/* comment */ true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientComments() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());

    reader = newReader("[# comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());

    reader = newReader("[/* comment */ true]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());

    reader = newReader("a//");
    reader.setLenient(true);
    assertEquals("a", reader.nextString());
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void strictCommentsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[# comment \n true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[/* comment */ true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
  }

  @Test public void jsonIsSingleUnquotedString() throws IOException {
    JsonReader reader = newReader("abc");
    reader.setLenient(true);
    assertEquals("abc", reader.nextString());
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void strictUnquotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
  }

  @Test public void strictSingleQuotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictUnquotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals("a", reader.nextString());
  }

  @Test public void lenientUnquotedStringsDelimitedByComment() throws IOException {
    JsonReader reader = newReader("[a#comment\n]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals("a", reader.nextString());
    reader.endArray();
  }

  @Test public void strictSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals("a", reader.nextString());
  }

  @Test public void strictSingleQuotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      reader.nextBoolean();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    assertEquals(true, reader.nextBoolean());
  }

  @Test public void strictSemicolonDelimitedArrayWithSkipValue() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    try {
      reader.nextBoolean();
      reader.nextName();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(true, reader.nextBoolean());
    assertEquals("b", reader.nextName());
  }

  @Test public void strictSemicolonDelimitedNameValuePairWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    try {
      reader.nextNull();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[,true]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[true,]");
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    try {
      reader.nextNull();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[,]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    reader.nextNull();
    assertEquals(true, reader.nextBoolean());
    reader.endArray();

    reader = newReader("[,true]");
    reader.setLenient(true);
    reader.beginArray();
    reader.nextNull();
    assertEquals(true, reader.nextBoolean());
    reader.endArray();

    reader = newReader("[true,]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    reader.nextNull();
    reader.endArray();

    reader = newReader("[,]");
    reader.setLenient(true);
    reader.beginArray();
    reader.nextNull();
    reader.nextNull();
    reader.endArray();
  }

  @Test public void strictUnnecessaryArraySeparatorsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[,true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[true,]");
    reader.beginArray();
    assertEquals(true, reader.nextBoolean());
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }

    reader = newReader("[,]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] true {}");
    reader.setLenient(true);
    reader.beginArray();
    reader.endArray();
    assertEquals(true, reader.nextBoolean());
    reader.beginObject();
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void strictMultipleTopLevelValuesWithSkipValue() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void strictTopLevelString() {
    JsonReader reader = newReader("\"a\"");
    try {
      reader.nextString();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientTopLevelString() throws IOException {
    JsonReader reader = newReader("\"a\"");
    reader.setLenient(true);
    assertEquals("a", reader.nextString());
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void strictTopLevelValueType() {
    JsonReader reader = newReader("true");
    try {
      reader.nextBoolean();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientTopLevelValueType() throws IOException {
    JsonReader reader = newReader("true");
    reader.setLenient(true);
    assertEquals(true, reader.nextBoolean());
  }

  @Test public void strictTopLevelValueTypeWithSkipValue() {
    JsonReader reader = newReader("true");
    try {
      reader.skipValue();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test @Ignore public void bomIgnoredAsFirstCharacterOfDocument() throws IOException {
    JsonReader reader = newReader("\ufeff[]");
    reader.beginArray();
    reader.endArray();
  }

  @Test public void bomForbiddenAsOtherCharacterInDocument() throws IOException {
    JsonReader reader = newReader("[\ufeff]");
    reader.beginArray();
    try {
      reader.endArray();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void failWithPosition() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "[\n\n\n\n\n\"a\",}]");
  }

  @Test public void failWithPositionGreaterThanBufferSize() throws IOException {
    String spaces = repeat(' ', 8192);
    testFailWithPosition("Expected value at path $[1]",
        "[\n\n" + spaces + "\n\n\n\"a\",}]");
  }

  @Test public void failWithPositionOverSlashSlashEndOfLineComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "\n// foo\n\n//bar\r\n[\"a\",}");
  }

  @Test public void failWithPositionOverHashEndOfLineComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "\n# foo\n\n#bar\r\n[\"a\",}");
  }

  @Test public void failWithPositionOverCStyleComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "\n\n/* foo\n*\n*\r\nbar */[\"a\",}");
  }

  @Test public void failWithPositionOverQuotedString() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "[\"foo\nbar\r\nbaz\n\",\n  }");
  }

  @Test public void failWithPositionOverUnquotedString() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\n\nabcd\n\n,}");
  }

  @Test public void failWithEscapedNewlineCharacter() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\n\n\"\\\n\n\",}");
  }

  @Test @Ignore public void failWithPositionIsOffsetByBom() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "\ufeff[\"a\",}]");
  }

  private void testFailWithPosition(String message, String json) throws IOException {
    // Validate that it works reading the string normally.
    JsonReader reader1 = newReader(json);
    reader1.setLenient(true);
    reader1.beginArray();
    reader1.nextString();
    try {
      reader1.peek();
      fail();
    } catch (IOException expected) {
      assertEquals(message, expected.getMessage());
    }

    // Also validate that it works when skipping.
    JsonReader reader2 = newReader(json);
    reader2.setLenient(true);
    reader2.beginArray();
    reader2.skipValue();
    try {
      reader2.peek();
      fail();
    } catch (IOException expected) {
      assertEquals(message, expected.getMessage());
    }
  }

  @Test public void failWithPositionDeepPath() throws IOException {
    JsonReader reader = newReader("[1,{\"a\":[2,3,}");
    reader.beginArray();
    reader.nextInt();
    reader.beginObject();
    reader.nextName();
    reader.beginArray();
    reader.nextInt();
    reader.nextInt();
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
      assertEquals("Expected value at path $[1].a[2]", expected.getMessage());
    }
  }

  @Test @Ignore public void strictVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.beginArray();
    try {
      assertEquals(1d, reader.nextDouble(), 0d);
      fail();
    } catch (IOException expected) {
    }
  }

  @Test @Ignore public void lenientVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(JsonReader.Token.STRING, reader.peek());
    assertEquals(1d, reader.nextDouble(), 0d);
    reader.endArray();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void veryLongUnquotedLiteral() throws IOException {
    String literal = "a" + repeat('b', 8192) + "c";
    JsonReader reader = newReader("[" + literal + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(literal, reader.nextString());
    reader.endArray();
  }

  @Test public void deeplyNestedArrays() throws IOException {
    // this is nested 40 levels deep; Gson is tuned for nesting is 30 levels deep or fewer
    JsonReader reader = newReader(
        "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]");
    for (int i = 0; i < 40; i++) {
      reader.beginArray();
    }
    assertEquals("$[0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0]"
        + "[0][0][0][0][0][0][0][0][0][0][0][0][0][0]", reader.getPath());
    for (int i = 0; i < 40; i++) {
      reader.endArray();
    }
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void deeplyNestedObjects() throws IOException {
    // Build a JSON document structured like {"a":{"a":{"a":{"a":true}}}}, but 40 levels deep
    String array = "{\"a\":%s}";
    String json = "true";
    for (int i = 0; i < 40; i++) {
      json = String.format(array, json);
    }

    JsonReader reader = newReader(json);
    for (int i = 0; i < 40; i++) {
      reader.beginObject();
      assertEquals("a", reader.nextName());
    }
    assertEquals("$.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a"
        + ".a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a", reader.getPath());
    assertEquals(true, reader.nextBoolean());
    for (int i = 0; i < 40; i++) {
      reader.endObject();
    }
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  // http://code.google.com/p/google-gson/issues/detail?id=409
  @Test public void stringEndingInSlash() throws IOException {
    JsonReader reader = newReader("/");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void documentWithCommentEndingInSlash() throws IOException {
    JsonReader reader = newReader("/* foo *//");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void stringWithLeadingSlash() throws IOException {
    JsonReader reader = newReader("/x");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void unterminatedObject() throws IOException {
    JsonReader reader = newReader("{\"a\":\"android\"x");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals("android", reader.nextString());
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void veryLongQuotedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[\"" + string + "\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals(string, reader.nextString());
    reader.endArray();
  }

  @Test public void veryLongUnquotedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[" + string + "]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(string, reader.nextString());
    reader.endArray();
  }

  @Test public void veryLongUnterminatedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[" + string;
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(string, reader.nextString());
    try {
      reader.peek();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void skipVeryLongUnquotedString() throws IOException {
    JsonReader reader = newReader("[" + repeat('x', 8192) + "]");
    reader.setLenient(true);
    reader.beginArray();
    reader.skipValue();
    reader.endArray();
  }

  @Test public void skipTopLevelUnquotedString() throws IOException {
    JsonReader reader = newReader(repeat('x', 8192));
    reader.setLenient(true);
    reader.skipValue();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void skipVeryLongQuotedString() throws IOException {
    JsonReader reader = newReader("[\"" + repeat('x', 8192) + "\"]");
    reader.beginArray();
    reader.skipValue();
    reader.endArray();
  }

  @Test public void skipTopLevelQuotedString() throws IOException {
    JsonReader reader = newReader("\"" + repeat('x', 8192) + "\"");
    reader.setLenient(true);
    reader.skipValue();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void stringAsNumberWithTruncatedExponent() throws IOException {
    JsonReader reader = newReader("[123e]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(STRING, reader.peek());
  }

  @Test public void stringAsNumberWithDigitAndNonDigitExponent() throws IOException {
    JsonReader reader = newReader("[123e4b]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(STRING, reader.peek());
  }

  @Test public void stringAsNumberWithNonDigitExponent() throws IOException {
    JsonReader reader = newReader("[123eb]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(STRING, reader.peek());
  }

  @Test public void emptyStringName() throws IOException {
    JsonReader reader = newReader("{\"\":true}");
    reader.setLenient(true);
    assertEquals(BEGIN_OBJECT, reader.peek());
    reader.beginObject();
    assertEquals(NAME, reader.peek());
    assertEquals("", reader.nextName());
    assertEquals(JsonReader.Token.BOOLEAN, reader.peek());
    assertEquals(true, reader.nextBoolean());
    assertEquals(JsonReader.Token.END_OBJECT, reader.peek());
    reader.endObject();
    assertEquals(JsonReader.Token.END_DOCUMENT, reader.peek());
  }

  @Test public void strictExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals("b", reader.nextString());
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void lenientExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals("b", reader.nextString());
    try {
      reader.peek();
      fail();
    } catch (IOException expected) {
    }
  }

  private String repeat(char c, int count) {
    char[] array = new char[count];
    Arrays.fill(array, c);
    return new String(array);
  }

  @Test public void malformedDocuments() throws IOException {
    assertDocument("{]", BEGIN_OBJECT, IOException.class);
    assertDocument("{,", BEGIN_OBJECT, IOException.class);
    assertDocument("{{", BEGIN_OBJECT, IOException.class);
    assertDocument("{[", BEGIN_OBJECT, IOException.class);
    assertDocument("{:", BEGIN_OBJECT, IOException.class);
    assertDocument("{\"name\",", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\",", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\":}", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\"::", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\":,", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\"=}", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\"=>}", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\"=>\"string\":", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\"=>\"string\"=", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\"=>\"string\"=>", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\"=>\"string\",", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\"=>\"string\",\"name\"", BEGIN_OBJECT, NAME, STRING, NAME);
    assertDocument("[}", BEGIN_ARRAY, IOException.class);
    assertDocument("[,]", BEGIN_ARRAY, NULL, NULL, END_ARRAY);
    assertDocument("{", BEGIN_OBJECT, IOException.class);
    assertDocument("{\"name\"", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{\"name\",", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{'name'", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{'name',", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("{name", BEGIN_OBJECT, NAME, IOException.class);
    assertDocument("[", BEGIN_ARRAY, IOException.class);
    assertDocument("[string", BEGIN_ARRAY, STRING, IOException.class);
    assertDocument("[\"string\"", BEGIN_ARRAY, STRING, IOException.class);
    assertDocument("['string'", BEGIN_ARRAY, STRING, IOException.class);
    assertDocument("[123", BEGIN_ARRAY, NUMBER, IOException.class);
    assertDocument("[123,", BEGIN_ARRAY, NUMBER, IOException.class);
    assertDocument("{\"name\":123", BEGIN_OBJECT, NAME, NUMBER, IOException.class);
    assertDocument("{\"name\":123,", BEGIN_OBJECT, NAME, NUMBER, IOException.class);
    assertDocument("{\"name\":\"string\"", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\":\"string\",", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\":'string'", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\":'string',", BEGIN_OBJECT, NAME, STRING, IOException.class);
    assertDocument("{\"name\":false", BEGIN_OBJECT, NAME, BOOLEAN, IOException.class);
    assertDocument("{\"name\":false,,", BEGIN_OBJECT, NAME, BOOLEAN, IOException.class);
  }

  /**
   * This test behave slightly differently in Gson 2.2 and earlier. It fails
   * during peek rather than during nextString().
   */
  @Test public void unterminatedStringFailure() throws IOException {
    JsonReader reader = newReader("[\"string");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(JsonReader.Token.STRING, reader.peek());
    try {
      reader.nextString();
      fail();
    } catch (IOException expected) {
    }
  }

  private void assertDocument(String document, Object... expectations) throws IOException {
    JsonReader reader = newReader(document);
    reader.setLenient(true);
    for (Object expectation : expectations) {
      if (expectation == BEGIN_OBJECT) {
        reader.beginObject();
      } else if (expectation == BEGIN_ARRAY) {
        reader.beginArray();
      } else if (expectation == END_OBJECT) {
        reader.endObject();
      } else if (expectation == END_ARRAY) {
        reader.endArray();
      } else if (expectation == NAME) {
        assertEquals("name", reader.nextName());
      } else if (expectation == BOOLEAN) {
        assertEquals(false, reader.nextBoolean());
      } else if (expectation == STRING) {
        assertEquals("string", reader.nextString());
      } else if (expectation == NUMBER) {
        assertEquals(123, reader.nextInt());
      } else if (expectation == NULL) {
        reader.nextNull();
      } else if (expectation == IOException.class) {
        try {
          reader.peek();
          fail();
        } catch (IOException expected) {
        }
      } else {
        throw new AssertionError();
      }
    }
  }
}
