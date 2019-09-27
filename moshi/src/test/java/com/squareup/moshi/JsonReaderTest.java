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
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static com.squareup.moshi.JsonReader.Token.BEGIN_ARRAY;
import static com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT;
import static com.squareup.moshi.JsonReader.Token.NAME;
import static com.squareup.moshi.JsonReader.Token.STRING;
import static com.squareup.moshi.TestUtil.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
@SuppressWarnings("CheckReturnValue")
public final class JsonReaderTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
  }

  JsonReader newReader(String json) throws IOException {
    return factory.newReader(json);
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

  @Test public void longs() throws IOException {
    assumeTrue(factory.implementsStrictPrecision());

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

  @Test public void booleans() throws IOException {
    JsonReader reader = newReader("[true,false]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isFalse();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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
    assumeTrue(factory.implementsStrictPrecision());

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
    assumeTrue(factory.implementsStrictPrecision());

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
    assumeTrue(factory.implementsStrictPrecision());

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

  @Test public void deeplyNestedArrays() throws IOException {
    JsonReader reader = newReader("[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]");
    for (int i = 0; i < 31; i++) {
      reader.beginArray();
    }
    assertThat(reader.getPath()).isEqualTo("$[0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0]"
        + "[0][0][0][0][0][0][0][0][0][0][0][0][0]");
    for (int i = 0; i < 31; i++) {
      reader.endArray();
    }
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void deeplyNestedObjects() throws IOException {
    // Build a JSON document structured like {"a":{"a":{"a":{"a":true}}}}, but 31 levels deep.
    String array = "{\"a\":%s}";
    String json = "true";
    for (int i = 0; i < 31; i++) {
      json = String.format(array, json);
    }

    JsonReader reader = newReader(json);
    for (int i = 0; i < 31; i++) {
      reader.beginObject();
      assertThat(reader.nextName()).isEqualTo("a");
    }
    assertThat(reader.getPath())
        .isEqualTo("$.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a");
    assertThat(reader.nextBoolean()).isTrue();
    for (int i = 0; i < 31; i++) {
      reader.endObject();
    }
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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

  @Test public void validEscapes() throws IOException {
    JsonReader reader = newReader("[\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"]");
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("\"\\/\b\f\n\r\t");
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

  /** Select does match necessarily escaping. The decoded value is used in the path. */
  @Test public void selectNameNecessaryEscaping() throws IOException {
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

  /** Select removes unnecessary escaping from the source JSON. */
  @Test public void selectNameUnnecessaryEscaping() throws IOException {
    JsonReader.Options options = JsonReader.Options.of("coffee", "tea");

    JsonReader reader = newReader("{\"cof\\u0066ee\":5, \"\\u0074e\\u0061\":4, \"water\":3}");
    reader.beginObject();
    assertEquals(0, reader.selectName(options));
    assertEquals(5, reader.nextInt());
    assertEquals("$.coffee", reader.getPath());
    assertEquals(1, reader.selectName(options));
    assertEquals(4, reader.nextInt());
    assertEquals("$.tea", reader.getPath());

    // Ensure select name doesn't advance the stack in case there are no matches.
    assertEquals(-1, reader.selectName(options));
    assertEquals(JsonReader.Token.NAME, reader.peek());
    assertEquals("$.tea", reader.getPath());

    // Consume the last token.
    assertEquals("water", reader.nextName());
    assertEquals(3, reader.nextInt());
    reader.endObject();
  }

  @Test public void selectNameUnquoted() throws Exception {
    JsonReader.Options options = JsonReader.Options.of("a", "b");

    JsonReader reader = newReader("{a:2}");
    reader.setLenient(true);
    reader.beginObject();

    assertEquals(0, reader.selectName(options));
    assertEquals("$.a", reader.getPath());
    assertEquals(2, reader.nextInt());
    assertEquals("$.a", reader.getPath());

    reader.endObject();
  }

  @Test public void selectNameSingleQuoted() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b");

    JsonReader reader = newReader("{'a':5}");
    reader.setLenient(true);
    reader.beginObject();

    assertEquals(0, reader.selectName(abc));
    assertEquals("$.a", reader.getPath());
    assertEquals(5, reader.nextInt());
    assertEquals("$.a", reader.getPath());

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

  @Test public void selectStringNecessaryEscaping() throws Exception {
    JsonReader.Options options = JsonReader.Options.of("\n", "\u0000", "\"");

    JsonReader reader = newReader("[\"\\n\",\"\\u0000\", \"\\\"\"]");
    reader.beginArray();
    assertEquals(0, reader.selectString(options));
    assertEquals(1, reader.selectString(options));
    assertEquals(2, reader.selectString(options));
    reader.endArray();
  }

  /** Select strips unnecessarily-escaped strings. */
  @Test public void selectStringUnnecessaryEscaping() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[\"\\u0061\", \"b\", \"\\u0063\"]");
    reader.beginArray();
    assertEquals(0, reader.selectString(abc));
    assertEquals(1, reader.selectString(abc));
    assertEquals(2, reader.selectString(abc));
    reader.endArray();
  }

  @Test public void selectStringUnquoted() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[a, \"b\", c]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(0, reader.selectString(abc));
    assertEquals(1, reader.selectString(abc));
    assertEquals(2, reader.selectString(abc));
    reader.endArray();
  }

  @Test public void selectStringSingleQuoted() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("['a', \"b\", c]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(0, reader.selectString(abc));
    assertEquals(1, reader.selectString(abc));
    assertEquals(2, reader.selectString(abc));
    reader.endArray();
  }

  @Test public void selectStringMaintainsReaderState() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[\"\\u0061\", \"42\"]");
    reader.beginArray();
    assertEquals(0, reader.selectString(abc));
    assertEquals(-1, reader.selectString(abc));
    assertEquals(JsonReader.Token.STRING, reader.peek());
    // Next long can retrieve a value from a buffered string.
    assertEquals(42, reader.nextLong());
    reader.endArray();
  }

  @Test public void selectStringWithoutString() throws IOException {
    JsonReader.Options numbers = JsonReader.Options.of("1", "2.0", "true", "4");

    JsonReader reader = newReader("[0, 2.0, true, \"4\"]");
    reader.beginArray();
    assertThat(reader.selectString(numbers)).isEqualTo(-1);
    reader.skipValue();
    assertThat(reader.selectString(numbers)).isEqualTo(-1);
    reader.skipValue();
    assertThat(reader.selectString(numbers)).isEqualTo(-1);
    reader.skipValue();
    assertThat(reader.selectString(numbers)).isEqualTo(3);
    reader.endArray();
  }

  @Test public void stringToNumberCoersion() throws Exception {
    JsonReader reader = newReader("[\"0\", \"9223372036854775807\", \"1.5\"]");
    reader.beginArray();
    assertThat(reader.nextInt()).isEqualTo(0);
    assertThat(reader.nextLong()).isEqualTo(9223372036854775807L);
    assertThat(reader.nextDouble()).isEqualTo(1.5d);
    reader.endArray();
  }

  @Test public void unnecessaryPrecisionNumberCoersion() throws Exception {
    JsonReader reader = newReader("[\"0.0\", \"9223372036854775807.0\"]");
    reader.beginArray();
    assertThat(reader.nextInt()).isEqualTo(0);
    assertThat(reader.nextLong()).isEqualTo(9223372036854775807L);
    reader.endArray();
  }

  @Test public void nanInfinityDoubleCoersion() throws Exception {
    JsonReader reader = newReader("[\"NaN\", \"Infinity\", \"-Infinity\"]");
    reader.beginArray();
    reader.setLenient(true);
    assertThat(reader.nextDouble()).isNaN();
    assertThat(reader.nextDouble()).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(reader.nextDouble()).isEqualTo(Double.NEGATIVE_INFINITY);
    reader.endArray();
  }

  @Test public void intMismatchWithStringDoesNotAdvance() throws Exception {
    JsonReader reader = newReader("[\"a\"]");
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("a");
    reader.endArray();
  }

  @Test public void longMismatchWithStringDoesNotAdvance() throws Exception {
    JsonReader reader = newReader("[\"a\"]");
    reader.beginArray();
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("a");
    reader.endArray();
  }

  @Test public void doubleMismatchWithStringDoesNotAdvance() throws Exception {
    JsonReader reader = newReader("[\"a\"]");
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("a");
    reader.endArray();
  }

  @Test public void readJsonValueInt() throws IOException {
    JsonReader reader = newReader("1");
    Object value = reader.readJsonValue();
    assertThat(value).isEqualTo(1.0);
  }

  @Test public void readJsonValueMap() throws IOException {
    JsonReader reader = newReader("{\"hello\": \"world\"}");
    Object value = reader.readJsonValue();
    assertThat(value).isEqualTo(Collections.singletonMap("hello", "world"));
  }

  @Test public void readJsonValueList() throws IOException {
    JsonReader reader = newReader("[\"a\", \"b\"]");
    Object value = reader.readJsonValue();
    assertThat(value).isEqualTo(Arrays.asList("a", "b"));
  }

  @Test public void readJsonValueListMultipleTypes() throws IOException {
    JsonReader reader = newReader("[\"a\", 5, false]");
    Object value = reader.readJsonValue();
    assertThat(value).isEqualTo(Arrays.asList("a", 5.0, false));
  }

  @Test public void readJsonValueNestedListInMap() throws IOException {
    JsonReader reader = newReader("{\"pizzas\": [\"cheese\", \"pepperoni\"]}");
    Object value = reader.readJsonValue();
    assertThat(value).isEqualTo(
        Collections.singletonMap("pizzas", Arrays.asList("cheese", "pepperoni")));
  }

  @Test public void skipName() throws IOException {
    JsonReader reader = newReader("{\"a\":1}");
    reader.beginObject();
    reader.skipName();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NUMBER);
    reader.skipValue();
    reader.endObject();
  }

  @Test public void skipNameFailUnknown() throws IOException {
    JsonReader reader = newReader("{\"a\":1,\"b\":2}");
    reader.setFailOnUnknown(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(1, reader.nextInt());
    try {
      reader.skipName();
      fail();
    } catch (JsonDataException e) {
      assertThat(e).hasMessage("Cannot skip unexpected NAME at $.b");
    }
  }

  @Test public void skipNameOnValueFails() throws IOException {
    JsonReader reader = newReader("1");
    try {
      reader.skipName();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextInt()).isEqualTo(1);
  }

  @Test public void emptyDocumentHasNextReturnsFalse() throws IOException {
    JsonReader reader = newReader("1");
    reader.readJsonValue();
    assertThat(reader.hasNext()).isFalse();
  }

  @Test public void skipValueAtEndOfObjectFails() throws IOException {
    JsonReader reader = newReader("{}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Expected a value but was END_OBJECT at path $.");
    }
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipValueAtEndOfArrayFails() throws IOException {
    JsonReader reader = newReader("[]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Expected a value but was END_ARRAY at path $[0]");
    }
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipValueAtEndOfDocumentFails() throws IOException {
    JsonReader reader = newReader("1");
    reader.nextInt();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Expected a value but was END_DOCUMENT at path $");
    }
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void basicPeekJson() throws IOException {
    JsonReader reader = newReader("{\"a\":12,\"b\":[34,56],\"c\":78}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextInt()).isEqualTo(12);
    assertThat(reader.nextName()).isEqualTo("b");
    reader.beginArray();
    assertThat(reader.nextInt()).isEqualTo(34);

    // Peek.
    JsonReader peekReader = reader.peekJson();
    assertThat(peekReader.nextInt()).isEqualTo(56);
    peekReader.endArray();
    assertThat(peekReader.nextName()).isEqualTo("c");
    assertThat(peekReader.nextInt()).isEqualTo(78);
    peekReader.endObject();
    assertThat(peekReader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    // Read again.
    assertThat(reader.nextInt()).isEqualTo(56);
    reader.endArray();
    assertThat(reader.nextName()).isEqualTo("c");
    assertThat(reader.nextInt()).isEqualTo(78);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  /**
   * We have a document that requires 12 operations to read. We read it step-by-step with one real
   * reader. Before each of the real reader’s operations we create a peeking reader and let it read
   * the rest of the document.
   */
  @Test public void peekJsonReader() throws IOException {
    JsonReader reader = newReader("[12,34,{\"a\":56,\"b\":78},90]");
    for (int i = 0; i < 12; i++) {
      readPeek12Steps(reader.peekJson(), i, 12);
      readPeek12Steps(reader, i, i + 1);
    }
  }

  /**
   * Read a fragment of {@code reader}. This assumes the fixed document defined in {@link
   * #peekJsonReader} and reads a range of it on each call.
   */
  private void readPeek12Steps(JsonReader reader, int from, int until) throws IOException {
    switch (from) {
      case 0:
        if (until == 0) break;
        reader.beginArray();
        assertThat(reader.getPath()).isEqualTo("$[0]");
      case 1:
        if (until == 1) break;
        assertThat(reader.nextInt()).isEqualTo(12);
        assertThat(reader.getPath()).isEqualTo("$[1]");
      case 2:
        if (until == 2) break;
        assertThat(reader.nextInt()).isEqualTo(34);
        assertThat(reader.getPath()).isEqualTo("$[2]");
      case 3:
        if (until == 3) break;
        reader.beginObject();
        assertThat(reader.getPath()).isEqualTo("$[2].");
      case 4:
        if (until == 4) break;
        assertThat(reader.nextName()).isEqualTo("a");
        assertThat(reader.getPath()).isEqualTo("$[2].a");
      case 5:
        if (until == 5) break;
        assertThat(reader.nextInt()).isEqualTo(56);
        assertThat(reader.getPath()).isEqualTo("$[2].a");
      case 6:
        if (until == 6) break;
        assertThat(reader.nextName()).isEqualTo("b");
        assertThat(reader.getPath()).isEqualTo("$[2].b");
      case 7:
        if (until == 7) break;
        assertThat(reader.nextInt()).isEqualTo(78);
        assertThat(reader.getPath()).isEqualTo("$[2].b");
      case 8:
        if (until == 8) break;
        reader.endObject();
        assertThat(reader.getPath()).isEqualTo("$[3]");
      case 9:
        if (until == 9) break;
        assertThat(reader.nextInt()).isEqualTo(90);
        assertThat(reader.getPath()).isEqualTo("$[4]");
      case 10:
        if (until == 10) break;
        reader.endArray();
        assertThat(reader.getPath()).isEqualTo("$");
      case 11:
        if (until == 11) break;
        assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
        assertThat(reader.getPath()).isEqualTo("$");
    }
  }

  /** Confirm that we can peek in every state of the UTF-8 reader. */
  @Test public void peekAfterPeek() throws IOException {
    JsonReader reader = newReader(
        "[{\"a\":\"aaa\",'b':'bbb',c:c,\"d\":\"d\"},true,false,null,1,2.0]");
    reader.setLenient(true);
    readValue(reader, true);
    reader.peekJson();
  }

  @Test public void peekAfterPromoteNameToValue() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals("a", reader.peekJson().nextString());
    assertEquals("a", reader.nextString());
    assertEquals("b", reader.peekJson().nextString());
    assertEquals("b", reader.nextString());
    reader.endObject();
  }

  /** Peek a value, then read it, recursively. */
  private void readValue(JsonReader reader, boolean peekJsonFirst) throws IOException {
    JsonReader.Token token = reader.peek();
    if (peekJsonFirst) {
      readValue(reader.peekJson(), false);
    }

    switch (token) {
      case BEGIN_ARRAY:
        reader.beginArray();
        while (reader.hasNext()) {
          readValue(reader, peekJsonFirst);
        }
        reader.peekJson().endArray();
        reader.endArray();
        break;
      case BEGIN_OBJECT:
        reader.beginObject();
        while (reader.hasNext()) {
          assertNotNull(reader.peekJson().nextName());
          assertNotNull(reader.nextName());
          readValue(reader, peekJsonFirst);
        }
        reader.peekJson().endObject();
        reader.endObject();
        break;
      case STRING:
        reader.nextString();
        break;
      case NUMBER:
        reader.nextDouble();
        break;
      case BOOLEAN:
        reader.nextBoolean();
        break;
      case NULL:
        reader.nextNull();
        break;
      default:
        throw new AssertionError();
    }
  }
}
