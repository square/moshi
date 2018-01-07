/*
 * Copyright (C) 2017 Square, Inc.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static com.squareup.moshi.TestUtil.MAX_DEPTH;
import static com.squareup.moshi.TestUtil.repeat;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class JsonValueReaderTest {
  @Test public void array() throws Exception {
    List<Object> root = new ArrayList<>();
    root.add("s");
    root.add(1.5d);
    root.add(true);
    root.add(null);
    JsonReader reader = new JsonValueReader(root);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BEGIN_ARRAY);
    reader.beginArray();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo("s");

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NUMBER);
    assertThat(reader.nextDouble()).isEqualTo(1.5d);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BOOLEAN);
    assertThat(reader.nextBoolean()).isEqualTo(true);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NULL);
    assertThat(reader.nextNull()).isNull();

    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_ARRAY);
    reader.endArray();

    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void object() throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("a", "s");
    root.put("b", 1.5d);
    root.put("c", true);
    root.put("d", null);
    JsonReader reader = new JsonValueReader(root);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BEGIN_OBJECT);
    reader.beginObject();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NAME);
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo("s");

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NAME);
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NUMBER);
    assertThat(reader.nextDouble()).isEqualTo(1.5d);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NAME);
    assertThat(reader.nextName()).isEqualTo("c");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BOOLEAN);
    assertThat(reader.nextBoolean()).isEqualTo(true);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NAME);
    assertThat(reader.nextName()).isEqualTo("d");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NULL);
    assertThat(reader.nextNull()).isNull();

    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_OBJECT);
    reader.endObject();

    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void nesting() throws Exception {
    List<Map<String, List<Map<String, Double>>>> root
        = singletonList(singletonMap("a", singletonList(singletonMap("b", 1.5d))));
    JsonReader reader = new JsonValueReader(root);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BEGIN_ARRAY);
    reader.beginArray();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BEGIN_OBJECT);
    reader.beginObject();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NAME);
    assertThat(reader.nextName()).isEqualTo("a");

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BEGIN_ARRAY);
    reader.beginArray();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BEGIN_OBJECT);
    reader.beginObject();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NAME);
    assertThat(reader.nextName()).isEqualTo("b");

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.NUMBER);
    assertThat(reader.nextDouble()).isEqualTo(1.5d);

    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_OBJECT);
    reader.endObject();

    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_ARRAY);
    reader.endArray();

    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_OBJECT);
    reader.endObject();

    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_ARRAY);
    reader.endArray();

    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void promoteNameToValue() throws Exception {
    Map<String, String> root = singletonMap("a", "b");

    JsonReader reader = new JsonValueReader(root);
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo("a");

    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo("b");
    reader.endObject();

    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void endArrayTooEarly() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList("s"));

    reader.beginArray();
    try {
      reader.endArray();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected END_ARRAY but was s, a java.lang.String, at path $[0]");
    }
  }

  @Test public void endObjectTooEarly() throws Exception {
    JsonReader reader = new JsonValueReader(singletonMap("a", "b"));

    reader.beginObject();
    try {
      reader.endObject();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessageStartingWith("Expected END_OBJECT but was a=b");
    }
  }

  @Test public void unsupportedType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("x")));

    reader.beginArray();
    try {
      reader.peek();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected a JSON value but was x, a java.lang.StringBuilder, at path $[0]");
    }
  }

  @Test public void unsupportedKeyType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonMap(new StringBuilder("x"), "y"));

    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected NAME but was x, a java.lang.StringBuilder, at path $.");
    }
  }

  @Test public void nullKey() throws Exception {
    JsonReader reader = new JsonValueReader(singletonMap(null, "y"));

    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Expected NAME but was null at path $.");
    }
  }

  @Test public void unexpectedIntType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("1")));
    reader.beginArray();
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected NUMBER but was 1, a java.lang.StringBuilder, at path $[0]");
    }
  }

  @Test public void unexpectedLongType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("1")));
    reader.beginArray();
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected NUMBER but was 1, a java.lang.StringBuilder, at path $[0]");
    }
  }

  @Test public void unexpectedDoubleType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("1")));
    reader.beginArray();
    try {
      reader.nextDouble();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected NUMBER but was 1, a java.lang.StringBuilder, at path $[0]");
    }
  }

  @Test public void unexpectedStringType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("s")));
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected STRING but was s, a java.lang.StringBuilder, at path $[0]");
    }
  }

  @Test public void unexpectedBooleanType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("true")));
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected BOOLEAN but was true, a java.lang.StringBuilder, at path $[0]");
    }
  }

  @Test public void unexpectedNullType() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("null")));
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected NULL but was null, a java.lang.StringBuilder, at path $[0]");
    }
  }

  @Test public void skipRoot() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList(new StringBuilder("x")));
    reader.skipValue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipListValue() throws Exception {
    List<Object> root = new ArrayList<>();
    root.add("a");
    root.add("b");
    root.add("c");
    JsonReader reader = new JsonValueReader(root);

    reader.beginArray();

    assertThat(reader.getPath()).isEqualTo("$[0]");
    assertThat(reader.nextString()).isEqualTo("a");

    assertThat(reader.getPath()).isEqualTo("$[1]");
    reader.skipValue();

    assertThat(reader.getPath()).isEqualTo("$[2]");
    assertThat(reader.nextString()).isEqualTo("c");

    reader.endArray();
  }

  @Test public void skipObjectName() throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("a", "s");
    root.put("b", 1.5d);
    root.put("c", true);
    JsonReader reader = new JsonValueReader(root);

    reader.beginObject();

    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.getPath()).isEqualTo("$.a");
    assertThat(reader.nextString()).isEqualTo("s");
    assertThat(reader.getPath()).isEqualTo("$.a");

    reader.skipValue();
    assertThat(reader.getPath()).isEqualTo("$.null");
    assertThat(reader.nextDouble()).isEqualTo(1.5d);
    assertThat(reader.getPath()).isEqualTo("$.null");

    assertThat(reader.nextName()).isEqualTo("c");
    assertThat(reader.getPath()).isEqualTo("$.c");
    assertThat(reader.nextBoolean()).isEqualTo(true);
    assertThat(reader.getPath()).isEqualTo("$.c");

    reader.endObject();
  }

  @Test public void skipObjectValue() throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("a", "s");
    root.put("b", 1.5d);
    root.put("c", true);
    JsonReader reader = new JsonValueReader(root);

    reader.beginObject();

    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.getPath()).isEqualTo("$.a");
    assertThat(reader.nextString()).isEqualTo("s");
    assertThat(reader.getPath()).isEqualTo("$.a");

    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.getPath()).isEqualTo("$.b");
    reader.skipValue();
    assertThat(reader.getPath()).isEqualTo("$.null");

    assertThat(reader.nextName()).isEqualTo("c");
    assertThat(reader.getPath()).isEqualTo("$.c");
    assertThat(reader.nextBoolean()).isEqualTo(true);
    assertThat(reader.getPath()).isEqualTo("$.c");

    reader.endObject();
  }

  @Test public void failOnUnknown() throws Exception {
    JsonReader reader = new JsonValueReader(singletonList("a"));
    reader.setFailOnUnknown(true);

    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Cannot skip unexpected STRING at $[0]");
    }
  }

  @Test public void close() throws Exception {
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

  @Test public void numberToStringCoersion() throws Exception {
    JsonReader reader =
        new JsonValueReader(Arrays.asList(0, 9223372036854775807L, 2.5d, 3.01f, "a", "5"));
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("0");
    assertThat(reader.nextString()).isEqualTo("9223372036854775807");
    assertThat(reader.nextString()).isEqualTo("2.5");
    assertThat(reader.nextString()).isEqualTo("3.01");
    assertThat(reader.nextString()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("5");
    reader.endArray();
  }

  @Test public void tooDeeplyNestedArrays() throws IOException {
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
      assertThat(expected).hasMessage("Nesting too deep at $" + repeat("[0]", MAX_DEPTH + 1));
    }
  }

  @Test public void tooDeeplyNestedObjects() throws IOException {
    Object root = Boolean.TRUE;
    for (int i = 0; i < MAX_DEPTH + 1; i++) {
      root = singletonMap("a", root);
    }
    JsonReader reader = new JsonValueReader(root);
    for (int i = 0; i < MAX_DEPTH; i++) {
      reader.beginObject();
      assertThat(reader.nextName()).isEqualTo("a");
    }
    try {
      reader.beginObject();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Nesting too deep at $" + repeat(".a", MAX_DEPTH) + ".");
    }
  }
}
