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
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public final class BufferedSinkJsonWriterTest {
  @Parameter public JsonWriterFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonWriterFactory.factories();
  }

  @Test public void nullsValuesNotSerializedByDefault() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    writer.nullValue();
    writer.endObject();
    writer.close();
    assertThat(factory.json()).isEqualTo("{}");
  }

  @Test public void nullsValuesSerializedWhenConfigured() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setSerializeNulls(true);
    writer.beginObject();
    writer.name("a");
    writer.nullValue();
    writer.endObject();
    writer.close();
    assertThat(factory.json()).isEqualTo("{\"a\":null}");
  }

  @Test public void topLevelBoolean() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.value(true);
    writer.close();
    assertThat(factory.json()).isEqualTo("true");
  }

  @Test public void topLevelNull() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.nullValue();
    writer.close();
    assertThat(factory.json()).isEqualTo("null");
  }

  @Test public void topLevelInt() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.value(123);
    writer.close();
    assertThat(factory.json()).isEqualTo("123");
  }

  @Test public void topLevelDouble() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.value(123.4);
    writer.close();
    assertThat(factory.json()).isEqualTo("123.4");
  }

  @Test public void topLevelString() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.value("a");
    writer.close();
    assertThat(factory.json()).isEqualTo("\"a\"");
  }

  @Test public void invalidTopLevelTypes() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.name("hello");
    try {
      writer.value("world");
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void twoNames() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    try {
      writer.name("a");
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void nameWithoutValue() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    try {
      writer.endObject();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void valueWithoutName() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    try {
      writer.value(true);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void multipleTopLevelValues() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray().endArray();
    try {
      writer.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void badNestingObject() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.beginObject();
    try {
      writer.endArray();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void badNestingArray() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.beginArray();
    try {
      writer.endObject();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void nullName() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    try {
      writer.name(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void nullStringValue() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setSerializeNulls(true);
    writer.beginObject();
    writer.name("a");
    writer.value((String) null);
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{\"a\":null}");
  }

  @Test public void nonFiniteDoubles() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    try {
      writer.value(Double.NaN);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      writer.value(Double.NEGATIVE_INFINITY);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      writer.value(Double.POSITIVE_INFINITY);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void nonFiniteBoxedDoubles() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    try {
      writer.value(new Double(Double.NaN));
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      writer.value(new Double(Double.NEGATIVE_INFINITY));
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      writer.value(new Double(Double.POSITIVE_INFINITY));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void doubles() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value(-0.0);
    writer.value(1.0);
    writer.value(Double.MAX_VALUE);
    writer.value(Double.MIN_VALUE);
    writer.value(0.0);
    writer.value(-0.5);
    writer.value(2.2250738585072014E-308);
    writer.value(Math.PI);
    writer.value(Math.E);
    writer.endArray();
    writer.close();
    assertThat(factory.json()).isEqualTo("[-0.0,"
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
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value(0);
    writer.value(1);
    writer.value(-1);
    writer.value(Long.MIN_VALUE);
    writer.value(Long.MAX_VALUE);
    writer.endArray();
    writer.close();
    assertThat(factory.json()).isEqualTo("[0,"
        + "1,"
        + "-1,"
        + "-9223372036854775808,"
        + "9223372036854775807]");
  }

  @Test public void numbers() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value(new BigInteger("0"));
    writer.value(new BigInteger("9223372036854775808"));
    writer.value(new BigInteger("-9223372036854775809"));
    writer.value(new BigDecimal("3.141592653589793238462643383"));
    writer.endArray();
    writer.close();
    assertThat(factory.json()).isEqualTo("[0,"
        + "9223372036854775808,"
        + "-9223372036854775809,"
        + "3.141592653589793238462643383]");
  }

  @Test public void booleans() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value(true);
    writer.value(false);
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[true,false]");
  }

  @Test public void boxedBooleans() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value((Boolean) true);
    writer.value((Boolean) false);
    writer.value((Boolean) null);
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[true,false,null]");
  }

  @Test public void nulls() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.nullValue();
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[null]");
  }

  @Test public void strings() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value("a");
    writer.value("a\"");
    writer.value("\"");
    writer.value(":");
    writer.value(",");
    writer.value("\b");
    writer.value("\f");
    writer.value("\n");
    writer.value("\r");
    writer.value("\t");
    writer.value(" ");
    writer.value("\\");
    writer.value("{");
    writer.value("}");
    writer.value("[");
    writer.value("]");
    writer.value("\0");
    writer.value("\u0019");
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\","
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
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value("\u2028 \u2029");
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"\\u2028 \\u2029\"]");
  }

  @Test public void emptyArray() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[]");
  }

  @Test public void emptyObject() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{}");
  }

  @Test public void objectsInArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.beginObject();
    writer.name("a").value(5);
    writer.name("b").value(false);
    writer.endObject();
    writer.beginObject();
    writer.name("c").value(6);
    writer.name("d").value(true);
    writer.endObject();
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[{\"a\":5,\"b\":false},"
        + "{\"c\":6,\"d\":true}]");
  }

  @Test public void arraysInObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    writer.beginArray();
    writer.value(5);
    writer.value(false);
    writer.endArray();
    writer.name("b");
    writer.beginArray();
    writer.value(6);
    writer.value(true);
    writer.endArray();
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{\"a\":[5,false],"
        + "\"b\":[6,true]}");
  }

  @Test public void deepNestingArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    for (int i = 0; i < 31; i++) {
      writer.beginArray();
    }
    for (int i = 0; i < 31; i++) {
      writer.endArray();
    }
    assertThat(factory.json())
        .isEqualTo("[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]");
  }

  @Test public void tooDeepNestingArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    for (int i = 0; i < 31; i++) {
      writer.beginArray();
    }
    try {
      writer.beginArray();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Nesting too deep at $[0][0][0][0][0][0][0][0][0][0][0][0][0]"
          + "[0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0][0]: circular reference?");
    }
  }

  @Test public void deepNestingObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    for (int i = 0; i < 31; i++) {
      writer.beginObject();
      writer.name("a");
    }
    writer.value(true);
    for (int i = 0; i < 31; i++) {
      writer.endObject();
    }
    assertThat(factory.json()).isEqualTo(""
        + "{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":"
        + "{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":"
        + "{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":{\"a\":true}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}");
  }

  @Test public void tooDeepNestingObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    for (int i = 0; i < 31; i++) {
      writer.beginObject();
      writer.name("a");
    }
    try {
      writer.beginObject();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Nesting too deep at $.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a."
          + "a.a.a.a.a.a.a.a.a.a.a.a: circular reference?");
    }
  }

  @Test public void repeatedName() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a").value(true);
    writer.name("a").value(false);
    writer.endObject();
    // JsonWriter doesn't attempt to detect duplicate names
    assertThat(factory.json()).isEqualTo("{\"a\":true,\"a\":false}");
  }

  @Test public void prettyPrintObject() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setSerializeNulls(true);
    writer.setIndent("   ");

    writer.beginObject();
    writer.name("a").value(true);
    writer.name("b").value(false);
    writer.name("c").value(5.0);
    writer.name("e").nullValue();
    writer.name("f").beginArray();
    writer.value(6.0);
    writer.value(7.0);
    writer.endArray();
    writer.name("g").beginObject();
    writer.name("h").value(8.0);
    writer.name("i").value(9.0);
    writer.endObject();
    writer.endObject();

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
    assertThat(factory.json()).isEqualTo(expected);
  }

  @Test public void prettyPrintArray() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setIndent("   ");

    writer.beginArray();
    writer.value(true);
    writer.value(false);
    writer.value(5.0);
    writer.nullValue();
    writer.beginObject();
    writer.name("a").value(6.0);
    writer.name("b").value(7.0);
    writer.endObject();
    writer.beginArray();
    writer.value(8.0);
    writer.value(9.0);
    writer.endArray();
    writer.endArray();

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
    assertThat(factory.json()).isEqualTo(expected);
  }

  @Test public void lenientWriterPermitsMultipleTopLevelValues() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setLenient(true);
    writer.beginArray();
    writer.endArray();
    writer.beginArray();
    writer.endArray();
    writer.close();
    assertThat(factory.json()).isEqualTo("[][]");
  }

  @Test public void strictWriterDoesNotPermitMultipleTopLevelValues() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.endArray();
    try {
      writer.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void closedWriterThrowsOnStructure() throws IOException {
    JsonWriter writer = factory.newWriter();
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
    JsonWriter writer = factory.newWriter();
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
    JsonWriter writer = factory.newWriter();
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
    JsonWriter writer = factory.newWriter();
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
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.endArray();
    writer.close();
    writer.close();
  }
}
