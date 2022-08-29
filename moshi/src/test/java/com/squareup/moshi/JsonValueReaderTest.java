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

import static com.squareup.moshi.TestUtil.MAX_DEPTH;
import static com.squareup.moshi.TestUtil.repeat;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class JsonValueReaderTest {
  @Test
  public void array() throws Exception {
    List<Object> root = new ArrayList<>();
    root.add("s");
    root.add(1.5d);
    root.add(true);
    root.add(null);
    JsonReader reader = new JsonValueReader(root);

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.BEGIN_ARRAY);
    reader.beginArray();

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextString(), "s");

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NUMBER);
    assertEquals(reader.nextDouble(), 1.5d, 0);

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.BOOLEAN);
    assertEquals(reader.nextBoolean(), true);

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NULL);
    assertNull(reader.nextNull());

    assertFalse(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.END_ARRAY);
    reader.endArray();

    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void object() throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("a", "s");
    root.put("b", 1.5d);
    root.put("c", true);
    root.put("d", null);
    JsonReader reader = new JsonValueReader(root);

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.BEGIN_OBJECT);
    reader.beginObject();

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NAME);
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextString(), "s");

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NAME);
    assertEquals(reader.nextName(), "b");
    assertEquals(reader.peek(), JsonReader.Token.NUMBER);
    assertEquals(reader.nextDouble(), 1.5d, 0);

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NAME);
    assertEquals(reader.nextName(), "c");
    assertEquals(reader.peek(), JsonReader.Token.BOOLEAN);
    assertEquals(reader.nextBoolean(), true);

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NAME);
    assertEquals(reader.nextName(), "d");
    assertEquals(reader.peek(), JsonReader.Token.NULL);
    assertNull(reader.nextNull());

    assertFalse(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.END_OBJECT);
    reader.endObject();

    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void nesting() throws Exception {
    List<Map<String, List<Map<String, Double>>>> root =
        singletonList(singletonMap("a", singletonList(singletonMap("b", 1.5d))));
    JsonReader reader = new JsonValueReader(root);

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.BEGIN_ARRAY);
    reader.beginArray();

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.BEGIN_OBJECT);
    reader.beginObject();

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NAME);
    assertEquals(reader.nextName(), "a");

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.BEGIN_ARRAY);
    reader.beginArray();

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.BEGIN_OBJECT);
    reader.beginObject();

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NAME);
    assertEquals(reader.nextName(), "b");

    assertTrue(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.NUMBER);
    assertEquals(reader.nextDouble(), 1.5d, 0);

    assertFalse(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.END_OBJECT);
    reader.endObject();

    assertFalse(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.END_ARRAY);
    reader.endArray();

    assertFalse(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.END_OBJECT);
    reader.endObject();

    assertFalse(reader.hasNext());
    assertEquals(reader.peek(), JsonReader.Token.END_ARRAY);
    reader.endArray();

    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void promoteNameToValue() throws Exception {
    Map<String, String> root = singletonMap("a", "b");

    JsonReader reader = new JsonValueReader(root);
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextString(), "a");

    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextString(), "b");
    reader.endObject();

    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void endArrayTooEarly() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList("s"));

    reader.beginArray();
    try {
      reader.endArray();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("Expected END_ARRAY but was s, a java.lang.String, at path $[0]"));
    }
  }

  @Test
  public void endObjectTooEarly() throws Exception {
    JsonReader reader = new JsonValueReader(singletonMap("a", "b"));

    reader.beginObject();
    try {
      reader.endObject();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().startsWith("Expected END_OBJECT but was a=b"));
    }
  }

  @Test
  public void unsupportedType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("x")));

    reader.beginArray();
    try {
      reader.peek();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains(
                  "Expected a JSON value but was x, a java.lang.StringBuilder, at path $[0]"));
    }
  }

  @Test
  public void unsupportedKeyType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonMap(new StringBuilder("x"), "y"));

    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected NAME but was x, a java.lang.StringBuilder, at path $.", expected.getMessage());
    }
  }

  @Test
  public void nullKey() throws Exception {
    JsonReader reader = new JsonValueReader(singletonMap(null, "y"));

    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
      assertEquals("Expected NAME but was null at path $.", expected.getMessage());
    }
  }

  @Test
  public void unexpectedIntType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("1")));
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected NUMBER but was 1, a java.lang.StringBuilder, at path $[0]",
          expected.getMessage());
    }
  }

  @Test
  public void unexpectedLongType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("1")));
    reader.beginArray();
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected NUMBER but was 1, a java.lang.StringBuilder, at path $[0]",
          expected.getMessage());
    }
  }

  @Test
  public void unexpectedDoubleType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("1")));
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected NUMBER but was 1, a java.lang.StringBuilder, at path $[0]",
          expected.getMessage());
    }
  }

  @Test
  public void unexpectedStringType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("s")));
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected STRING but was s, a java.lang.StringBuilder, at path $[0]",
          expected.getMessage());
    }
  }

  @Test
  public void unexpectedBooleanType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("true")));
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected BOOLEAN but was true, a java.lang.StringBuilder, at path $[0]",
          expected.getMessage());
    }
  }

  @Test
  public void unexpectedNullType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("null")));
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected NULL but was null, a java.lang.StringBuilder, at path $[0]",
          expected.getMessage());
    }
  }

  @Test
  public void skipRoot() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("x")));
    reader.skipValue();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void skipListValue() throws Exception {
    List<Object> root = new ArrayList<>();
    root.add("a");
    root.add("b");
    root.add("c");
    JsonReader reader = new JsonValueReader(root);

    reader.beginArray();

    assertEquals(reader.getPath(), "$[0]");
    assertEquals(reader.nextString(), "a");

    assertEquals(reader.getPath(), "$[1]");
    reader.skipValue();

    assertEquals(reader.getPath(), "$[2]");
    assertEquals(reader.nextString(), "c");

    reader.endArray();
  }

  @Test
  public void skipObjectName() throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("a", "s");
    root.put("b", 1.5d);
    root.put("c", true);
    JsonReader reader = new JsonValueReader(root);

    reader.beginObject();

    assertEquals(reader.nextName(), "a");
    assertEquals(reader.getPath(), "$.a");
    assertEquals(reader.nextString(), "s");
    assertEquals(reader.getPath(), "$.a");

    reader.skipValue();
    assertEquals(reader.getPath(), "$.null");
    assertEquals(reader.nextDouble(), 1.5d, 0);
    assertEquals(reader.getPath(), "$.null");

    assertEquals(reader.nextName(), "c");
    assertEquals(reader.getPath(), "$.c");
    assertEquals(reader.nextBoolean(), true);
    assertEquals(reader.getPath(), "$.c");

    reader.endObject();
  }

  @Test
  public void skipObjectValue() throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("a", "s");
    root.put("b", 1.5d);
    root.put("c", true);
    JsonReader reader = new JsonValueReader(root);

    reader.beginObject();

    assertEquals(reader.nextName(), "a");
    assertEquals(reader.getPath(), "$.a");
    assertEquals(reader.nextString(), "s");
    assertEquals(reader.getPath(), "$.a");

    assertEquals(reader.nextName(), "b");
    assertEquals(reader.getPath(), "$.b");
    reader.skipValue();
    assertEquals(reader.getPath(), "$.null");

    assertEquals(reader.nextName(), "c");
    assertEquals(reader.getPath(), "$.c");
    assertEquals(reader.nextBoolean(), true);
    assertEquals(reader.getPath(), "$.c");

    reader.endObject();
  }

  @Test
  public void failOnUnknown() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList("a"));
    reader.setFailOnUnknown(true);

    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertEquals("Cannot skip unexpected STRING at $[0]", expected.getMessage());
    }
  }

  @Test
  public void close() throws Exception {
    try {
      JsonReader reader = new JsonValueReader(singletonList("a"));
      reader.beginArray();
      reader.close();
      reader.nextString();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      JsonReader reader = new JsonValueReader(singletonList("a"));
      reader.close();
      reader.beginArray();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void numberToStringCoersion() throws Exception {
    JsonReader reader =
        new JsonValueReader(Arrays.asList(0, 9223372036854775807L, 2.5d, 3.01f, "a", "5"));
    reader.beginArray();
    assertEquals(reader.nextString(), "0");
    assertEquals(reader.nextString(), "9223372036854775807");
    assertEquals(reader.nextString(), "2.5");
    assertEquals(reader.nextString(), "3.01");
    assertEquals(reader.nextString(), "a");
    assertEquals(reader.nextString(), "5");
    reader.endArray();
  }

  @Test
  public void tooDeeplyNestedArrays() throws IOException {
    Object root = Collections.emptyList();
    for (int i = 0; i < MAX_DEPTH + 1; i++) {
      root = singletonList(root);
    }
    JsonReader reader = new JsonValueReader(root);
    for (int i = 0; i < MAX_DEPTH; i++) {
      reader.beginArray();
    }
    try {
      reader.beginArray();
      fail();
    } catch (JsonDataException expected) {
      assertEquals("Nesting too deep at $" + repeat("[0]", MAX_DEPTH + 1), expected.getMessage());
    }
  }

  @Test
  public void tooDeeplyNestedObjects() throws IOException {
    Object root = Boolean.TRUE;
    for (int i = 0; i < MAX_DEPTH + 1; i++) {
      root = singletonMap("a", root);
    }
    JsonReader reader = new JsonValueReader(root);
    for (int i = 0; i < MAX_DEPTH; i++) {
      reader.beginObject();
      assertEquals(reader.nextName(), "a");
    }
    try {
      reader.beginObject();
      fail();
    } catch (JsonDataException expected) {
      assertEquals("Nesting too deep at $" + repeat(".a", MAX_DEPTH) + ".", expected.getMessage());
    }
  }
}
