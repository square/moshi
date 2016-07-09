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
import static org.junit.Assert.fail;

public final class BufferedSourceJsonReaderTest {
  @Test public void readingDoesNotBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{}{}");

    JsonReader reader1 = JsonReader.of(buffer);
    reader1.beginObject();
    reader1.endObject();
    assertThat(buffer.size()).isEqualTo(2);

    JsonReader reader2 = JsonReader.of(buffer);
    reader2.beginObject();
    reader2.endObject();
    assertThat(buffer.size()).isEqualTo(0);
  }

  @Test public void readArray() throws IOException {
    JsonReader reader = newReader("[true, true]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isTrue();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readEmptyArray() throws IOException {
    JsonReader reader = newReader("[]");
    reader.beginArray();
    assertThat(reader.hasNext()).isFalse();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readObject() throws IOException {
    JsonReader reader = newReader("{\"a\": \"android\", \"b\": \"banana\"}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextString()).isEqualTo("banana");
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readObjectBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = JsonReader.of(buffer);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextString()).isEqualTo("banana");
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readObjectSource() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = JsonReader.of(buffer);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextString()).isEqualTo("banana");
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readEmptyObject() throws IOException {
    JsonReader reader = newReader("{}");
    reader.beginObject();
    assertThat(reader.hasNext()).isFalse();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipArray() throws IOException {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipArrayAfterPeek() throws Exception {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.peek()).isEqualTo(BEGIN_ARRAY);
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipTopLevelObject() throws Exception {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.skipValue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipObject() throws IOException {
    JsonReader reader = newReader(
        "{\"a\": { \"c\": [], \"d\": [true, true, {}] }, \"b\": \"banana\"}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipObjectAfterPeek() throws Exception {
    String json = "{" + "  \"one\": { \"num\": 1 }"
        + ", \"two\": { \"num\": 2 }" + ", \"three\": { \"num\": 3 }" + "}";
    JsonReader reader = newReader(json);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("one");
    assertThat(reader.peek()).isEqualTo(BEGIN_OBJECT);
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("two");
    assertThat(reader.peek()).isEqualTo(BEGIN_OBJECT);
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("three");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipInteger() throws IOException {
    JsonReader reader = newReader("{\"a\":123456789,\"b\":-123456789}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipDouble() throws IOException {
    JsonReader reader = newReader("{\"a\":-123.456e-789,\"b\":123456789.0}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void failOnUnknownFailsOnUnknownObjectValue() throws IOException {
    JsonReader reader = newReader("{\"a\": 123}");
    reader.setFailOnUnknown(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Cannot skip unexpected NUMBER at $.a");
    }
    // Confirm that the reader is left in a consistent state after the exception.
    reader.setFailOnUnknown(false);
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void failOnUnknownFailsOnUnknownArrayElement() throws IOException {
    JsonReader reader = newReader("[\"a\", 123]");
    reader.setFailOnUnknown(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Cannot skip unexpected NUMBER at $[1]");
    }
    // Confirm that the reader is left in a consistent state after the exception.
    reader.setFailOnUnknown(false);
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void helloWorld() throws IOException {
    String json = "{\n" +
        "   \"hello\": true,\n" +
        "   \"foo\": [\"world\"]\n" +
        "}";
    JsonReader reader = newReader(json);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("hello");
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextName()).isEqualTo("foo");
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("world");
    reader.endArray();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void nullSource() {
    try {
      JsonReader.of(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void emptyString() throws Exception {
    try {
      newReader("").beginArray();
      fail();
    } catch (EOFException expected) {
    }
    try {
      newReader("").beginObject();
      fail();
    } catch (EOFException expected) {
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
    assertThat(reader.nextString()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("a\"");
    assertThat(reader.nextString()).isEqualTo("\"");
    assertThat(reader.nextString()).isEqualTo(":");
    assertThat(reader.nextString()).isEqualTo(",");
    assertThat(reader.nextString()).isEqualTo("\b");
    assertThat(reader.nextString()).isEqualTo("\f");
    assertThat(reader.nextString()).isEqualTo("\n");
    assertThat(reader.nextString()).isEqualTo("\r");
    assertThat(reader.nextString()).isEqualTo("\t");
    assertThat(reader.nextString()).isEqualTo(" ");
    assertThat(reader.nextString()).isEqualTo("\\");
    assertThat(reader.nextString()).isEqualTo("{");
    assertThat(reader.nextString()).isEqualTo("}");
    assertThat(reader.nextString()).isEqualTo("[");
    assertThat(reader.nextString()).isEqualTo("]");
    assertThat(reader.nextString()).isEqualTo("\0");
    assertThat(reader.nextString()).isEqualTo("\u0019");
    assertThat(reader.nextString()).isEqualTo("\u20AC");
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void unescapingInvalidCharacters() throws IOException {
    String json = "[\"\\u000g\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void unescapingTruncatedCharacters() throws IOException {
    String json = "[\"\\u000";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void unescapingTruncatedSequence() throws IOException {
    String json = "[\"\\";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void integersWithFractionalPartSpecified() throws IOException {
    JsonReader reader = newReader("[1.0,1.0,1.0]");
    reader.beginArray();
    assertThat(reader.nextDouble()).isEqualTo(1.0);
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.nextLong()).isEqualTo(1L);
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
    assertThat(reader.nextDouble()).isEqualTo(-0.0);
    assertThat(reader.nextDouble()).isEqualTo(1.0);
    assertThat(reader.nextDouble()).isEqualTo(1.7976931348623157E308);
    assertThat(reader.nextDouble()).isEqualTo(4.9E-324);
    assertThat(reader.nextDouble()).isEqualTo(0.0);
    assertThat(reader.nextDouble()).isEqualTo(-0.5);
    assertThat(reader.nextDouble()).isEqualTo(2.2250738585072014E-308);
    assertThat(reader.nextDouble()).isEqualTo(3.141592653589793);
    assertThat(reader.nextDouble()).isEqualTo(2.718281828459045);
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictNonFiniteDoubles() throws IOException {
    String json = "[NaN]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictQuotedNonFiniteDoubles() throws IOException {
    String json = "[\"NaN\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonEncodingException expected) {
      assertThat(expected).hasMessageContaining("NaN");
    }
  }

  @Test public void lenientNonFiniteDoubles() throws IOException {
    String json = "[NaN, -Infinity, Infinity]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertThat(Double.isNaN(reader.nextDouble())).isTrue();
    assertThat(reader.nextDouble()).isEqualTo(Double.NEGATIVE_INFINITY);
    assertThat(reader.nextDouble()).isEqualTo(Double.POSITIVE_INFINITY);
    reader.endArray();
  }

  @Test public void lenientQuotedNonFiniteDoubles() throws IOException {
    String json = "[\"NaN\", \"-Infinity\", \"Infinity\"]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextDouble()).isNaN();
    assertThat(reader.nextDouble()).isEqualTo(Double.NEGATIVE_INFINITY);
    assertThat(reader.nextDouble()).isEqualTo(Double.POSITIVE_INFINITY);
    reader.endArray();
  }

  @Test public void strictNonFiniteDoublesWithSkipValue() throws IOException {
    String json = "[NaN]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
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
    assertThat(reader.nextLong()).isEqualTo(0L);
    assertThat(reader.nextInt()).isEqualTo(0);
    assertThat(reader.nextDouble()).isEqualTo(0.0d);
    assertThat(reader.nextLong()).isEqualTo(1L);
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.nextDouble()).isEqualTo(1.0d);
    assertThat(reader.nextLong()).isEqualTo(-1L);
    assertThat(reader.nextInt()).isEqualTo(-1);
    assertThat(reader.nextDouble()).isEqualTo(-1.0d);
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextLong()).isEqualTo(Long.MIN_VALUE);
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextLong()).isEqualTo(Long.MAX_VALUE);
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test @Ignore public void numberWithOctalPrefix() throws IOException {
    String json = "[01]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
    try {
      reader.nextInt();
      fail();
    } catch (JsonEncodingException expected) {
    }
    try {
      reader.nextLong();
      fail();
    } catch (JsonEncodingException expected) {
    }
    try {
      reader.nextDouble();
      fail();
    } catch (JsonEncodingException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("01");
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void booleans() throws IOException {
    JsonReader reader = newReader("[true,false]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isFalse();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void peekingUnquotedStringsPrefixedWithBooleans() throws IOException {
    JsonReader reader = newReader("[truey]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("truey");
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
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo(s);
    reader.endArray();
  }

  @Test public void peekingUnquotedStringsPrefixedWithIntegers() throws IOException {
    JsonReader reader = newReader("[12.34e5x]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("12.34e5x");
  }

  @Test public void peekLongMinValue() throws IOException {
    JsonReader reader = newReader("[-9223372036854775808]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    assertThat(reader.nextLong()).isEqualTo(-9223372036854775808L);
  }

  @Test public void peekLongMaxValue() throws IOException {
    JsonReader reader = newReader("[9223372036854775807]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    assertThat(reader.nextLong()).isEqualTo(9223372036854775807L);
  }

  @Test public void longLargerThanMaxLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test public void longLargerThanMinLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[-22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
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
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
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
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextDouble()).isEqualTo(-9223372036854775809d);
  }

  /**
   * This test fails because there's no double for 9223372036854775806, and
   * our long parsing uses Double.parseDouble() for fractional values.
   */
  @Test @Ignore public void highPrecisionLong() throws IOException {
    String json = "[9223372036854775806.000]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertThat(reader.nextLong()).isEqualTo(9223372036854775806L);
    reader.endArray();
  }

  @Test public void peekMuchLargerThanLongMinValue() throws IOException {
    JsonReader reader = newReader("[-92233720368547758080]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextDouble()).isEqualTo(-92233720368547758080d);
  }

  @Test public void quotedNumberWithEscape() throws IOException {
    JsonReader reader = newReader("[\"12\u00334\"]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
    assertThat(reader.nextInt()).isEqualTo(1234);
  }

  @Test public void mixedCaseLiterals() throws IOException {
    JsonReader reader = newReader("[True,TruE,False,FALSE,NULL,nulL]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isFalse();
    assertThat(reader.nextBoolean()).isFalse();
    reader.nextNull();
    reader.nextNull();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void missingValue() throws IOException {
    JsonReader reader = newReader("{\"a\":}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void prematureEndOfInput() throws IOException {
    JsonReader reader = newReader("{\"a\":true,");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.nextName();
      fail();
    } catch (EOFException expected) {
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
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.beginArray();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.endArray();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.beginObject();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.endObject();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.beginArray();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.endArray();
      fail();
    } catch (JsonDataException expected) {
    }
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
    reader.close();
  }

  @Test public void integerMismatchWithDoubleDoesNotAdvance() throws IOException {
    JsonReader reader = newReader("[1.5]");
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextDouble()).isEqualTo(1.5d);
    reader.endArray();
  }

  @Test public void integerMismatchWithLongDoesNotAdvance() throws IOException {
    JsonReader reader = newReader("[9223372036854775807]");
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextLong()).isEqualTo(9223372036854775807L);
    reader.endArray();
  }

  @Test public void longMismatchWithDoubleDoesNotAdvance() throws IOException {
    JsonReader reader = newReader("[1.5]");
    reader.beginArray();
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextDouble()).isEqualTo(1.5d);
    reader.endArray();
  }

  @Test public void stringNullIsNotNull() throws IOException {
    JsonReader reader = newReader("[\"null\"]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test public void nullLiteralIsNotAString() throws IOException {
    JsonReader reader = newReader("[null]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test public void strictNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("{\"a\"=>true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();
  }

  @Test public void strictNameValueSeparatorWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void commentsInStringValue() throws Exception {
    JsonReader reader = newReader("[\"// comment\"]");
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("// comment");
    reader.endArray();

    reader = newReader("{\"a\":\"#someComment\"}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("#someComment");
    reader.endObject();

    reader = newReader("{\"#//a\":\"#some //Comment\"}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("#//a");
    assertThat(reader.nextString()).isEqualTo("#some //Comment");
    reader.endObject();
  }

  @Test public void strictComments() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[# comment \n true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[/* comment */ true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientComments() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("[# comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("[/* comment */ true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("a//");
    reader.setLenient(true);
    assertThat(reader.nextString()).isEqualTo("a");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictCommentsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[# comment \n true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[/* comment */ true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
  }

  @Test public void jsonIsSingleUnquotedString() throws IOException {
    JsonReader reader = newReader("abc");
    reader.setLenient(true);
    assertThat(reader.nextString()).isEqualTo("abc");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictUnquotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
  }

  @Test public void strictSingleQuotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictUnquotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
  }

  @Test public void lenientUnquotedStringsDelimitedByComment() throws IOException {
    JsonReader reader = newReader("[a#comment\n]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
    reader.endArray();
  }

  @Test public void strictSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
  }

  @Test public void strictSingleQuotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isTrue();
  }

  @Test public void strictSemicolonDelimitedArrayWithSkipValue() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextBoolean();
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextName()).isEqualTo("b");
  }

  @Test public void strictSemicolonDelimitedNameValuePairWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,true]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[true,]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    reader.nextNull();
    assertThat(reader.nextBoolean()).isTrue();
    reader.endArray();

    reader = newReader("[,true]");
    reader.setLenient(true);
    reader.beginArray();
    reader.nextNull();
    assertThat(reader.nextBoolean()).isTrue();
    reader.endArray();

    reader = newReader("[true,]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
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
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[true,]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] true {}");
    reader.setLenient(true);
    reader.beginArray();
    reader.endArray();
    assertThat(reader.nextBoolean()).isTrue();
    reader.beginObject();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictMultipleTopLevelValuesWithSkipValue() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void topLevelValueTypes() throws IOException {
    JsonReader reader1 = newReader("true");
    assertThat(reader1.nextBoolean()).isTrue();
    assertThat(reader1.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader2 = newReader("false");
    assertThat(reader2.nextBoolean()).isFalse();
    assertThat(reader2.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader3 = newReader("null");
    assertThat(reader3.nextNull()).isNull();
    assertThat(reader3.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader4 = newReader("123");
    assertThat(reader4.nextInt()).isEqualTo(123);
    assertThat(reader4.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader5 = newReader("123.4");
    assertThat(reader5.nextDouble()).isEqualTo(123.4);
    assertThat(reader5.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader6 = newReader("\"a\"");
    assertThat(reader6.nextString()).isEqualTo("a");
    assertThat(reader6.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void topLevelValueTypeWithSkipValue() throws IOException {
    JsonReader reader = newReader("true");
    reader.skipValue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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
    } catch (JsonEncodingException expected) {
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
    } catch (JsonEncodingException expected) {
      assertThat(expected).hasMessage(message);
    }

    // Also validate that it works when skipping.
    JsonReader reader2 = newReader(json);
    reader2.setLenient(true);
    reader2.beginArray();
    reader2.skipValue();
    try {
      reader2.peek();
      fail();
    } catch (JsonEncodingException expected) {
      assertThat(expected).hasMessage(message);
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
    } catch (JsonEncodingException expected) {
      assertThat(expected).hasMessage("Expected value at path $[1].a[2]");
    }
  }

  @Test @Ignore public void strictVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.beginArray();
    try {
      assertThat(reader.nextDouble()).isEqualTo(1d);
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test @Ignore public void lenientVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextDouble()).isEqualTo(1d);
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void veryLongUnquotedLiteral() throws IOException {
    String literal = "a" + repeat('b', 8192) + "c";
    JsonReader reader = newReader("[" + literal + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo(literal);
    reader.endArray();
  }

  @Test public void deeplyNestedArrays() throws IOException {
    // this is nested 40 levels deep; Gson is tuned for nesting is 30 levels deep or fewer
    JsonReader reader = newReader(
        "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]");
    for (int i = 0; i < 40; i++) {
      reader.beginArray();
    }
    assertThat(reader.getPath()).isEqualTo(
        "$[0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0]"
        + "[0][0][0][0][0][0][0][0][0][0][0][0][0][0]");
    for (int i = 0; i < 40; i++) {
      reader.endArray();
    }
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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
      assertThat(reader.nextName()).isEqualTo("a");
    }
    assertThat(reader.getPath()).isEqualTo("$.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a"
        + ".a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a");
    assertThat(reader.nextBoolean()).isTrue();
    for (int i = 0; i < 40; i++) {
      reader.endObject();
    }
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  // http://code.google.com/p/google-gson/issues/detail?id=409
  @Test public void stringEndingInSlash() throws IOException {
    JsonReader reader = newReader("/");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void documentWithCommentEndingInSlash() throws IOException {
    JsonReader reader = newReader("/* foo *//");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void stringWithLeadingSlash() throws IOException {
    JsonReader reader = newReader("/x");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void unterminatedObject() throws IOException {
    JsonReader reader = newReader("{\"a\":\"android\"x");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void veryLongQuotedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[\"" + string + "\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo(string);
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
    assertThat(reader.nextString()).isEqualTo(string);
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
    assertThat(reader.nextString()).isEqualTo(string);
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
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void stringAsNumberWithTruncatedExponent() throws IOException {
    JsonReader reader = newReader("[123e]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
  }

  @Test public void stringAsNumberWithDigitAndNonDigitExponent() throws IOException {
    JsonReader reader = newReader("[123e4b]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
  }

  @Test public void stringAsNumberWithNonDigitExponent() throws IOException {
    JsonReader reader = newReader("[123eb]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
  }

  @Test public void emptyStringName() throws IOException {
    JsonReader reader = newReader("{\"\":true}");
    reader.setLenient(true);
    assertThat(reader.peek()).isEqualTo(BEGIN_OBJECT);
    reader.beginObject();
    assertThat(reader.peek()).isEqualTo(NAME);
    assertThat(reader.nextName()).isEqualTo("");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BOOLEAN);
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_OBJECT);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("b");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("b");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  private String repeat(char c, int count) {
    char[] array = new char[count];
    Arrays.fill(array, c);
    return new String(array);
  }

  @Test public void malformedDocuments() throws IOException {
    assertDocument("{]", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{,", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{{", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{[", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{:", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{\"name\",", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\":}", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\"::", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\":,", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\"=}", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\"=>}", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\"=>\"string\":", BEGIN_OBJECT, NAME, STRING, JsonEncodingException.class);
    assertDocument("{\"name\"=>\"string\"=", BEGIN_OBJECT, NAME, STRING, JsonEncodingException.class);
    assertDocument("{\"name\"=>\"string\"=>", BEGIN_OBJECT, NAME, STRING, JsonEncodingException.class);
    assertDocument("{\"name\"=>\"string\",", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\"=>\"string\",\"name\"", BEGIN_OBJECT, NAME, STRING, NAME);
    assertDocument("[}", BEGIN_ARRAY, JsonEncodingException.class);
    assertDocument("[,]", BEGIN_ARRAY, NULL, NULL, END_ARRAY);
    assertDocument("{", BEGIN_OBJECT, EOFException.class);
    assertDocument("{\"name\"", BEGIN_OBJECT, NAME, EOFException.class);
    assertDocument("{\"name\",", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{'name'", BEGIN_OBJECT, NAME, EOFException.class);
    assertDocument("{'name',", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{name", BEGIN_OBJECT, NAME, EOFException.class);
    assertDocument("[", BEGIN_ARRAY, EOFException.class);
    assertDocument("[string", BEGIN_ARRAY, STRING, EOFException.class);
    assertDocument("[\"string\"", BEGIN_ARRAY, STRING, EOFException.class);
    assertDocument("['string'", BEGIN_ARRAY, STRING, EOFException.class);
    assertDocument("[123", BEGIN_ARRAY, NUMBER, EOFException.class);
    assertDocument("[123,", BEGIN_ARRAY, NUMBER, EOFException.class);
    assertDocument("{\"name\":123", BEGIN_OBJECT, NAME, NUMBER, EOFException.class);
    assertDocument("{\"name\":123,", BEGIN_OBJECT, NAME, NUMBER, EOFException.class);
    assertDocument("{\"name\":\"string\"", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":\"string\",", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":'string'", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":'string',", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":false", BEGIN_OBJECT, NAME, BOOLEAN, EOFException.class);
    assertDocument("{\"name\":false,,", BEGIN_OBJECT, NAME, BOOLEAN, JsonEncodingException.class);
  }

  /**
   * This test behave slightly differently in Gson 2.2 and earlier. It fails
   * during peek rather than during nextString().
   */
  @Test public void unterminatedStringFailure() throws IOException {
    JsonReader reader = newReader("[\"string");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void invalidEscape() throws IOException {
    JsonReader reader = newReader("[\"str\\ing\"]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
      assertThat(expected).hasMessage("Invalid escape sequence: \\i at path $[0]");
    }
  }

  @Test public void lenientInvalidEscape() throws IOException {
    JsonReader reader = newReader("[\"str\\ing\"]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("string");
  }

  @Test public void selectName() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("{\"a\": 5, \"b\": 5, \"c\": 5, \"d\": 5}");
    reader.beginObject();
    assertEquals("$.", reader.getPath());

    assertEquals(0, reader.selectName(abc));
    assertEquals("$.a", reader.getPath());
    assertEquals(5, reader.nextInt());
    assertEquals("$.a", reader.getPath());

    assertEquals(1, reader.selectName(abc));
    assertEquals("$.b", reader.getPath());
    assertEquals(5, reader.nextInt());
    assertEquals("$.b", reader.getPath());

    assertEquals(2, reader.selectName(abc));
    assertEquals("$.c", reader.getPath());
    assertEquals(5, reader.nextInt());
    assertEquals("$.c", reader.getPath());

    // A missed selectName() doesn't advance anything, not even the path.
    assertEquals(-1, reader.selectName(abc));
    assertEquals("$.c", reader.getPath());
    assertEquals(JsonReader.Token.NAME, reader.peek());

    assertEquals("d", reader.nextName());
    assertEquals("$.d", reader.getPath());
    assertEquals(5, reader.nextInt());
    assertEquals("$.d", reader.getPath());

    reader.endObject();
  }

  @Test public void selectString() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[\"a\", \"b\", \"c\", \"d\"]");
    reader.beginArray();
    assertEquals("$[0]", reader.getPath());

    assertEquals(0, reader.selectString(abc));
    assertEquals("$[1]", reader.getPath());

    assertEquals(1, reader.selectString(abc));
    assertEquals("$[2]", reader.getPath());

    assertEquals(2, reader.selectString(abc));
    assertEquals("$[3]", reader.getPath());

    // A missed selectName() doesn't advance anything, not even the path.
    assertEquals(-1, reader.selectString(abc));
    assertEquals("$[3]", reader.getPath());
    assertEquals(JsonReader.Token.STRING, reader.peek());

    assertEquals("d", reader.nextString());
    assertEquals("$[4]", reader.getPath());

    reader.endArray();
  }

  /** Select doesn't match unquoted strings. */
  @Test public void selectStringUnquoted() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[a]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(-1, reader.selectString(abc));
    assertEquals("a", reader.nextString());
    reader.endArray();
  }

  /** Select doesn't match single quoted strings. */
  @Test public void selectStringSingleQuoted() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("['a']");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(-1, reader.selectString(abc));
    assertEquals("a", reader.nextString());
    reader.endArray();
  }

  /** Select doesn't match unnecessarily-escaped strings. */
  @Test public void selectUnnecessaryEscaping() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[\"\\u0061\"]");
    reader.beginArray();
    assertEquals(-1, reader.selectString(abc));
    assertEquals("a", reader.nextString());
    reader.endArray();
  }

  /** Select does match necessarily escaping. The decoded value is used in the path. */
  @Test public void selectNecessaryEscaping() throws IOException {
    JsonReader.Options options = JsonReader.Options.of("\n", "\u0000", "\"");

    JsonReader reader = newReader("{\"\\n\": 5,\"\\u0000\": 5, \"\\\"\": 5}");
    reader.beginObject();
    assertEquals(0, reader.selectName(options));
    assertEquals(5, reader.nextInt());
    assertEquals("$.\n", reader.getPath());
    assertEquals(1, reader.selectName(options));
    assertEquals(5, reader.nextInt());
    assertEquals("$.\u0000", reader.getPath());
    assertEquals(2, reader.selectName(options));
    assertEquals(5, reader.nextInt());
    assertEquals("$.\"", reader.getPath());
    reader.endObject();
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
        assertThat(reader.nextName()).isEqualTo("name");
      } else if (expectation == BOOLEAN) {
        assertThat(reader.nextBoolean()).isFalse();
      } else if (expectation == STRING) {
        assertThat(reader.nextString()).isEqualTo("string");
      } else if (expectation == NUMBER) {
        assertThat(reader.nextInt()).isEqualTo(123);
      } else if (expectation == NULL) {
        reader.nextNull();
      } else if (expectation instanceof Class
          && Exception.class.isAssignableFrom((Class<?>) expectation)) {
        try {
          reader.peek();
          fail();
        } catch (Exception expected) {
          assertEquals(expected.toString(), expectation, expected.getClass());
        }
      } else {
        throw new AssertionError();
      }
    }
  }
}
