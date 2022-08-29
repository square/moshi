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

import static com.squareup.moshi.JsonReader.Token.*;
import static com.squareup.moshi.TestUtil.repeat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import okio.BufferedSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

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

  @Test
  public void readArray() throws IOException {
    JsonReader reader = newReader("[true, true]");
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    assertTrue(reader.nextBoolean());
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void readEmptyArray() throws IOException {
    JsonReader reader = newReader("[]");
    reader.beginArray();
    assertFalse(reader.hasNext());
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void readObject() throws IOException {
    JsonReader reader = newReader("{\"a\": \"android\", \"b\": \"banana\"}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextString(), "android");
    assertEquals(reader.nextName(), "b");
    assertEquals(reader.nextString(), "banana");
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void readEmptyObject() throws IOException {
    JsonReader reader = newReader("{}");
    reader.beginObject();
    assertFalse(reader.hasNext());
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipArray() throws IOException {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    reader.skipValue();
    assertEquals(reader.nextName(), "b");
    assertEquals(reader.nextInt(), 123);
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipArrayAfterPeek() throws Exception {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.peek(), BEGIN_ARRAY);
    reader.skipValue();
    assertEquals(reader.nextName(), "b");
    assertEquals(reader.nextInt(), 123);
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipTopLevelObject() throws Exception {
    JsonReader reader = newReader("{\"a\": [\"one\", \"two\", \"three\"], \"b\": 123}");
    reader.skipValue();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipObject() throws IOException {
    JsonReader reader =
        newReader("{\"a\": { \"c\": [], \"d\": [true, true, {}] }, \"b\": \"banana\"}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    reader.skipValue();
    assertEquals(reader.nextName(), "b");
    reader.skipValue();
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipObjectAfterPeek() throws Exception {
    String json =
        "{"
            + "  \"one\": { \"num\": 1 }"
            + ", \"two\": { \"num\": 2 }"
            + ", \"three\": { \"num\": 3 }"
            + "}";
    JsonReader reader = newReader(json);
    reader.beginObject();
    assertEquals(reader.nextName(), "one");
    assertEquals(reader.peek(), BEGIN_OBJECT);
    reader.skipValue();
    assertEquals(reader.nextName(), "two");
    assertEquals(reader.peek(), BEGIN_OBJECT);
    reader.skipValue();
    assertEquals(reader.nextName(), "three");
    reader.skipValue();
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipInteger() throws IOException {
    JsonReader reader = newReader("{\"a\":123456789,\"b\":-123456789}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    reader.skipValue();
    assertEquals(reader.nextName(), "b");
    reader.skipValue();
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipDouble() throws IOException {
    JsonReader reader = newReader("{\"a\":-123.456e-789,\"b\":123456789.0}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    reader.skipValue();
    assertEquals(reader.nextName(), "b");
    reader.skipValue();
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void failOnUnknownFailsOnUnknownObjectValue() throws IOException {
    JsonReader reader = newReader("{\"a\": 123}");
    reader.setFailOnUnknown(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Cannot skip unexpected NUMBER at $.a"));
    }
    // Confirm that the reader is left in a consistent state after the exception.
    reader.setFailOnUnknown(false);
    assertEquals(reader.nextInt(), 123);
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void failOnUnknownFailsOnUnknownArrayElement() throws IOException {
    JsonReader reader = newReader("[\"a\", 123]");
    reader.setFailOnUnknown(true);
    reader.beginArray();
    assertEquals(reader.nextString(), "a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Cannot skip unexpected NUMBER at $[1]"));
    }
    // Confirm that the reader is left in a consistent state after the exception.
    reader.setFailOnUnknown(false);
    assertEquals(reader.nextInt(), 123);
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void helloWorld() throws IOException {
    String json = "{\n" + "   \"hello\": true,\n" + "   \"foo\": [\"world\"]\n" + "}";
    JsonReader reader = newReader(json);
    reader.beginObject();
    assertEquals(reader.nextName(), "hello");
    assertTrue(reader.nextBoolean());
    assertEquals(reader.nextName(), "foo");
    reader.beginArray();
    assertEquals(reader.nextString(), "world");
    reader.endArray();
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void emptyString() throws Exception {
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

  @Test
  public void characterUnescaping() throws IOException {
    String json =
        "[\"a\","
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
    assertEquals(reader.nextString(), "a");
    assertEquals(reader.nextString(), "a\"");
    assertEquals(reader.nextString(), "\"");
    assertEquals(reader.nextString(), ":");
    assertEquals(reader.nextString(), ",");
    assertEquals(reader.nextString(), "\b");
    assertEquals(reader.nextString(), "\f");
    assertEquals(reader.nextString(), "\n");
    assertEquals(reader.nextString(), "\r");
    assertEquals(reader.nextString(), "\t");
    assertEquals(reader.nextString(), " ");
    assertEquals(reader.nextString(), "\\");
    assertEquals(reader.nextString(), "{");
    assertEquals(reader.nextString(), "}");
    assertEquals(reader.nextString(), "[");
    assertEquals(reader.nextString(), "]");
    assertEquals(reader.nextString(), "\0");
    assertEquals(reader.nextString(), "\u0019");
    assertEquals(reader.nextString(), "\u20AC");
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void integersWithFractionalPartSpecified() throws IOException {
    JsonReader reader = newReader("[1.0,1.0,1.0]");
    reader.beginArray();
    assertEquals(1.0, reader.nextDouble(), 0);
    assertEquals(1, reader.nextInt(), 0);
    assertEquals(1L, reader.nextLong(), 0);
  }

  @Test
  public void doubles() throws IOException {
    String json =
        "[-0.0,"
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
    assertEquals(-0.0, reader.nextDouble(), 0);
    assertEquals(1.0, reader.nextDouble(), 0);
    assertEquals(1.7976931348623157E308, reader.nextDouble(), 0);
    assertEquals(4.9E-324, reader.nextDouble(), 0);
    assertEquals(0.0, reader.nextDouble(), 0);
    assertEquals(-0.5, reader.nextDouble(), 0);
    assertEquals(2.2250738585072014E-308, reader.nextDouble(), 0);
    assertEquals(3.141592653589793, reader.nextDouble(), 0);
    assertEquals(2.718281828459045, reader.nextDouble(), 0);
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void strictNonFiniteDoubles() throws IOException {
    String json = "[NaN]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictQuotedNonFiniteDoubles() throws IOException {
    String json = "[\"NaN\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonEncodingException expected) {
      assertTrue(expected.getMessage().contains("NaN"));
    }
  }

  @Test
  public void lenientNonFiniteDoubles() throws IOException {
    String json = "[NaN, -Infinity, Infinity]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(Double.isNaN(reader.nextDouble()));
    assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble(), 0);
    assertEquals(Double.POSITIVE_INFINITY, reader.nextDouble(), 0);
    reader.endArray();
  }

  @Test
  public void lenientQuotedNonFiniteDoubles() throws IOException {
    String json = "[\"NaN\", \"-Infinity\", \"Infinity\"]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(Double.isNaN((reader.nextDouble())));
    assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble(), 0);
    assertEquals(Double.POSITIVE_INFINITY, reader.nextDouble(), 0);
    reader.endArray();
  }

  @Test
  public void longs() throws IOException {
    assumeTrue(factory.implementsStrictPrecision());

    String json =
        "[0,0,0," + "1,1,1," + "-1,-1,-1," + "-9223372036854775808," + "9223372036854775807]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals(reader.nextLong(), 0L);
    assertEquals(reader.nextInt(), 0);
    assertEquals(0.0d, reader.nextDouble(), 0);
    assertEquals(reader.nextLong(), 1L);
    assertEquals(reader.nextInt(), 1);
    assertEquals(1.0d, reader.nextDouble(), 0);
    assertEquals(reader.nextLong(), -1L);
    assertEquals(reader.nextInt(), -1);
    assertEquals(-1.0d, reader.nextDouble(), 0);
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextLong(), Long.MIN_VALUE);
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextLong(), Long.MAX_VALUE);
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void booleans() throws IOException {
    JsonReader reader = newReader("[true,false]");
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    assertFalse(reader.nextBoolean());
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void nextFailuresDoNotAdvance() throws IOException {
    JsonReader reader = newReader("{\"a\":true}");
    reader.beginObject();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextName(), "a");
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
    assertTrue(reader.nextBoolean());
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
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
    reader.close();
  }

  @Test
  public void integerMismatchWithDoubleDoesNotAdvance() throws IOException {
    assumeTrue(factory.implementsStrictPrecision());

    JsonReader reader = newReader("[1.5]");
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(1.5d, reader.nextDouble(), 0);
    reader.endArray();
  }

  @Test
  public void integerMismatchWithLongDoesNotAdvance() throws IOException {
    assumeTrue(factory.implementsStrictPrecision());

    JsonReader reader = newReader("[9223372036854775807]");
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextLong(), 9223372036854775807L);
    reader.endArray();
  }

  @Test
  public void longMismatchWithDoubleDoesNotAdvance() throws IOException {
    assumeTrue(factory.implementsStrictPrecision());

    JsonReader reader = newReader("[1.5]");
    reader.beginArray();
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(1.5d, reader.nextDouble(), 0);
    reader.endArray();
  }

  @Test
  public void stringNullIsNotNull() throws IOException {
    JsonReader reader = newReader("[\"null\"]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test
  public void nullLiteralIsNotAString() throws IOException {
    JsonReader reader = newReader("[null]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test
  public void topLevelValueTypes() throws IOException {
    JsonReader reader1 = newReader("true");
    assertTrue(reader1.nextBoolean());
    assertEquals(reader1.peek(), JsonReader.Token.END_DOCUMENT);

    JsonReader reader2 = newReader("false");
    assertFalse(reader2.nextBoolean());
    assertEquals(reader2.peek(), JsonReader.Token.END_DOCUMENT);

    JsonReader reader3 = newReader("null");
    assertNull(reader3.nextNull());
    assertEquals(reader3.peek(), JsonReader.Token.END_DOCUMENT);

    JsonReader reader4 = newReader("123");
    assertEquals(reader4.nextInt(), 123);
    assertEquals(reader4.peek(), JsonReader.Token.END_DOCUMENT);

    JsonReader reader5 = newReader("123.4");
    assertEquals(123.4, reader5.nextDouble(), 0);
    assertEquals(reader5.peek(), JsonReader.Token.END_DOCUMENT);

    JsonReader reader6 = newReader("\"a\"");
    assertEquals(reader6.nextString(), "a");
    assertEquals(reader6.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void topLevelValueTypeWithSkipValue() throws IOException {
    JsonReader reader = newReader("true");
    reader.skipValue();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void deeplyNestedArrays() throws IOException {
    JsonReader reader = newReader("[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]");
    for (int i = 0; i < 31; i++) {
      reader.beginArray();
    }
    assertEquals(
        reader.getPath(),
        "$[0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0]"
            + "[0][0][0][0][0][0][0][0][0][0][0][0][0]");
    for (int i = 0; i < 31; i++) {
      reader.endArray();
    }
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void deeplyNestedObjects() throws IOException {
    // Build a JSON document structured like {"a":{"a":{"a":{"a":true}}}}, but 31 levels deep.
    String array = "{\"a\":%s}";
    String json = "true";
    for (int i = 0; i < 31; i++) {
      json = String.format(array, json);
    }

    JsonReader reader = newReader(json);
    for (int i = 0; i < 31; i++) {
      reader.beginObject();
      assertEquals(reader.nextName(), "a");
    }
    assertEquals(
        reader.getPath(), "$.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a");
    assertTrue(reader.nextBoolean());
    for (int i = 0; i < 31; i++) {
      reader.endObject();
    }
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipVeryLongUnquotedString() throws IOException {
    JsonReader reader = newReader("[" + repeat('x', 8192) + "]");
    reader.setLenient(true);
    reader.beginArray();
    reader.skipValue();
    reader.endArray();
  }

  @Test
  public void skipTopLevelUnquotedString() throws IOException {
    JsonReader reader = newReader(repeat('x', 8192));
    reader.setLenient(true);
    reader.skipValue();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipVeryLongQuotedString() throws IOException {
    JsonReader reader = newReader("[\"" + repeat('x', 8192) + "\"]");
    reader.beginArray();
    reader.skipValue();
    reader.endArray();
  }

  @Test
  public void skipTopLevelQuotedString() throws IOException {
    JsonReader reader = newReader("\"" + repeat('x', 8192) + "\"");
    reader.setLenient(true);
    reader.skipValue();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void stringAsNumberWithTruncatedExponent() throws IOException {
    JsonReader reader = newReader("[123e]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), STRING);
  }

  @Test
  public void stringAsNumberWithDigitAndNonDigitExponent() throws IOException {
    JsonReader reader = newReader("[123e4b]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), STRING);
  }

  @Test
  public void stringAsNumberWithNonDigitExponent() throws IOException {
    JsonReader reader = newReader("[123eb]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), STRING);
  }

  @Test
  public void emptyStringName() throws IOException {
    JsonReader reader = newReader("{\"\":true}");
    reader.setLenient(true);
    assertEquals(reader.peek(), BEGIN_OBJECT);
    reader.beginObject();
    assertEquals(reader.peek(), NAME);
    assertEquals(reader.nextName(), "");
    assertEquals(reader.peek(), JsonReader.Token.BOOLEAN);
    assertTrue(reader.nextBoolean());
    assertEquals(reader.peek(), JsonReader.Token.END_OBJECT);
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void validEscapes() throws IOException {
    JsonReader reader = newReader("[\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"]");
    reader.beginArray();
    assertEquals(reader.nextString(), "\"\\/\b\f\n\r\t");
  }

  @Test
  public void selectName() throws IOException {
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
  @Test
  public void selectNameNecessaryEscaping() throws IOException {
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
  @Test
  public void selectNameUnnecessaryEscaping() throws IOException {
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

  @Test
  public void selectNameUnquoted() throws Exception {
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

  @Test
  public void selectNameSingleQuoted() throws IOException {
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

  @Test
  public void selectString() throws IOException {
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

  @Test
  public void selectStringNecessaryEscaping() throws Exception {
    JsonReader.Options options = JsonReader.Options.of("\n", "\u0000", "\"");

    JsonReader reader = newReader("[\"\\n\",\"\\u0000\", \"\\\"\"]");
    reader.beginArray();
    assertEquals(0, reader.selectString(options));
    assertEquals(1, reader.selectString(options));
    assertEquals(2, reader.selectString(options));
    reader.endArray();
  }

  /** Select strips unnecessarily-escaped strings. */
  @Test
  public void selectStringUnnecessaryEscaping() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[\"\\u0061\", \"b\", \"\\u0063\"]");
    reader.beginArray();
    assertEquals(0, reader.selectString(abc));
    assertEquals(1, reader.selectString(abc));
    assertEquals(2, reader.selectString(abc));
    reader.endArray();
  }

  @Test
  public void selectStringUnquoted() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("[a, \"b\", c]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(0, reader.selectString(abc));
    assertEquals(1, reader.selectString(abc));
    assertEquals(2, reader.selectString(abc));
    reader.endArray();
  }

  @Test
  public void selectStringSingleQuoted() throws IOException {
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");

    JsonReader reader = newReader("['a', \"b\", c]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(0, reader.selectString(abc));
    assertEquals(1, reader.selectString(abc));
    assertEquals(2, reader.selectString(abc));
    reader.endArray();
  }

  @Test
  public void selectStringMaintainsReaderState() throws IOException {
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

  @Test
  public void selectStringWithoutString() throws IOException {
    JsonReader.Options numbers = JsonReader.Options.of("1", "2.0", "true", "4");

    JsonReader reader = newReader("[0, 2.0, true, \"4\"]");
    reader.beginArray();
    assertEquals(reader.selectString(numbers), -1);
    reader.skipValue();
    assertEquals(reader.selectString(numbers), -1);
    reader.skipValue();
    assertEquals(reader.selectString(numbers), -1);
    reader.skipValue();
    assertEquals(reader.selectString(numbers), 3);
    reader.endArray();
  }

  @Test
  public void stringToNumberCoersion() throws Exception {
    JsonReader reader = newReader("[\"0\", \"9223372036854775807\", \"1.5\"]");
    reader.beginArray();
    assertEquals(reader.nextInt(), 0);
    assertEquals(reader.nextLong(), 9223372036854775807L);
    assertEquals(1.5d, reader.nextDouble(), 0);
    reader.endArray();
  }

  @Test
  public void unnecessaryPrecisionNumberCoersion() throws Exception {
    JsonReader reader = newReader("[\"0.0\", \"9223372036854775807.0\"]");
    reader.beginArray();
    assertEquals(reader.nextInt(), 0);
    assertEquals(reader.nextLong(), 9223372036854775807L);
    reader.endArray();
  }

  @Test
  public void nanInfinityDoubleCoersion() throws Exception {
    JsonReader reader = newReader("[\"NaN\", \"Infinity\", \"-Infinity\"]");
    reader.beginArray();
    reader.setLenient(true);
    assertTrue(Double.isNaN((reader.nextDouble())));
    assertEquals(Double.POSITIVE_INFINITY, reader.nextDouble(), 0);
    assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble(), 0);
    reader.endArray();
  }

  @Test
  public void intMismatchWithStringDoesNotAdvance() throws Exception {
    JsonReader reader = newReader("[\"a\"]");
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextString(), "a");
    reader.endArray();
  }

  @Test
  public void longMismatchWithStringDoesNotAdvance() throws Exception {
    JsonReader reader = newReader("[\"a\"]");
    reader.beginArray();
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextString(), "a");
    reader.endArray();
  }

  @Test
  public void doubleMismatchWithStringDoesNotAdvance() throws Exception {
    JsonReader reader = newReader("[\"a\"]");
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextString(), "a");
    reader.endArray();
  }

  @Test
  public void readJsonValueInt() throws IOException {
    JsonReader reader = newReader("1");
    Object value = reader.readJsonValue();
    assertEquals(value, 1.0);
  }

  @Test
  public void readJsonValueMap() throws IOException {
    JsonReader reader = newReader("{\"hello\": \"world\"}");
    Object value = reader.readJsonValue();
    assertEquals(value, Collections.singletonMap("hello", "world"));
  }

  @Test
  public void readJsonValueList() throws IOException {
    JsonReader reader = newReader("[\"a\", \"b\"]");
    Object value = reader.readJsonValue();
    assertEquals(value, Arrays.asList("a", "b"));
  }

  @Test
  public void readJsonValueListMultipleTypes() throws IOException {
    JsonReader reader = newReader("[\"a\", 5, false]");
    Object value = reader.readJsonValue();
    assertEquals(value, Arrays.asList("a", 5.0, false));
  }

  @Test
  public void readJsonValueNestedListInMap() throws IOException {
    JsonReader reader = newReader("{\"pizzas\": [\"cheese\", \"pepperoni\"]}");
    Object value = reader.readJsonValue();
    assertEquals(value, Collections.singletonMap("pizzas", Arrays.asList("cheese", "pepperoni")));
  }

  @Test
  public void skipName() throws IOException {
    JsonReader reader = newReader("{\"a\":1}");
    reader.beginObject();
    reader.skipName();
    assertEquals(reader.peek(), JsonReader.Token.NUMBER);
    reader.skipValue();
    reader.endObject();
  }

  @Test
  public void skipNameFailUnknown() throws IOException {
    JsonReader reader = newReader("{\"a\":1,\"b\":2}");
    reader.setFailOnUnknown(true);
    reader.beginObject();
    assertEquals("a", reader.nextName());
    assertEquals(1, reader.nextInt());
    try {
      reader.skipName();
      fail();
    } catch (JsonDataException e) {
      assertTrue(e.getMessage().contains("Cannot skip unexpected NAME at $.b"));
    }
  }

  @Test
  public void skipNameOnValueFails() throws IOException {
    JsonReader reader = newReader("1");
    try {
      reader.skipName();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextInt(), 1);
  }

  @Test
  public void emptyDocumentHasNextReturnsFalse() throws IOException {
    JsonReader reader = newReader("1");
    reader.readJsonValue();
    assertFalse(reader.hasNext());
  }

  @Test
  public void skipValueAtEndOfObjectFails() throws IOException {
    JsonReader reader = newReader("{}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a value but was END_OBJECT at path $."));
    }
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipValueAtEndOfArrayFails() throws IOException {
    JsonReader reader = newReader("[]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a value but was END_ARRAY at path $[0]"));
    }
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipValueAtEndOfDocumentFails() throws IOException {
    JsonReader reader = newReader("1");
    reader.nextInt();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a value but was END_DOCUMENT at path $"));
    }
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void basicPeekJson() throws IOException {
    JsonReader reader = newReader("{\"a\":12,\"b\":[34,56],\"c\":78}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextInt(), 12);
    assertEquals(reader.nextName(), "b");
    reader.beginArray();
    assertEquals(reader.nextInt(), 34);

    // Peek.
    JsonReader peekReader = reader.peekJson();
    assertEquals(peekReader.nextInt(), 56);
    peekReader.endArray();
    assertEquals(peekReader.nextName(), "c");
    assertEquals(peekReader.nextInt(), 78);
    peekReader.endObject();
    assertEquals(peekReader.peek(), JsonReader.Token.END_DOCUMENT);

    // Read again.
    assertEquals(reader.nextInt(), 56);
    reader.endArray();
    assertEquals(reader.nextName(), "c");
    assertEquals(reader.nextInt(), 78);
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  /**
   * We have a document that requires 12 operations to read. We read it step-by-step with one real
   * reader. Before each of the real reader’s operations we create a peeking reader and let it read
   * the rest of the document.
   */
  @Test
  public void peekJsonReader() throws IOException {
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
        assertEquals(reader.getPath(), "$[0]");
      case 1:
        if (until == 1) break;
        assertEquals(reader.nextInt(), 12);
        assertEquals(reader.getPath(), "$[1]");
      case 2:
        if (until == 2) break;
        assertEquals(reader.nextInt(), 34);
        assertEquals(reader.getPath(), "$[2]");
      case 3:
        if (until == 3) break;
        reader.beginObject();
        assertEquals(reader.getPath(), "$[2].");
      case 4:
        if (until == 4) break;
        assertEquals(reader.nextName(), "a");
        assertEquals(reader.getPath(), "$[2].a");
      case 5:
        if (until == 5) break;
        assertEquals(reader.nextInt(), 56);
        assertEquals(reader.getPath(), "$[2].a");
      case 6:
        if (until == 6) break;
        assertEquals(reader.nextName(), "b");
        assertEquals(reader.getPath(), "$[2].b");
      case 7:
        if (until == 7) break;
        assertEquals(reader.nextInt(), 78);
        assertEquals(reader.getPath(), "$[2].b");
      case 8:
        if (until == 8) break;
        reader.endObject();
        assertEquals(reader.getPath(), "$[3]");
      case 9:
        if (until == 9) break;
        assertEquals(reader.nextInt(), 90);
        assertEquals(reader.getPath(), "$[4]");
      case 10:
        if (until == 10) break;
        reader.endArray();
        assertEquals(reader.getPath(), "$");
      case 11:
        if (until == 11) break;
        assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
        assertEquals(reader.getPath(), "$");
    }
  }

  /** Confirm that we can peek in every state of the UTF-8 reader. */
  @Test
  public void peekAfterPeek() throws IOException {
    JsonReader reader =
        newReader("[{\"a\":\"aaa\",'b':'bbb',c:c,\"d\":\"d\"},true,false,null,1,2.0]");
    reader.setLenient(true);
    readValue(reader, true);
    reader.peekJson();
  }

  @Test
  public void peekAfterPromoteNameToValue() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals("a", reader.peekJson().nextString());
    assertEquals("a", reader.nextString());
    assertEquals("b", reader.peekJson().nextString());
    assertEquals("b", reader.nextString());
    reader.endObject();
  }

  @Test
  public void promoteStringNameToValue() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals("a", reader.nextString());
    assertEquals("b", reader.nextString());
    reader.endObject();
  }

  @Test
  public void promoteDoubleNameToValue() throws IOException {
    JsonReader reader = newReader("{\"5\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(5.0, reader.nextDouble(), 0.0);
    assertEquals("b", reader.nextString());
    reader.endObject();
  }

  @Test
  public void promoteLongNameToValue() throws IOException {
    JsonReader reader = newReader("{\"5\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(5L, reader.nextLong());
    assertEquals("b", reader.nextString());
    reader.endObject();
  }

  @Test
  public void promoteNullNameToValue() throws IOException {
    JsonReader reader = newReader("{\"null\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    try {
      reader.nextNull();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals("null", reader.nextString());
  }

  @Test
  public void promoteBooleanNameToValue() throws IOException {
    JsonReader reader = newReader("{\"true\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals("true", reader.nextString());
  }

  @Test
  public void promoteBooleanNameToValueCannotBeReadAsName() throws IOException {
    JsonReader reader = newReader("{\"true\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals("true", reader.nextString());
  }

  @Test
  public void promoteSkippedNameToValue() throws IOException {
    JsonReader reader = newReader("{\"true\":\"b\"}");
    reader.beginObject();
    reader.promoteNameToValue();
    reader.skipValue();
    assertEquals("b", reader.nextString());
  }

  @Test
  public void promoteNameToValueAtEndOfObject() throws IOException {
    JsonReader reader = newReader("{}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertFalse(reader.hasNext());
    reader.endObject();
  }

  @Test
  public void optionsStrings() {
    String[] options = new String[] {"a", "b", "c"};
    JsonReader.Options abc = JsonReader.Options.of("a", "b", "c");
    List<String> strings = abc.strings();
    List<String> optionList = Arrays.stream(options).collect(Collectors.toList());
    assertTrue(optionList.containsAll(strings));
    assertTrue(strings.containsAll(optionList));
    try {
      // Confirm it's unmodifiable and we can't mutate the original underlying array
      strings.add("d");
      fail();
    } catch (UnsupportedOperationException expected) {

    }
  }

  @Test
  public void nextSourceString() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":\"this is a string\"}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "\"this is a string\"");
    }
  }

  @Test
  public void nextSourceLong() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":-2.0}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "-2.0");
    }
  }

  @Test
  public void nextSourceNull() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":null}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "null");
    }
  }

  @Test
  public void nextSourceBoolean() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":false}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "false");
    }
  }

  @Test
  public void nextSourceObject() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":{\"b\":2.0,\"c\":3.0}}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "{\"b\":2.0,\"c\":3.0}");
    }
  }

  @Test
  public void nextSourceArray() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":[2.0,2.0,3.0]}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "[2.0,2.0,3.0]");
    }
  }

  /**
   * When we call {@link JsonReader#selectString} it causes the reader to consume bytes of the input
   * string. When attempting to read it as a stream afterwards the bytes are reconstructed.
   */
  @Test
  public void nextSourceStringBuffered() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":\"b\"}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.selectString(JsonReader.Options.of("x'")), -1);
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "\"b\"");
    }
  }

  /** If we don't read the bytes of the source, they JsonReader doesn't lose its place. */
  @Test
  public void nextSourceNotConsumed() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":\"b\",\"c\":\"d\"}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    reader.nextSource(); // Not closed.
    assertEquals(reader.nextName(), "c");
    assertEquals(reader.nextString(), "d");
  }

  @SuppressWarnings("rawtypes")
  @Test
  public void tags() throws IOException {
    JsonReader reader = newReader("{}");
    assertNull(reader.tag(Integer.class));
    assertNull(reader.tag(CharSequence.class));

    reader.setTag(Integer.class, 1);
    reader.setTag(CharSequence.class, "Foo");
    try {
      reader.setTag((Class) CharSequence.class, 1);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected.getMessage().contains("Tag value must be of type java.lang.CharSequence"));
    }

    Object intTag = reader.tag(Integer.class);
    assertEquals(intTag, 1);
    assertNotNull(intTag);
    assertTrue(intTag instanceof Integer);
    Object charSequenceTag = reader.tag(CharSequence.class);
    assertEquals(charSequenceTag, "Foo");
    assertTrue(charSequenceTag instanceof String);
    assertNull(reader.tag(String.class));
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
