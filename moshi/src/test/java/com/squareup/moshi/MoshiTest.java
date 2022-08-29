/*
 * Copyright (C) 2014 Square, Inc.
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

import static com.squareup.moshi.TestUtil.newReader;
import static com.squareup.moshi.TestUtil.repeat;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.util.Pair;
import com.squareup.moshi.internal.Util;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.crypto.KeyGenerator;
import okio.Buffer;
import org.junit.Test;

@SuppressWarnings({"CheckReturnValue", "ResultOfMethodCallIgnored"})
public final class MoshiTest {
  @Test
  public void booleanAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Boolean> adapter = moshi.adapter(boolean.class).lenient();
    assertTrue(adapter.fromJson("true"));
    assertTrue(adapter.fromJson("TRUE"));
    assertEquals(adapter.toJson(true), "true");
    assertFalse(adapter.fromJson("false"));
    assertFalse(adapter.fromJson("FALSE"));
    assertEquals(adapter.toJson(false), "false");

    // Nulls not allowed for boolean.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a boolean but was NULL at path $"));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void BooleanAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Boolean> adapter = moshi.adapter(Boolean.class).lenient();
    assertTrue(adapter.fromJson("true"));
    assertEquals(adapter.toJson(true), "true");
    assertFalse(adapter.fromJson("false"));
    assertEquals(adapter.toJson(false), "false");
    // Allow nulls for Boolean.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void byteAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Byte> adapter = moshi.adapter(byte.class).lenient();
    assertEquals(Objects.requireNonNull(adapter.fromJson("1")).byteValue(), (byte) 1);
    assertEquals(adapter.toJson((byte) -2), "254");

    // Canonical byte representation is unsigned, but parse the whole range -128..255
    assertEquals(Objects.requireNonNull(adapter.fromJson("-128")).byteValue(), (byte) -128);
    assertEquals(Objects.requireNonNull(adapter.fromJson("128")).byteValue(), (byte) -128);
    assertEquals(adapter.toJson((byte) -128), "128");

    assertEquals(adapter.fromJson("255").byteValue(), (byte) -1);
    assertEquals(adapter.toJson((byte) -1), "255");

    assertEquals(Objects.requireNonNull(adapter.fromJson("127")).byteValue(), (byte) 127);
    assertEquals(adapter.toJson((byte) 127), "127");

    try {
      adapter.fromJson("256");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a byte but was 256 at path $"));
    }

    try {
      adapter.fromJson("-129");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a byte but was -129 at path "));
    }

    // Nulls not allowed for byte.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected an int but was NULL at path "));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void ByteAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Byte> adapter = moshi.adapter(Byte.class).lenient();
    assertEquals(adapter.fromJson("1").byteValue(), (byte) 1);
    assertEquals(adapter.toJson((byte) -2), "254");
    // Allow nulls for Byte.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void charAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Character> adapter = moshi.adapter(char.class).lenient();
    assertEquals(adapter.fromJson("\"a\"").charValue(), 'a');
    assertEquals(adapter.fromJson("'a'").charValue(), 'a');
    assertEquals(adapter.toJson('b'), "\"b\"");

    // Exhaustively test all valid characters.  Use an int to loop so we can check termination.
    for (int i = 0; i <= Character.MAX_VALUE; ++i) {
      final char c = (char) i;
      String s;
      switch (c) {
          // TODO: make JsonWriter.REPLACEMENT_CHARS visible for testing?
        case '\"':
          s = "\\\"";
          break;
        case '\\':
          s = "\\\\";
          break;
        case '\t':
          s = "\\t";
          break;
        case '\b':
          s = "\\b";
          break;
        case '\n':
          s = "\\n";
          break;
        case '\r':
          s = "\\r";
          break;
        case '\f':
          s = "\\f";
          break;
        case '\u2028':
          s = "\\u2028";
          break;
        case '\u2029':
          s = "\\u2029";
          break;
        default:
          if (c <= 0x1f) {
            s = String.format("\\u%04x", (int) c);
          } else if (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE) {
            // TODO: not handled properly; do we need to?
            continue;
          } else {
            s = String.valueOf(c);
          }
          break;
      }
      s = '"' + s + '"';
      assertEquals(adapter.toJson(c), s);
      assertEquals(adapter.fromJson(s).charValue(), c);
    }

    try {
      // Only a single character is allowed.
      adapter.fromJson("'ab'");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a char but was \"ab\" at path "));
    }

    // Nulls not allowed for char.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a string but was NULL at path "));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void CharacterAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Character> adapter = moshi.adapter(Character.class).lenient();
    assertEquals(adapter.fromJson("\"a\"").charValue(), 'a');
    assertEquals(adapter.fromJson("'a'").charValue(), 'a');
    assertEquals(adapter.toJson('b'), "\"b\"");

    try {
      // Only a single character is allowed.
      adapter.fromJson("'ab'");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a char but was \"ab\" at path "));
    }

    // Allow nulls for Character.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void doubleAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Double> adapter = moshi.adapter(double.class).lenient();
    assertEquals(adapter.fromJson("1.0"), 1.0, 0);
    assertEquals(adapter.fromJson("1"), 1.0, 0);
    assertEquals(adapter.fromJson("1e0"), 1.0, 0);
    assertEquals(adapter.toJson(-2.0), "-2.0");

    // Test min/max values.
    assertEquals(adapter.fromJson("-1.7976931348623157E308"), -Double.MAX_VALUE, 0);
    assertEquals(adapter.toJson(-Double.MAX_VALUE), "-1.7976931348623157E308");
    assertEquals(adapter.fromJson("1.7976931348623157E308"), Double.MAX_VALUE, 0);
    assertEquals(adapter.toJson(Double.MAX_VALUE), "1.7976931348623157E308");

    // Lenient reader converts too large values to infinities.
    assertEquals(adapter.fromJson("1E309"), Double.POSITIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("-1E309"), Double.NEGATIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("+Infinity"), Double.POSITIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("Infinity"), Double.POSITIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("-Infinity"), Double.NEGATIVE_INFINITY, 0);

    // Nulls not allowed for double.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a double but was NULL at path "));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }

    // Non-lenient adapter won't allow values outside of range.
    adapter = moshi.adapter(double.class);
    JsonReader reader = newReader("[1E309]");
    reader.beginArray();
    try {
      adapter.fromJson(reader);
      fail();
    } catch (IOException expected) {
      assertTrue(
          expected.getMessage().contains("JSON forbids NaN and infinities: Infinity at path $[0]"));
    }

    reader = newReader("[-1E309]");
    reader.beginArray();
    try {
      adapter.fromJson(reader);
      fail();
    } catch (IOException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("JSON forbids NaN and infinities: -Infinity at path $[0]"));
    }
  }

  @Test
  public void DoubleAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Double> adapter = moshi.adapter(Double.class).lenient();
    assertEquals(adapter.fromJson("1.0"), 1.0, 0);
    assertEquals(adapter.fromJson("1"), 1.0, 0);
    assertEquals(adapter.fromJson("1e0"), 1.0, 0);
    assertEquals(adapter.toJson(-2.0), "-2.0");
    // Allow nulls for Double.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void floatAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Float> adapter = moshi.adapter(float.class).lenient();
    assertEquals(adapter.fromJson("1.0"), 1.0f, 0);
    assertEquals(adapter.fromJson("1"), 1.0f, 0);
    assertEquals(adapter.fromJson("1e0"), 1.0f, 0);
    assertEquals(adapter.toJson(-2.0f), "-2.0");

    // Test min/max values.
    assertEquals(adapter.fromJson("-3.4028235E38"), -Float.MAX_VALUE, 0);
    assertEquals(adapter.toJson(-Float.MAX_VALUE), "-3.4028235E38");
    assertEquals(adapter.fromJson("3.4028235E38"), Float.MAX_VALUE, 0);
    assertEquals(adapter.toJson(Float.MAX_VALUE), "3.4028235E38");

    // Lenient reader converts too large values to infinities.
    assertEquals(adapter.fromJson("1E39"), Float.POSITIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("-1E39"), Float.NEGATIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("+Infinity"), Float.POSITIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("Infinity"), Float.POSITIVE_INFINITY, 0);
    assertEquals(adapter.fromJson("-Infinity"), Float.NEGATIVE_INFINITY, 0);

    // Nulls not allowed for float.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a double but was NULL at path "));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }

    // Non-lenient adapter won't allow values outside of range.
    adapter = moshi.adapter(float.class);
    JsonReader reader = newReader("[1E39]");
    reader.beginArray();
    try {
      adapter.fromJson(reader);
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected.getMessage().contains("JSON forbids NaN and infinities: Infinity at path $[1]"));
    }

    reader = newReader("[-1E39]");
    reader.beginArray();
    try {
      adapter.fromJson(reader);
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("JSON forbids NaN and infinities: -Infinity at path $[1]"));
    }
  }

  @Test
  public void FloatAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Float> adapter = moshi.adapter(Float.class).lenient();
    assertEquals(adapter.fromJson("1.0"), 1.0f, 0);
    assertEquals(adapter.fromJson("1"), 1.0f, 0);
    assertEquals(adapter.fromJson("1e0"), 1.0f, 0);
    assertEquals(adapter.toJson(-2.0f), "-2.0");
    // Allow nulls for Float.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void intAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Integer> adapter = moshi.adapter(int.class).lenient();
    assertEquals(adapter.fromJson("1"), 1, 0);
    assertEquals(adapter.toJson(-2), "-2");

    // Test min/max values
    assertEquals(adapter.fromJson("-2147483648"), Integer.MIN_VALUE, 0);
    assertEquals(adapter.toJson(Integer.MIN_VALUE), "-2147483648");
    assertEquals(adapter.fromJson("2147483647"), Integer.MAX_VALUE, 0);
    assertEquals(adapter.toJson(Integer.MAX_VALUE), "2147483647");

    try {
      adapter.fromJson("2147483648");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected an int but was 2147483648 at path "));
    }

    try {
      adapter.fromJson("-2147483649");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected an int but was -2147483649 at path "));
    }

    // Nulls not allowed for int.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected an int but was NULL at path "));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void IntegerAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Integer> adapter = moshi.adapter(Integer.class).lenient();
    assertEquals(adapter.fromJson("1"), 1, 0);
    assertEquals(adapter.toJson(-2), "-2");
    // Allow nulls for Integer.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void longAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Long> adapter = moshi.adapter(long.class).lenient();
    assertEquals(adapter.fromJson("1"), 1L, 0);
    assertEquals(adapter.toJson(-2L), "-2");

    // Test min/max values
    assertEquals(adapter.fromJson("-9223372036854775808"), Long.MIN_VALUE, 0);
    assertEquals(adapter.toJson(Long.MIN_VALUE), "-9223372036854775808");
    assertEquals(adapter.fromJson("9223372036854775807"), Long.MAX_VALUE, 0);
    assertEquals(adapter.toJson(Long.MAX_VALUE), "9223372036854775807");

    try {
      adapter.fromJson("9223372036854775808");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected.getMessage().contains("Expected a long but was 9223372036854775808 at path "));
    }

    try {
      adapter.fromJson("-9223372036854775809");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected.getMessage().contains("Expected a long but was -9223372036854775809 at path "));
    }

    // Nulls not allowed for long.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a long but was NULL at path "));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void LongAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Long> adapter = moshi.adapter(Long.class).lenient();
    assertEquals(adapter.fromJson("1"), 1L, 0);
    assertEquals(adapter.toJson(-2L), "-2");
    // Allow nulls for Integer.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void shortAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Short> adapter = moshi.adapter(short.class).lenient();
    assertEquals(adapter.fromJson("1"), (short) 1, 0);
    assertEquals(adapter.toJson((short) -2), "-2");

    // Test min/max values.
    assertEquals(adapter.fromJson("-32768"), Short.MIN_VALUE, 0);
    assertEquals(adapter.toJson(Short.MIN_VALUE), "-32768");
    assertEquals(adapter.fromJson("32767"), Short.MAX_VALUE, 0);
    assertEquals(adapter.toJson(Short.MAX_VALUE), "32767");

    try {
      adapter.fromJson("32768");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a short but was 32768 at path "));
    }

    try {
      adapter.fromJson("-32769");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected a short but was -32769 at path "));
    }

    // Nulls not allowed for short.class
    try {
      adapter.fromJson("null");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Expected an int but was NULL at path "));
    }

    try {
      adapter.toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void ShortAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Short> adapter = moshi.adapter(Short.class).lenient();
    assertEquals(adapter.fromJson("1"), (short) 1, 0);
    assertEquals(adapter.toJson((short) -2), "-2");
    // Allow nulls for Byte.class
    assertEquals(adapter.fromJson("null"), null);
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void stringAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<String> adapter = moshi.adapter(String.class).lenient();
    assertEquals(adapter.fromJson("\"a\""), "a");
    assertEquals(adapter.toJson("b"), "\"b\"");
    assertNull(adapter.fromJson("null"));
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void upperBoundedWildcardsAreHandled() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Types.subtypeOf(String.class));
    assertEquals(adapter.fromJson("\"a\""), "a");
    assertEquals(adapter.toJson("b"), "\"b\"");
    assertNull(adapter.fromJson("null"));
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void lowerBoundedWildcardsAreNotHandled() {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(Types.supertypeOf(String.class));
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains("No JsonAdapter for ? super java.lang.String (with no annotations)"));
    }
  }

  @Test
  public void addNullFails() throws Exception {
    Type type = Object.class;
    Class<? extends Annotation> annotation = Annotation.class;
    Moshi.Builder builder = new Moshi.Builder();
    try {
      builder.add((null));
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
    try {
      builder.add((Object) null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
    try {
      builder.add(null, null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
    try {
      builder.add(type, null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
    try {
      builder.add(null, null, null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
    try {
      builder.add(type, null, null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
    try {
      builder.add(type, annotation, null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
  }

  @Test
  public void customJsonAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().add(Pizza.class, new PizzaAdapter()).build();

    JsonAdapter<Pizza> jsonAdapter = moshi.adapter(Pizza.class);
    assertEquals(jsonAdapter.toJson(new Pizza(15, true)), "{\"size\":15,\"extra cheese\":true}");
    assertEquals(jsonAdapter.fromJson("{\"extra cheese\":true,\"size\":18}"), new Pizza(18, true));
  }

  @Test
  public void classAdapterToObjectAndFromObject() throws Exception {
    Moshi moshi = new Moshi.Builder().build();

    Pizza pizza = new Pizza(15, true);

    Map<String, Object> pizzaObject = new LinkedHashMap<>();
    pizzaObject.put("diameter", 15L);
    pizzaObject.put("extraCheese", true);

    JsonAdapter<Pizza> jsonAdapter = moshi.adapter(Pizza.class);
    assertEquals(jsonAdapter.toJsonValue(pizza), pizzaObject);
    assertEquals(jsonAdapter.fromJsonValue(pizzaObject), pizza);
  }

  @Test
  public void customJsonAdapterToObjectAndFromObject() throws Exception {
    Moshi moshi = new Moshi.Builder().add(Pizza.class, new PizzaAdapter()).build();

    Pizza pizza = new Pizza(15, true);

    Map<String, Object> pizzaObject = new LinkedHashMap<>();
    pizzaObject.put("size", 15L);
    pizzaObject.put("extra cheese", true);

    JsonAdapter<Pizza> jsonAdapter = moshi.adapter(Pizza.class);
    assertEquals(jsonAdapter.toJsonValue(pizza), pizzaObject);
    assertEquals(jsonAdapter.fromJsonValue(pizzaObject), pizza);
  }

  @Test
  public void indent() throws Exception {
    Moshi moshi = new Moshi.Builder().add(Pizza.class, new PizzaAdapter()).build();
    JsonAdapter<Pizza> jsonAdapter = moshi.adapter(Pizza.class);

    Pizza pizza = new Pizza(15, true);
    assertEquals(
        jsonAdapter.indent("  ").toJson(pizza),
        "" + "{\n" + "  \"size\": 15,\n" + "  \"extra cheese\": true\n" + "}");
  }

  @Test
  public void unindent() throws Exception {
    Moshi moshi = new Moshi.Builder().add(Pizza.class, new PizzaAdapter()).build();
    JsonAdapter<Pizza> jsonAdapter = moshi.adapter(Pizza.class);

    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.setLenient(true);
    writer.setIndent("  ");

    Pizza pizza = new Pizza(15, true);

    // Calling JsonAdapter.indent("") can remove indentation.
    jsonAdapter.indent("").toJson(writer, pizza);
    assertEquals(buffer.readUtf8(), "{\"size\":15,\"extra cheese\":true}");

    // Indentation changes only apply to their use.
    jsonAdapter.toJson(writer, pizza);
    assertEquals(
        buffer.readUtf8(), "" + "{\n" + "  \"size\": 15,\n" + "  \"extra cheese\": true\n" + "}");
  }

  @Test
  public void composingJsonAdapterFactory() throws Exception {
    Moshi moshi =
        new Moshi.Builder()
            .add(new MealDealAdapterFactory())
            .add(Pizza.class, new PizzaAdapter())
            .build();

    JsonAdapter<MealDeal> jsonAdapter = moshi.adapter(MealDeal.class);
    assertEquals(
        jsonAdapter.toJson(new MealDeal(new Pizza(15, true), "Pepsi")),
        "[{\"size\":15,\"extra cheese\":true},\"Pepsi\"]");
    assertEquals(
        jsonAdapter.fromJson("[{\"extra cheese\":true,\"size\":18},\"Coke\"]"),
        new MealDeal(new Pizza(18, true), "Coke"));
  }

  static class Message {
    String speak;
    @Uppercase String shout;
  }

  @Test
  public void registerJsonAdapterForAnnotatedType() throws Exception {
    JsonAdapter<String> uppercaseAdapter =
        new JsonAdapter<String>() {
          @Override
          public String fromJson(JsonReader reader) throws IOException {
            throw new AssertionError();
          }

          @Override
          public void toJson(JsonWriter writer, String value) throws IOException {
            writer.value(value.toUpperCase(Locale.US));
          }
        };

    Moshi moshi = new Moshi.Builder().add(String.class, Uppercase.class, uppercaseAdapter).build();

    JsonAdapter<Message> messageAdapter = moshi.adapter(Message.class);

    Message message = new Message();
    message.speak = "Yo dog";
    message.shout = "What's up";

    assertEquals(messageAdapter.toJson(message), "{\"shout\":\"WHAT'S UP\",\"speak\":\"Yo dog\"}");
  }

  @Test
  public void adapterLookupDisallowsNullType() {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(null, Collections.<Annotation>emptySet());
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
  }

  @Test
  public void adapterLookupDisallowsNullAnnotations() {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(String.class, (Class<? extends Annotation>) null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
    try {
      moshi.adapter(String.class, (Set<? extends Annotation>) null);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
  }

  @Test
  public void nextJsonAdapterDisallowsNullAnnotations() throws Exception {
    JsonAdapter.Factory badFactory =
        new JsonAdapter.Factory() {
          @Nullable
          @Override
          public JsonAdapter<?> create(
              Type type, Set<? extends Annotation> annotations, Moshi moshi) {
            return moshi.nextAdapter(this, type, null);
          }
        };
    Moshi moshi = new Moshi.Builder().add(badFactory).build();
    try {
      moshi.adapter(Object.class);
      fail();
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("Parameter specified as non-null is null"));
    }
  }

  @Uppercase static String uppercaseString;

  @Test
  public void delegatingJsonAdapterFactory() throws Exception {
    Moshi moshi = new Moshi.Builder().add(new UppercaseAdapterFactory()).build();

    Field uppercaseString = MoshiTest.class.getDeclaredField("uppercaseString");
    Set<? extends Annotation> annotations = Util.getJsonAnnotations(uppercaseString);
    JsonAdapter<String> adapter = moshi.<String>adapter(String.class, annotations).lenient();
    assertEquals(adapter.toJson("a"), "\"A\"");
    assertEquals(adapter.fromJson("\"b\""), "B");
  }

  @Test
  public void listJsonAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<List<String>> adapter =
        moshi.adapter(Types.newParameterizedType(List.class, String.class));
    assertEquals(adapter.toJson(Arrays.asList("a", "b")), "[\"a\",\"b\"]");
    assertEquals(adapter.fromJson("[\"a\",\"b\"]"), Arrays.asList("a", "b"));
  }

  @Test
  public void setJsonAdapter() throws Exception {
    Set<String> set = new LinkedHashSet<>();
    set.add("a");
    set.add("b");

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Set<String>> adapter =
        moshi.adapter(Types.newParameterizedType(Set.class, String.class));
    assertEquals(adapter.toJson(set), "[\"a\",\"b\"]");
    assertEquals(adapter.fromJson("[\"a\",\"b\"]"), set);
  }

  @Test
  public void collectionJsonAdapter() throws Exception {
    Collection<String> collection = new ArrayDeque<>();
    collection.add("a");
    collection.add("b");

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Collection<String>> adapter =
        moshi.adapter(Types.newParameterizedType(Collection.class, String.class));
    Object[] values = adapter.fromJson("[\"a\",\"b\"]").toArray();
    assertEquals(adapter.toJson(collection), "[\"a\",\"b\"]");
    assertEquals(values.length, 2);
    assertEquals(values[0], "a");
    assertEquals(values[1], "b");
  }

  @Uppercase static List<String> uppercaseStrings;

  @Test
  public void collectionsDoNotKeepAnnotations() throws Exception {
    Moshi moshi = new Moshi.Builder().add(new UppercaseAdapterFactory()).build();

    Field uppercaseStringsField = MoshiTest.class.getDeclaredField("uppercaseStrings");
    try {
      moshi.adapter(
          uppercaseStringsField.getGenericType(), Util.getJsonAnnotations(uppercaseStringsField));
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains(
                  "No JsonAdapter for java.util.List<java.lang.String> "
                      + "annotated [@com.squareup.moshi.MoshiTest$Uppercase()]"));
    }
  }

  @Test
  public void noTypeAdapterForQualifiedPlatformType() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    Field uppercaseStringField = MoshiTest.class.getDeclaredField("uppercaseString");
    try {
      moshi.adapter(
          uppercaseStringField.getGenericType(), Util.getJsonAnnotations(uppercaseStringField));
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains(
                  "No JsonAdapter for class java.lang.String "
                      + "annotated [@com.squareup.moshi.MoshiTest$Uppercase()]"));
    }
  }

  @Test
  public void objectArray() throws IOException {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<String[]> adapter = moshi.adapter(String[].class);
    assertEquals(adapter.toJson(new String[] {"a", "b"}), "[\"a\",\"b\"]");
    String[] values = adapter.fromJson("[\"a\",\"b\"]");

    assertEquals("a", values[0]);
    assertEquals("b", values[1]);
  }

  @Test
  public void primitiveArray() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<int[]> adapter = moshi.adapter(int[].class);
    assertEquals(adapter.toJson(new int[] {1, 2}), "[1,2]");
    int[] values = adapter.fromJson("[2,3]");

    assertEquals(2, values[0]);
    assertEquals(3, values[1]);
  }

  @Test
  public void enumAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class).lenient();
    assertEquals(adapter.fromJson("\"ROCK\""), Roshambo.ROCK);
    assertEquals(adapter.toJson(Roshambo.PAPER), "\"PAPER\"");
  }

  @Test
  public void annotatedEnum() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class).lenient();
    assertEquals(adapter.fromJson("\"scr\""), Roshambo.SCISSORS);
    assertEquals(adapter.toJson(Roshambo.SCISSORS), "\"scr\"");
  }

  @Test
  public void invalidEnum() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class);
    try {
      adapter.fromJson("\"SPOCK\"");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("Expected one of [ROCK, PAPER, scr] but was SPOCK at path "));
    }
  }

  @Test
  public void invalidEnumHasCorrectPathInExceptionMessage() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class);
    JsonReader reader = JsonReader.of(new Buffer().writeUtf8("[\"SPOCK\"]"));
    reader.beginArray();
    try {
      adapter.fromJson(reader);
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("Expected one of [ROCK, PAPER, scr] but was SPOCK at path $[0]"));
    }
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void nullEnum() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class).lenient();
    assertNull(adapter.fromJson("null"));
    assertEquals(adapter.toJson(null), "null");
  }

  @Test
  public void byDefaultUnknownFieldsAreIgnored() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Pizza> adapter = moshi.adapter(Pizza.class);
    Pizza pizza = adapter.fromJson("{\"diameter\":5,\"crust\":\"thick\",\"extraCheese\":true}");
    assertEquals(pizza.diameter, 5);
    assertEquals(pizza.extraCheese, true);
  }

  @Test
  public void failOnUnknownThrowsOnUnknownFields() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Pizza> adapter = moshi.adapter(Pizza.class).failOnUnknown();
    try {
      adapter.fromJson("{\"diameter\":5,\"crust\":\"thick\",\"extraCheese\":true}");
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Cannot skip unexpected NAME at $.crust"));
    }
  }

  @Test
  public void platformTypeThrows() throws IOException {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(File.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "Platform class java.io.File requires explicit JsonAdapter to be registered"));
    }
    try {
      moshi.adapter(KeyGenerator.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "Platform class javax.crypto.KeyGenerator requires explicit "
                      + "JsonAdapter to be registered"));
    }
    try {
      moshi.adapter(Pair.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "Platform class android.util.Pair requires explicit JsonAdapter to be registered"));
    }
  }

  @Test
  public void collectionClassesHaveClearErrorMessage() {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(Types.newParameterizedType(ArrayList.class, String.class));
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "No JsonAdapter for "
                      + "java.util.ArrayList<java.lang.String>, "
                      + "you should probably use List instead of ArrayList "
                      + "(Moshi only supports the collection interfaces by default) "
                      + "or else register a custom JsonAdapter."));
    }

    try {
      moshi.adapter(Types.newParameterizedType(HashMap.class, String.class, String.class));
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "No JsonAdapter for "
                      + "java.util.HashMap<java.lang.String, java.lang.String>, "
                      + "you should probably use Map instead of HashMap "
                      + "(Moshi only supports the collection interfaces by default) "
                      + "or else register a custom JsonAdapter."));
    }
  }

  @Test
  public void noCollectionErrorIfAdapterExplicitlyProvided() {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                new JsonAdapter.Factory() {
                  @Override
                  public JsonAdapter<?> create(
                      Type type, Set<? extends Annotation> annotations, Moshi moshi) {
                    return new MapJsonAdapter<String, String>(moshi, String.class, String.class);
                  }
                })
            .build();

    JsonAdapter<HashMap<String, String>> adapter =
        moshi.adapter(Types.newParameterizedType(HashMap.class, String.class, String.class));
    assertTrue(adapter instanceof MapJsonAdapter);
  }

  static final class HasPlatformType {
    UUID uuid;

    static final class Wrapper {
      HasPlatformType hasPlatformType;
    }

    static final class ListWrapper {
      List<HasPlatformType> platformTypes;
    }
  }

  @Test
  public void reentrantFieldErrorMessagesTopLevelMap() {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(Types.newParameterizedType(Map.class, String.class, HasPlatformType.class));
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "Platform class java.util.UUID requires explicit "
                      + "JsonAdapter to be registered"
                      + "\nfor class java.util.UUID uuid"
                      + "\nfor class com.squareup.moshi.MoshiTest$HasPlatformType"
                      + "\nfor java.util.Map<java.lang.String, "
                      + "com.squareup.moshi.MoshiTest$HasPlatformType>"));
      assertTrue(e.getCause() instanceof IllegalArgumentException);
      assertTrue(
          e.getCause()
              .getMessage()
              .contains(
                  "Platform class java.util.UUID "
                      + "requires explicit JsonAdapter to be registered"));
    }
  }

  @Test
  public void reentrantFieldErrorMessagesWrapper() {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(HasPlatformType.Wrapper.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "Platform class java.util.UUID requires explicit "
                      + "JsonAdapter to be registered"
                      + "\nfor class java.util.UUID uuid"
                      + "\nfor class com.squareup.moshi.MoshiTest$HasPlatformType hasPlatformType"
                      + "\nfor class com.squareup.moshi.MoshiTest$HasPlatformType$Wrapper"));
      assertTrue(e.getCause() instanceof IllegalArgumentException);
      assertTrue(
          e.getCause()
              .getMessage()
              .contains(
                  "Platform class java.util.UUID "
                      + "requires explicit JsonAdapter to be registered"));
    }
  }

  @Test
  public void reentrantFieldErrorMessagesListWrapper() {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(HasPlatformType.ListWrapper.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "Platform class java.util.UUID requires explicit "
                      + "JsonAdapter to be registered"
                      + "\nfor class java.util.UUID uuid"
                      + "\nfor class com.squareup.moshi.MoshiTest$HasPlatformType"
                      + "\nfor java.util.List<com.squareup.moshi.MoshiTest$HasPlatformType> platformTypes"
                      + "\nfor class com.squareup.moshi.MoshiTest$HasPlatformType$ListWrapper"));
      assertTrue(e.getCause() instanceof IllegalArgumentException);
      assertTrue(
          e.getCause()
              .getMessage()
              .contains(
                  "Platform class java.util.UUID "
                      + "requires explicit JsonAdapter to be registered"));
    }
  }

  @Test
  public void qualifierWithElementsMayNotBeDirectlyRegistered() throws IOException {
    try {
      new Moshi.Builder()
          .add(
              Boolean.class,
              Localized.class,
              StandardJsonAdapters.INSTANCE.getBOOLEAN_JSON_ADAPTER());
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected.getMessage().contains("Use JsonAdapter.Factory for annotations with elements"));
    }
  }

  @Test
  public void qualifierWithElements() throws IOException {
    Moshi moshi = new Moshi.Builder().add(LocalizedBooleanAdapter.FACTORY).build();

    Baguette baguette = new Baguette();
    baguette.avecBeurre = true;
    baguette.withButter = true;

    JsonAdapter<Baguette> adapter = moshi.adapter(Baguette.class);
    assertEquals(adapter.toJson(baguette), "{\"avecBeurre\":\"oui\",\"withButter\":\"yes\"}");

    Baguette decoded = adapter.fromJson("{\"avecBeurre\":\"oui\",\"withButter\":\"yes\"}");
    assertTrue(decoded.avecBeurre);
    assertTrue(decoded.withButter);
  }

  /** Note that this is the opposite of Gson's behavior, where later adapters are preferred. */
  @Test
  public void adaptersRegisteredInOrderOfPrecedence() throws Exception {
    JsonAdapter<String> adapter1 =
        new JsonAdapter<String>() {
          @Override
          public String fromJson(JsonReader reader) throws IOException {
            throw new AssertionError();
          }

          @Override
          public void toJson(JsonWriter writer, String value) throws IOException {
            writer.value("one!");
          }
        };

    JsonAdapter<String> adapter2 =
        new JsonAdapter<String>() {
          @Override
          public String fromJson(JsonReader reader) throws IOException {
            throw new AssertionError();
          }

          @Override
          public void toJson(JsonWriter writer, String value) throws IOException {
            writer.value("two!");
          }
        };

    Moshi moshi =
        new Moshi.Builder().add(String.class, adapter1).add(String.class, adapter2).build();
    JsonAdapter<String> adapter = moshi.adapter(String.class).lenient();
    assertEquals(adapter.toJson("a"), "\"one!\"");
  }

  @Test
  public void cachingJsonAdapters() {
    Moshi moshi = new Moshi.Builder().build();

    JsonAdapter<MealDeal> adapter1 = moshi.adapter(MealDeal.class);
    JsonAdapter<MealDeal> adapter2 = moshi.adapter(MealDeal.class);
    assertEquals(adapter1.getClass().getName(), adapter2.getClass().getName());
    assertEquals(adapter1.getClass(), adapter2.getClass());
  }

  @Test
  public void newBuilder() {
    Moshi moshi = new Moshi.Builder().add(Pizza.class, new PizzaAdapter()).build();
    Moshi.Builder newBuilder = moshi.newBuilder();
    for (JsonAdapter.Factory factory : Moshi.BUILT_IN_FACTORIES) {
      // Awkward but java sources don't know about the internal-ness of this
      assertFalse(newBuilder.getFactories$moshi().contains(factory));
    }
  }

  @Test
  public void referenceCyclesOnArrays() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("a", map);
    try {
      moshi.adapter(Object.class).toJson(map);
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("Nesting too deep at $" + repeat(".a", 255) + ": circular reference?"));
    }
  }

  @Test
  public void referenceCyclesOnObjects() {
    Moshi moshi = new Moshi.Builder().build();
    List<Object> list = new ArrayList<>();
    list.add(list);
    try {
      moshi.adapter(Object.class).toJson(list);
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("Nesting too deep at $" + repeat("[0]", 255) + ": circular reference?"));
    }
  }

  @Test
  public void referenceCyclesOnMixedTypes() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new LinkedHashMap<>();
    list.add(map);
    map.put("a", list);
    try {
      moshi.adapter(Object.class).toJson(list);
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains(
                  "Nesting too deep at $[0]" + repeat(".a[0]", 127) + ": circular reference?"));
    }
  }

  @Test
  public void duplicateKeyDisallowedInObjectType() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    String json = "{\"diameter\":5,\"diameter\":5,\"extraCheese\":true}";
    try {
      adapter.fromJson(json);
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("Map key 'diameter' has multiple values at path $.diameter: 5.0 and 5.0"));
    }
  }

  @Test
  public void duplicateKeysAllowedInCustomType() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Pizza> adapter = moshi.adapter(Pizza.class);
    String json = "{\"diameter\":5,\"diameter\":5,\"extraCheese\":true}";
    assertEquals(adapter.fromJson(json), new Pizza(5, true));
  }

  @Test
  public void precedence() throws Exception {
    Moshi moshi =
        new Moshi.Builder()
            .add(new AppendingAdapterFactory(" a"))
            .addLast(new AppendingAdapterFactory(" y"))
            .add(new AppendingAdapterFactory(" b"))
            .addLast(new AppendingAdapterFactory(" z"))
            .build();
    JsonAdapter<String> adapter = moshi.adapter(String.class).lenient();
    assertEquals(adapter.toJson("hello"), "\"hello a b y z\"");
  }

  @Test
  public void precedenceWithNewBuilder() throws Exception {
    Moshi moshi1 =
        new Moshi.Builder()
            .add(new AppendingAdapterFactory(" a"))
            .addLast(new AppendingAdapterFactory(" w"))
            .add(new AppendingAdapterFactory(" b"))
            .addLast(new AppendingAdapterFactory(" x"))
            .build();
    Moshi moshi2 =
        moshi1
            .newBuilder()
            .add(new AppendingAdapterFactory(" c"))
            .addLast(new AppendingAdapterFactory(" y"))
            .add(new AppendingAdapterFactory(" d"))
            .addLast(new AppendingAdapterFactory(" z"))
            .build();

    JsonAdapter<String> adapter = moshi2.adapter(String.class).lenient();
    assertEquals(adapter.toJson("hello"), "\"hello a b c d w x y z\"");
  }

  /** Adds a suffix to a string before emitting it. */
  static final class AppendingAdapterFactory implements JsonAdapter.Factory {
    private final String suffix;

    AppendingAdapterFactory(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (type != String.class) return null;

      final JsonAdapter<String> delegate = moshi.nextAdapter(this, type, annotations);
      return new JsonAdapter<String>() {
        @Override
        public String fromJson(JsonReader reader) throws IOException {
          throw new AssertionError();
        }

        @Override
        public void toJson(JsonWriter writer, String value) throws IOException {
          delegate.toJson(writer, value + suffix);
        }
      };
    }
  }

  static class Pizza {
    final int diameter;
    final boolean extraCheese;

    Pizza(int diameter, boolean extraCheese) {
      this.diameter = diameter;
      this.extraCheese = extraCheese;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Pizza
          && ((Pizza) o).diameter == diameter
          && ((Pizza) o).extraCheese == extraCheese;
    }

    @Override
    public int hashCode() {
      return diameter * (extraCheese ? 31 : 1);
    }
  }

  static class MealDeal {
    final Pizza pizza;
    final String drink;

    MealDeal(Pizza pizza, String drink) {
      this.pizza = pizza;
      this.drink = drink;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MealDeal
          && ((MealDeal) o).pizza.equals(pizza)
          && ((MealDeal) o).drink.equals(drink);
    }

    @Override
    public int hashCode() {
      return pizza.hashCode() + (31 * drink.hashCode());
    }
  }

  static class PizzaAdapter extends JsonAdapter<Pizza> {
    @Override
    public Pizza fromJson(JsonReader reader) throws IOException {
      int diameter = 13;
      boolean extraCheese = false;
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (name.equals("size")) {
          diameter = reader.nextInt();
        } else if (name.equals("extra cheese")) {
          extraCheese = reader.nextBoolean();
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return new Pizza(diameter, extraCheese);
    }

    @Override
    public void toJson(JsonWriter writer, Pizza value) throws IOException {
      writer.beginObject();
      writer.name("size").value(value.diameter);
      writer.name("extra cheese").value(value.extraCheese);
      writer.endObject();
    }
  }

  static class MealDealAdapterFactory implements JsonAdapter.Factory {
    @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (!type.equals(MealDeal.class)) return null;
      final JsonAdapter<Pizza> pizzaAdapter = moshi.adapter(Pizza.class);
      final JsonAdapter<String> drinkAdapter = moshi.adapter(String.class);
      return new JsonAdapter<MealDeal>() {
        @Override
        public MealDeal fromJson(JsonReader reader) throws IOException {
          reader.beginArray();
          Pizza pizza = pizzaAdapter.fromJson(reader);
          String drink = drinkAdapter.fromJson(reader);
          reader.endArray();
          return new MealDeal(pizza, drink);
        }

        @Override
        public void toJson(JsonWriter writer, MealDeal value) throws IOException {
          writer.beginArray();
          pizzaAdapter.toJson(writer, value.pizza);
          drinkAdapter.toJson(writer, value.drink);
          writer.endArray();
        }
      };
    }
  }

  @Retention(RUNTIME)
  @JsonQualifier
  public @interface Uppercase {}

  static class UppercaseAdapterFactory implements JsonAdapter.Factory {
    @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (!type.equals(String.class)) return null;
      if (!Util.isAnnotationPresent(annotations, Uppercase.class)) return null;

      final JsonAdapter<String> stringAdapter =
          moshi.nextAdapter(this, String.class, Util.NO_ANNOTATIONS);
      return new JsonAdapter<String>() {
        @Override
        public String fromJson(JsonReader reader) throws IOException {
          String s = stringAdapter.fromJson(reader);
          return s.toUpperCase(Locale.US);
        }

        @Override
        public void toJson(JsonWriter writer, String value) throws IOException {
          stringAdapter.toJson(writer, value.toUpperCase());
        }
      };
    }
  }

  enum Roshambo {
    ROCK,
    PAPER,
    @Json(name = "scr")
    SCISSORS
  }

  @Retention(RUNTIME)
  @JsonQualifier
  @interface Localized {
    String value();
  }

  static class Baguette {
    @Localized("en")
    boolean withButter;

    @Localized("fr")
    boolean avecBeurre;
  }

  static class LocalizedBooleanAdapter extends JsonAdapter<Boolean> {
    private static final JsonAdapter.Factory FACTORY =
        new JsonAdapter.Factory() {
          @Override
          public JsonAdapter<?> create(
              Type type, Set<? extends Annotation> annotations, Moshi moshi) {
            if (type == boolean.class) {
              for (Annotation annotation : annotations) {
                if (annotation instanceof Localized) {
                  return new LocalizedBooleanAdapter(((Localized) annotation).value());
                }
              }
            }
            return null;
          }
        };

    private final String trueString;
    private final String falseString;

    public LocalizedBooleanAdapter(String language) {
      if (language.equals("fr")) {
        trueString = "oui";
        falseString = "non";
      } else {
        trueString = "yes";
        falseString = "no";
      }
    }

    @Override
    public Boolean fromJson(JsonReader reader) throws IOException {
      return reader.nextString().equals(trueString);
    }

    @Override
    public void toJson(JsonWriter writer, Boolean value) throws IOException {
      writer.value(value ? trueString : falseString);
    }
  }
}
