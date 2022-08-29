/*
 * Copyright (C) 2017 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import okio.Buffer;
import org.junit.Test;

public final class JsonValueWriterTest {
  @SuppressWarnings("unchecked")
  @Test
  public void array() throws Exception {
    JsonValueWriter writer = new JsonValueWriter();

    writer.beginArray();
    writer.value("s");
    writer.value(1.5d);
    writer.value(true);
    writer.nullValue();
    writer.endArray();
    List<Object> values = (List<Object>) writer.root();
    assertEquals("s", values.get(0));
    assertEquals(1.5d, values.get(1));
    assertEquals(true, values.get(2));
    assertEquals(null, values.get(3));
  }

  @Test
  public void object() throws Exception {
    JsonValueWriter writer = new JsonValueWriter();
    writer.setSerializeNulls(true);

    writer.beginObject();
    writer.name("a").value("s");
    writer.name("b").value(1.5d);
    writer.name("c").value(true);
    writer.name("d").nullValue();
    writer.endObject();
    Map<String, Object> valuesMap = (Map<String, Object>) writer.root();

    List<String> keys = Arrays.asList("a", "b", "c", "d");
    List<Object> values = Arrays.asList("s", 1.5d, true, null);

    assertEquals("s", valuesMap.get("a"));
    assertEquals(1.5d, valuesMap.get("b"));
    assertEquals(true, valuesMap.get("c"));
    assertEquals(null, valuesMap.get("d"));
    assertTrue(valuesMap.keySet().containsAll(keys));
    assertTrue(keys.containsAll(valuesMap.keySet()));
    assertTrue(values.containsAll(valuesMap.values()));
    assertTrue(valuesMap.values().containsAll(values));
  }

  @Test
  public void repeatedNameThrows() throws IOException {
    JsonValueWriter writer = new JsonValueWriter();
    writer.beginObject();
    writer.name("a").value(1L);
    try {
      writer.name("a").value(2L);
      fail();
    } catch (IllegalArgumentException expected) {
      assertEquals("Map key 'a' has multiple values at path $.a: 1 and 2", expected.getMessage());
    }
  }

  @Test
  public void valueLongEmitsLong() {
    JsonValueWriter writer = new JsonValueWriter();
    writer.beginArray();
    writer.value(Long.MIN_VALUE);
    writer.value(-1L);
    writer.value(0L);
    writer.value(1L);
    writer.value(Long.MAX_VALUE);
    writer.endArray();

    List<Number> numbers = Arrays.asList(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE);
    assertEquals(writer.root(), numbers);
  }

  @Test
  public void valueDoubleEmitsDouble() throws Exception {
    JsonValueWriter writer = new JsonValueWriter();
    writer.setLenient(true);
    writer.beginArray();
    writer.value(-2147483649.0d);
    writer.value(-2147483648.0d);
    writer.value(-1.0d);
    writer.value(0.0d);
    writer.value(1.0d);
    writer.value(2147483647.0d);
    writer.value(2147483648.0d);
    writer.value(9007199254740991.0d);
    writer.value(9007199254740992.0d);
    writer.value(9007199254740994.0d);
    writer.value(9223372036854776832.0d);
    writer.value(-0.5d);
    writer.value(-0.0d);
    writer.value(0.5d);
    writer.value(9.22337203685478e18);
    writer.value(Double.NEGATIVE_INFINITY);
    writer.value(Double.MIN_VALUE);
    writer.value(Double.MIN_NORMAL);
    writer.value(-Double.MIN_NORMAL);
    writer.value(Double.MAX_VALUE);
    writer.value(Double.POSITIVE_INFINITY);
    writer.value(Double.NaN);
    writer.endArray();

    List<Number> numbers =
        Arrays.<Number>asList(
            -2147483649.0d,
            -2147483648.0d,
            -1.0d,
            0.0d,
            1.0d,
            2147483647.0d,
            2147483648.0d,
            9007199254740991.0d,
            9007199254740992.0d,
            9007199254740994.0d,
            9223372036854775807.0d,
            -0.5d,
            -0.0d,
            0.5d,
            9.22337203685478e18,
            Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE,
            Double.MIN_NORMAL,
            -Double.MIN_NORMAL,
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            Double.NaN);
    assertEquals((List<?>) writer.root(), numbers);
  }

  @Test
  public void primitiveIntegerTypesEmitLong() throws Exception {
    JsonValueWriter writer = new JsonValueWriter();
    writer.beginArray();
    writer.value(Byte.valueOf(Byte.MIN_VALUE));
    writer.value(Short.valueOf(Short.MIN_VALUE));
    writer.value(Integer.valueOf(Integer.MIN_VALUE));
    writer.value(Long.valueOf(Long.MIN_VALUE));
    writer.endArray();

    List<Number> numbers = Arrays.asList(-128L, -32768L, -2147483648L, -9223372036854775808L);
    assertEquals(writer.root(), numbers);
  }

  @Test
  public void primitiveFloatingPointTypesEmitDouble() throws Exception {
    JsonValueWriter writer = new JsonValueWriter();
    writer.beginArray();
    writer.value(Float.valueOf(0.5f));
    writer.value(Double.valueOf(0.5d));
    writer.endArray();

    List<Number> numbers = Arrays.asList(0.5d, 0.5d);
    assertEquals(writer.root(), numbers);
  }

  @Test
  public void otherNumberTypesEmitBigDecimal() throws Exception {
    JsonValueWriter writer = new JsonValueWriter();
    writer.beginArray();
    writer.value(new AtomicInteger(-2147483648));
    writer.value(new AtomicLong(-9223372036854775808L));
    writer.value(new BigInteger("-9223372036854775808"));
    writer.value(new BigInteger("-1"));
    writer.value(new BigInteger("0"));
    writer.value(new BigInteger("1"));
    writer.value(new BigInteger("9223372036854775807"));
    writer.value(new BigDecimal("-9223372036854775808"));
    writer.value(new BigDecimal("-1"));
    writer.value(new BigDecimal("0"));
    writer.value(new BigDecimal("1"));
    writer.value(new BigDecimal("9223372036854775807"));
    writer.value(new BigInteger("-9223372036854775809"));
    writer.value(new BigInteger("9223372036854775808"));
    writer.value(new BigDecimal("-9223372036854775809"));
    writer.value(new BigDecimal("9223372036854775808"));
    writer.value(new BigDecimal("0.5"));
    writer.value(new BigDecimal("100000e15"));
    writer.value(new BigDecimal("0.0000100e-10"));
    writer.endArray();

    List<Number> numbers =
        Arrays.<Number>asList(
            new BigDecimal("-2147483648"),
            new BigDecimal("-9223372036854775808"),
            new BigDecimal("-9223372036854775808"),
            new BigDecimal("-1"),
            new BigDecimal("0"),
            new BigDecimal("1"),
            new BigDecimal("9223372036854775807"),
            new BigDecimal("-9223372036854775808"),
            new BigDecimal("-1"),
            new BigDecimal("0"),
            new BigDecimal("1"),
            new BigDecimal("9223372036854775807"),
            new BigDecimal("-9223372036854775809"),
            new BigDecimal("9223372036854775808"),
            new BigDecimal("-9223372036854775809"),
            new BigDecimal("9223372036854775808"),
            new BigDecimal("0.5"),
            new BigDecimal("100000e15"),
            new BigDecimal("0.0000100e-10"));
    assertEquals((List<?>) writer.root(), numbers);
  }

  @Test
  public void valueCustomNumberTypeEmitsLongOrBigDecimal() throws Exception {
    JsonValueWriter writer = new JsonValueWriter();
    writer.beginArray();
    writer.value(stringNumber("-9223372036854775809"));
    writer.value(stringNumber("-9223372036854775808"));
    writer.value(stringNumber("0.5"));
    writer.value(stringNumber("1.0"));
    writer.endArray();

    List<Number> numbers =
        Arrays.asList(
            new BigDecimal("-9223372036854775809"),
            new BigDecimal("-9223372036854775808"),
            new BigDecimal("0.5"),
            new BigDecimal("1.0"));
    assertEquals(writer.root(), numbers);
  }

  @Test
  public void valueFromSource() throws IOException {
    JsonValueWriter writer = new JsonValueWriter();
    writer.beginObject();
    writer.name("a");
    writer.value(new Buffer().writeUtf8("[\"value\"]"));
    writer.name("b");
    writer.value(new Buffer().writeUtf8("2"));
    writer.name("c");
    writer.value(3);
    writer.name("d");
    writer.value(new Buffer().writeUtf8("null"));
    writer.endObject();

    Map<String, Object> valuesMap = (Map<String, Object>) writer.root();

    List<String> keys = Arrays.asList("a", "b", "c", "d");
    List<Object> values = Arrays.asList(singletonList("value"), 2.0d, 3L, null);

    assertEquals(singletonList("value"), valuesMap.get("a"));
    assertEquals(2.0d, valuesMap.get("b"));
    assertEquals(3L, valuesMap.get("c"));
    assertEquals(null, valuesMap.get("d"));
    assertTrue(valuesMap.keySet().containsAll(keys));
    assertTrue(keys.containsAll(valuesMap.keySet()));
    assertTrue(values.containsAll(valuesMap.values()));
    assertTrue(valuesMap.values().containsAll(values));
  }

  /**
   * Returns an instance of number whose {@link #toString} is {@code s}. Using the standard number
   * methods like {@link Number#doubleValue} are awkward because they may truncate or discard
   * precision.
   */
  private Number stringNumber(final String s) {
    return new Number() {
      @Override
      public int intValue() {
        throw new AssertionError();
      }

      @Override
      public long longValue() {
        throw new AssertionError();
      }

      @Override
      public float floatValue() {
        throw new AssertionError();
      }

      @Override
      public double doubleValue() {
        throw new AssertionError();
      }

      @Override
      public String toString() {
        return s;
      }
    };
  }
}
