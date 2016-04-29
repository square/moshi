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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import okio.Buffer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class BufferedSinkJsonWriterTest {
  @Test public void nullsValuesNotSerializedByDefault() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    jsonWriter.name("a");
    jsonWriter.nullValue();
    jsonWriter.endObject();
    jsonWriter.close();
    assertThat(buffer.readUtf8()).isEqualTo("{}");
  }

  @Test public void nullsValuesSerializedWhenConfigured() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.setSerializeNulls(true);
    jsonWriter.beginObject();
    jsonWriter.name("a");
    jsonWriter.nullValue();
    jsonWriter.endObject();
    jsonWriter.close();
    assertThat(buffer.readUtf8()).isEqualTo("{\"a\":null}");
  }

  @Test public void topLevelValueTypes() throws IOException {
    Buffer buffer = new Buffer();

    JsonWriter writer1 = JsonWriter.of(buffer);
    writer1.value(true);
    writer1.close();
    assertThat(buffer.readUtf8()).isEqualTo("true");

    JsonWriter writer2 = JsonWriter.of(buffer);
    writer2.nullValue();
    writer2.close();
    assertThat(buffer.readUtf8()).isEqualTo("null");

    JsonWriter writer3 = JsonWriter.of(buffer);
    writer3.value(123);
    writer3.close();
    assertThat(buffer.readUtf8()).isEqualTo("123");

    JsonWriter writer4 = JsonWriter.of(buffer);
    writer4.value(123.4);
    writer4.close();
    assertThat(buffer.readUtf8()).isEqualTo("123.4");

    JsonWriter writer5 = JsonWriter.of(buffer);
    writer5.value("a");
    writer5.close();
    assertThat(buffer.readUtf8()).isEqualTo("\"a\"");
  }

  @Test public void invalidTopLevelTypes() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.name("hello");
    try {
      writer.value("world");
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void twoNames() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    jsonWriter.name("a");
    try {
      jsonWriter.name("a");
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void nameWithoutValue() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    jsonWriter.name("a");
    try {
      jsonWriter.endObject();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void valueWithoutName() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    try {
      jsonWriter.value(true);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void multipleTopLevelValues() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray().endArray();
    try {
      jsonWriter.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void badNestingObject() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.beginObject();
    try {
      jsonWriter.endArray();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void badNestingArray() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.beginArray();
    try {
      jsonWriter.endObject();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void nullName() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    try {
      jsonWriter.name(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void nullStringValue() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.setSerializeNulls(true);
    jsonWriter.beginObject();
    jsonWriter.name("a");
    jsonWriter.value((String) null);
    jsonWriter.endObject();
    assertThat(buffer.readUtf8()).isEqualTo("{\"a\":null}");
  }

  @Test public void nonFiniteDoubles() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    try {
      jsonWriter.value(Double.NaN);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      jsonWriter.value(Double.NEGATIVE_INFINITY);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      jsonWriter.value(Double.POSITIVE_INFINITY);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void nonFiniteBoxedDoubles() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    try {
      jsonWriter.value(new Double(Double.NaN));
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      jsonWriter.value(new Double(Double.NEGATIVE_INFINITY));
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      jsonWriter.value(new Double(Double.POSITIVE_INFINITY));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void doubles() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.value(-0.0);
    jsonWriter.value(1.0);
    jsonWriter.value(Double.MAX_VALUE);
    jsonWriter.value(Double.MIN_VALUE);
    jsonWriter.value(0.0);
    jsonWriter.value(-0.5);
    jsonWriter.value(2.2250738585072014E-308);
    jsonWriter.value(Math.PI);
    jsonWriter.value(Math.E);
    jsonWriter.endArray();
    jsonWriter.close();
    assertThat(buffer.readUtf8()).isEqualTo("[-0.0,"
        + "1.0,"
        + "1.7976931348623157E308,"
        + "4.9E-324,"
        + "0.0,"
        + "-0.5,"
        + "2.2250738585072014E-308,"
        + "3.141592653589793,"
        + "2.718281828459045]");
  }

  @Test public void longs() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.value(0);
    jsonWriter.value(1);
    jsonWriter.value(-1);
    jsonWriter.value(Long.MIN_VALUE);
    jsonWriter.value(Long.MAX_VALUE);
    jsonWriter.endArray();
    jsonWriter.close();
    assertThat(buffer.readUtf8()).isEqualTo("[0,"
        + "1,"
        + "-1,"
        + "-9223372036854775808,"
        + "9223372036854775807]");
  }

  @Test public void numbers() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.value(new BigInteger("0"));
    jsonWriter.value(new BigInteger("9223372036854775808"));
    jsonWriter.value(new BigInteger("-9223372036854775809"));
    jsonWriter.value(new BigDecimal("3.141592653589793238462643383"));
    jsonWriter.endArray();
    jsonWriter.close();
    assertThat(buffer.readUtf8()).isEqualTo("[0,"
        + "9223372036854775808,"
        + "-9223372036854775809,"
        + "3.141592653589793238462643383]");
  }

  @Test public void booleans() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.value(true);
    jsonWriter.value(false);
    jsonWriter.endArray();
    assertThat(buffer.readUtf8()).isEqualTo("[true,false]");
  }

  @Test public void boxedBooleans() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.value((Boolean) true);
    jsonWriter.value((Boolean) false);
    jsonWriter.value((Boolean) null);
    jsonWriter.endArray();
    assertThat(buffer.readUtf8()).isEqualTo("[true,false,null]");
  }

  @Test public void nulls() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.nullValue();
    jsonWriter.endArray();
    assertThat(buffer.readUtf8()).isEqualTo("[null]");
  }

  @Test public void strings() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.value("a");
    jsonWriter.value("a\"");
    jsonWriter.value("\"");
    jsonWriter.value(":");
    jsonWriter.value(",");
    jsonWriter.value("\b");
    jsonWriter.value("\f");
    jsonWriter.value("\n");
    jsonWriter.value("\r");
    jsonWriter.value("\t");
    jsonWriter.value(" ");
    jsonWriter.value("\\");
    jsonWriter.value("{");
    jsonWriter.value("}");
    jsonWriter.value("[");
    jsonWriter.value("]");
    jsonWriter.value("\0");
    jsonWriter.value("\u0019");
    jsonWriter.endArray();
    assertThat(buffer.readUtf8()).isEqualTo("[\"a\","
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
        + "\"\\u0019\"]");
  }

  @Test public void unicodeLineBreaksEscaped() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.value("\u2028 \u2029");
    jsonWriter.endArray();
    assertThat(buffer.readUtf8()).isEqualTo("[\"\\u2028 \\u2029\"]");
  }

  @Test public void emptyArray() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.endArray();
    assertThat(buffer.readUtf8()).isEqualTo("[]");
  }

  @Test public void emptyObject() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    jsonWriter.endObject();
    assertThat(buffer.readUtf8()).isEqualTo("{}");
  }

  @Test public void objectsInArrays() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginArray();
    jsonWriter.beginObject();
    jsonWriter.name("a").value(5);
    jsonWriter.name("b").value(false);
    jsonWriter.endObject();
    jsonWriter.beginObject();
    jsonWriter.name("c").value(6);
    jsonWriter.name("d").value(true);
    jsonWriter.endObject();
    jsonWriter.endArray();
    assertThat(buffer.readUtf8()).isEqualTo("[{\"a\":5,\"b\":false},"
        + "{\"c\":6,\"d\":true}]");
  }

  @Test public void arraysInObjects() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    jsonWriter.name("a");
    jsonWriter.beginArray();
    jsonWriter.value(5);
    jsonWriter.value(false);
    jsonWriter.endArray();
    jsonWriter.name("b");
    jsonWriter.beginArray();
    jsonWriter.value(6);
    jsonWriter.value(true);
    jsonWriter.endArray();
    jsonWriter.endObject();
    assertThat(buffer.readUtf8()).isEqualTo("{\"a\":[5,false],"
        + "\"b\":[6,true]}");
  }

  @Test public void deepNestingArrays() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    for (int i = 0; i < 20; i++) {
      jsonWriter.beginArray();
    }
    for (int i = 0; i < 20; i++) {
      jsonWriter.endArray();
    }
    assertThat(buffer.readUtf8()).isEqualTo("[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]");
  }

  @Test public void deepNestingObjects() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    for (int i = 0; i < 20; i++) {
      jsonWriter.name("a");
      jsonWriter.beginObject();
    }
    for (int i = 0; i < 20; i++) {
      jsonWriter.endObject();
    }
    jsonWriter.endObject();
    assertThat(buffer.readUtf8()).isEqualTo(
        "{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":"
        + "{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{"
        + "}}}}}}}}}}}}}}}}}}}}}");
  }

  @Test public void repeatedName() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.beginObject();
    jsonWriter.name("a").value(true);
    jsonWriter.name("a").value(false);
    jsonWriter.endObject();
    // JsonWriter doesn't attempt to detect duplicate names
    assertThat(buffer.readUtf8()).isEqualTo("{\"a\":true,\"a\":false}");
  }

  @Test public void prettyPrintObject() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.setSerializeNulls(true);
    jsonWriter.setIndent("   ");

    jsonWriter.beginObject();
    jsonWriter.name("a").value(true);
    jsonWriter.name("b").value(false);
    jsonWriter.name("c").value(5.0);
    jsonWriter.name("e").nullValue();
    jsonWriter.name("f").beginArray();
    jsonWriter.value(6.0);
    jsonWriter.value(7.0);
    jsonWriter.endArray();
    jsonWriter.name("g").beginObject();
    jsonWriter.name("h").value(8.0);
    jsonWriter.name("i").value(9.0);
    jsonWriter.endObject();
    jsonWriter.endObject();

    String expected = "{\n"
        + "   \"a\": true,\n"
        + "   \"b\": false,\n"
        + "   \"c\": 5.0,\n"
        + "   \"e\": null,\n"
        + "   \"f\": [\n"
        + "      6.0,\n"
        + "      7.0\n"
        + "   ],\n"
        + "   \"g\": {\n"
        + "      \"h\": 8.0,\n"
        + "      \"i\": 9.0\n"
        + "   }\n"
        + "}";
    assertThat(buffer.readUtf8()).isEqualTo(expected);
  }

  @Test public void prettyPrintArray() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.setIndent("   ");

    jsonWriter.beginArray();
    jsonWriter.value(true);
    jsonWriter.value(false);
    jsonWriter.value(5.0);
    jsonWriter.nullValue();
    jsonWriter.beginObject();
    jsonWriter.name("a").value(6.0);
    jsonWriter.name("b").value(7.0);
    jsonWriter.endObject();
    jsonWriter.beginArray();
    jsonWriter.value(8.0);
    jsonWriter.value(9.0);
    jsonWriter.endArray();
    jsonWriter.endArray();

    String expected = "[\n"
        + "   true,\n"
        + "   false,\n"
        + "   5.0,\n"
        + "   null,\n"
        + "   {\n"
        + "      \"a\": 6.0,\n"
        + "      \"b\": 7.0\n"
        + "   },\n"
        + "   [\n"
        + "      8.0,\n"
        + "      9.0\n"
        + "   ]\n"
        + "]";
    assertThat(buffer.readUtf8()).isEqualTo(expected);
  }

  @Test public void lenientWriterPermitsMultipleTopLevelValues() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.setLenient(true);
    writer.beginArray();
    writer.endArray();
    writer.beginArray();
    writer.endArray();
    writer.close();
    assertThat(buffer.readUtf8()).isEqualTo("[][]");
  }

  @Test public void strictWriterDoesNotPermitMultipleTopLevelValues() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    writer.endArray();
    try {
      writer.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void closedWriterThrowsOnStructure() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    writer.endArray();
    writer.close();
    try {
      writer.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      writer.endArray();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      writer.beginObject();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      writer.endObject();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void closedWriterThrowsOnName() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    writer.endArray();
    writer.close();
    try {
      writer.name("a");
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void closedWriterThrowsOnValue() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    writer.endArray();
    writer.close();
    try {
      writer.value("a");
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void closedWriterThrowsOnFlush() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    writer.endArray();
    writer.close();
    try {
      writer.flush();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void writerCloseIsIdempotent() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    writer.endArray();
    writer.close();
    writer.close();
  }
}
