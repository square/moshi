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
import okio.Buffer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class JsonUtf8WriterTest {
  @Test public void prettyPrintObject() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
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
    assertThat(buffer.readUtf8()).isEqualTo(expected);
  }

  @Test public void prettyPrintArray() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
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
    assertThat(buffer.readUtf8()).isEqualTo(expected);
  }

  @Test public void repeatedNameIgnored() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginObject();
    writer.name("a").value(1);
    writer.name("a").value(2);
    writer.endObject();
    // JsonWriter doesn't attempt to detect duplicate names
    assertThat(buffer.readUtf8()).isEqualTo("{\"a\":1,\"a\":2}");
  }

  @Test public void valueFromSource() throws IOException {
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonUtf8Writer.of(buffer);
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
    assertThat(buffer.readUtf8()).isEqualTo("{\"a\":[\"value\"],\"b\":2,\"c\":3,\"d\":null}");
  }
}
