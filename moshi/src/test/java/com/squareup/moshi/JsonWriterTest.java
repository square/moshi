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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.BufferedSink;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static com.squareup.moshi.TestUtil.MAX_DEPTH;
import static com.squareup.moshi.TestUtil.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public final class JsonWriterTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
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
    try {
      writer.name("hello").value("world");
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
    assumeTrue(factory.supportsBigNumbers());

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

  @Test public void nullNumbers() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.value((Number) null);
    writer.endArray();
    writer.close();
    assertThat(factory.json()).isEqualTo("[null]");
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
    for (int i = 0; i < MAX_DEPTH; i++) {
      writer.beginArray();
    }
    for (int i = 0; i < MAX_DEPTH; i++) {
      writer.endArray();
    }
    assertThat(factory.json())
        .isEqualTo(repeat("[", MAX_DEPTH) + repeat("]", MAX_DEPTH));
  }

  @Test public void tooDeeplyNestingArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    for (int i = 0; i < MAX_DEPTH; i++) {
      writer.beginArray();
    }
    try {
      writer.beginArray();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Nesting too deep at $"
          + repeat("[0]", MAX_DEPTH) + ": circular reference?");
    }
  }

  @Test public void deepNestingObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    for (int i = 0; i < MAX_DEPTH; i++) {
      writer.beginObject();
      writer.name("a");
    }
    writer.value(true);
    for (int i = 0; i < MAX_DEPTH; i++) {
      writer.endObject();
    }
    assertThat(factory.json()).isEqualTo(
        repeat("{\"a\":", MAX_DEPTH) + "true" + repeat("}", MAX_DEPTH));
  }

  @Test public void tooDeeplyNestingObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    for (int i = 0; i < MAX_DEPTH; i++) {
      writer.beginObject();
      writer.name("a");
    }
    try {
      writer.beginObject();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Nesting too deep at $"
          + repeat(".a", MAX_DEPTH) + ": circular reference?");
    }
  }

  @Test public void lenientWriterPermitsMultipleTopLevelValues() throws IOException {
    assumeTrue(factory.encodesToBytes());

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

  @Test public void nameNotInObjectFails() throws IOException {
    JsonWriter writer = factory.newWriter();
    try {
      writer.name("a");
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage("Nesting problem.");
    }
  }

  @Test public void missingValueInObjectIsANestingProblem() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    try {
      writer.name("b");
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage("Nesting problem.");
    }
  }

  @Test public void nameInArrayIsANestingProblem() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    try {
      writer.name("a");
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage("Nesting problem.");
    }
  }
  
  @Test public void danglingNameFails() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    try {
      writer.endObject();
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage("Dangling name: a");
    }
  }

  @Test public void streamingValueInObject() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    BufferedSink value = writer.valueSink();
    value.writeByte('"');
    value.writeHexadecimalUnsignedLong(-1L);
    value.writeUtf8("sup");
    value.writeDecimalLong(-1L);
    value.writeByte('"');
    value.close();
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{\"a\":\"ffffffffffffffffsup-1\"}");
  }

  @Test public void streamingValueInArray() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.valueSink()
        .writeByte('"')
        .writeHexadecimalUnsignedLong(-1L)
        .writeByte('"')
        .close();
    writer.valueSink()
        .writeByte('"')
        .writeUtf8("sup")
        .writeByte('"')
        .close();
    writer.valueSink()
        .writeUtf8("-1.0")
        .close();
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"ffffffffffffffff\",\"sup\",-1.0]");
  }

  @Test public void streamingValueTopLevel() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.valueSink()
        .writeUtf8("-1.0")
        .close();
    assertThat(factory.json()).isEqualTo("-1.0");
  }

  @Test public void streamingValueTwiceBeforeCloseFails() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    BufferedSink sink = writer.valueSink();
    try {
      writer.valueSink();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Sink from valueSink() was not closed");
    }
  }

  @Test public void streamingValueTwiceAfterCloseFails() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    writer.valueSink().writeByte('0').close();
    try {
      // TODO currently UTF-8 fails eagerly on valueSink() but value does not fail until close().
      writer.valueSink().writeByte('0').close();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Nesting problem.");
    }
  }

  @Test public void streamingValueAndScalarValueFails() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    BufferedSink sink = writer.valueSink();
    try {
      writer.value("b");
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Sink from valueSink() was not closed");
    }
  }

  @Test public void streamingValueAndNameFails() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    BufferedSink sink = writer.valueSink();
    try {
      writer.name("b");
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Nesting problem.");
    }
  }

  @Test public void streamingValueInteractionAfterCloseFails() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    BufferedSink sink = writer.valueSink();
    sink.writeUtf8("1.0");
    sink.close();
    try {
      sink.writeByte('1');
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("closed");
    }
  }

  @Test public void streamingValueCloseIsIdempotent() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    BufferedSink sink = writer.valueSink();
    sink.writeUtf8("1.0");
    sink.close();
    sink.close();
    writer.endObject();
    sink.close();
    assertThat(factory.json()).isEqualTo("{\"a\":1.0}");
    sink.close();
  }

  @Test public void jsonValueTypes() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setSerializeNulls(true);

    writer.beginArray();
    writer.jsonValue(null);
    writer.jsonValue(1.1d);
    writer.jsonValue(1L);
    writer.jsonValue(1);
    writer.jsonValue(true);
    writer.jsonValue("one");
    writer.jsonValue(Collections.emptyList());
    writer.jsonValue(Arrays.asList(1, 2, null, 3));
    writer.jsonValue(Collections.emptyMap());
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("one", "uno");
    map.put("two", null);
    writer.jsonValue(map);
    writer.endArray();

    assertThat(factory.json()).isEqualTo("["
        + "null,"
        + "1.1,"
        + "1,"
        + "1,"
        + "true,"
        + "\"one\","
        + "[],"
        + "[1,2,null,3],"
        + "{},"
        + "{\"one\":\"uno\",\"two\":null}"
        + "]");
  }

  @Test public void jsonValueIllegalTypes() throws IOException {
    try {
      factory.newWriter().jsonValue(new Object());
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Unsupported type: java.lang.Object");
    }

    try {
      factory.newWriter().jsonValue('1');
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Unsupported type: java.lang.Character");
    }

    Map<Integer, String> mapWrongKey = new LinkedHashMap<>();
    mapWrongKey.put(1, "one");
    try {
      factory.newWriter().jsonValue(mapWrongKey);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Map keys must be of type String: java.lang.Integer");
    }

    Map<String, String> mapNullKey = new LinkedHashMap<>();
    mapNullKey.put(null, "one");
    try {
      factory.newWriter().jsonValue(mapNullKey);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Map keys must be non-null");
    }
  }
}
