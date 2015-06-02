/*
 * Copyright (C) 2014 Google Inc.
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
import org.junit.Test;

import static com.squareup.moshi.TestUtil.newReader;
import static org.assertj.core.api.Assertions.assertThat;

public final class JsonReaderPathTest {
  @Test public void path() throws IOException {
    JsonReader reader = newReader("{\"a\":[2,true,false,null,\"b\",{\"c\":\"d\"},[3]]}");
    assertThat(reader.getPath()).isEqualTo("$");
    reader.beginObject();
    assertThat(reader.getPath()).isEqualTo("$.");
    reader.nextName();
    assertThat(reader.getPath()).isEqualTo("$.a");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$.a[0]");
    reader.nextInt();
    assertThat(reader.getPath()).isEqualTo("$.a[1]");
    reader.nextBoolean();
    assertThat(reader.getPath()).isEqualTo("$.a[2]");
    reader.nextBoolean();
    assertThat(reader.getPath()).isEqualTo("$.a[3]");
    reader.nextNull();
    assertThat(reader.getPath()).isEqualTo("$.a[4]");
    reader.nextString();
    assertThat(reader.getPath()).isEqualTo("$.a[5]");
    reader.beginObject();
    assertThat(reader.getPath()).isEqualTo("$.a[5].");
    reader.nextName();
    assertThat(reader.getPath()).isEqualTo("$.a[5].c");
    reader.nextString();
    assertThat(reader.getPath()).isEqualTo("$.a[5].c");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$.a[6]");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$.a[6][0]");
    reader.nextInt();
    assertThat(reader.getPath()).isEqualTo("$.a[6][1]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$.a[7]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$.a");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void arrayOfObjects() throws IOException {
    JsonReader reader = newReader("[{},{},{}]");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$[0]");
    reader.beginObject();
    assertThat(reader.getPath()).isEqualTo("$[0].");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$[1]");
    reader.beginObject();
    assertThat(reader.getPath()).isEqualTo("$[1].");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$[2]");
    reader.beginObject();
    assertThat(reader.getPath()).isEqualTo("$[2].");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$[3]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void arrayOfArrays() throws IOException {
    JsonReader reader = newReader("[[],[],[]]");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$[0]");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$[0][0]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$[1]");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$[1][0]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$[2]");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$[2][0]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$[3]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void objectPath() throws IOException {
    JsonReader reader = newReader("{\"a\":1,\"b\":2}");
    assertThat(reader.getPath()).isEqualTo("$");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$");
    reader.beginObject();
    assertThat(reader.getPath()).isEqualTo("$.");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$.");
    reader.nextName();
    assertThat(reader.getPath()).isEqualTo("$.a");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$.a");
    reader.nextInt();
    assertThat(reader.getPath()).isEqualTo("$.a");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$.a");
    reader.nextName();
    assertThat(reader.getPath()).isEqualTo("$.b");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$.b");
    reader.nextInt();
    assertThat(reader.getPath()).isEqualTo("$.b");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$.b");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$");
    reader.close();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void arrayPath() throws IOException {
    JsonReader reader = newReader("[1,2]");
    assertThat(reader.getPath()).isEqualTo("$");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$");
    reader.beginArray();
    assertThat(reader.getPath()).isEqualTo("$[0]");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$[0]");
    reader.nextInt();
    assertThat(reader.getPath()).isEqualTo("$[1]");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$[1]");
    reader.nextInt();
    assertThat(reader.getPath()).isEqualTo("$[2]");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$[2]");
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$");

    reader.peek();
    assertThat(reader.getPath()).isEqualTo("$");
    reader.close();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void multipleTopLevelValuesInOneDocument() throws IOException {
    JsonReader reader = newReader("[][]");
    reader.setLenient(true);
    reader.beginArray();
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$");
    reader.beginArray();
    reader.endArray();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void skipArrayElements() throws IOException {
    JsonReader reader = newReader("[1,2,3]");
    reader.beginArray();
    reader.skipValue();
    reader.skipValue();
    assertThat(reader.getPath()).isEqualTo("$[2]");
  }

  @Test public void skipObjectNames() throws IOException {
    JsonReader reader = newReader("{\"a\":1}");
    reader.beginObject();
    reader.skipValue();
    assertThat(reader.getPath()).isEqualTo("$.null");
  }

  @Test public void skipObjectValues() throws IOException {
    JsonReader reader = newReader("{\"a\":1,\"b\":2}");
    reader.beginObject();
    reader.nextName();
    reader.skipValue();
    assertThat(reader.getPath()).isEqualTo("$.null");
    reader.nextName();
    assertThat(reader.getPath()).isEqualTo("$.b");
  }

  @Test public void skipNestedStructures() throws IOException {
    JsonReader reader = newReader("[[1,2,3],4]");
    reader.beginArray();
    reader.skipValue();
    assertThat(reader.getPath()).isEqualTo("$[1]");
  }
}
