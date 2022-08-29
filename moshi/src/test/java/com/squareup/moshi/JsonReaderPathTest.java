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

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class JsonReaderPathTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void path() throws IOException {
    JsonReader reader = factory.newReader("{\"a\":[2,true,false,null,\"b\",{\"c\":\"d\"},[3]]}");
    assertEquals(reader.getPath(), "$");
    reader.beginObject();
    assertEquals(reader.getPath(), "$.");
    reader.nextName();
    assertEquals(reader.getPath(), "$.a");
    reader.beginArray();
    assertEquals(reader.getPath(), "$.a[0]");
    reader.nextInt();
    assertEquals(reader.getPath(), "$.a[1]");
    reader.nextBoolean();
    assertEquals(reader.getPath(), "$.a[2]");
    reader.nextBoolean();
    assertEquals(reader.getPath(), "$.a[3]");
    reader.nextNull();
    assertEquals(reader.getPath(), "$.a[4]");
    reader.nextString();
    assertEquals(reader.getPath(), "$.a[5]");
    reader.beginObject();
    assertEquals(reader.getPath(), "$.a[5].");
    reader.nextName();
    assertEquals(reader.getPath(), "$.a[5].c");
    reader.nextString();
    assertEquals(reader.getPath(), "$.a[5].c");
    reader.endObject();
    assertEquals(reader.getPath(), "$.a[6]");
    reader.beginArray();
    assertEquals(reader.getPath(), "$.a[6][0]");
    reader.nextInt();
    assertEquals(reader.getPath(), "$.a[6][1]");
    reader.endArray();
    assertEquals(reader.getPath(), "$.a[7]");
    reader.endArray();
    assertEquals(reader.getPath(), "$.a");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void arrayOfObjects() throws IOException {
    JsonReader reader = factory.newReader("[{},{},{}]");
    reader.beginArray();
    assertEquals(reader.getPath(), "$[0]");
    reader.beginObject();
    assertEquals(reader.getPath(), "$[0].");
    reader.endObject();
    assertEquals(reader.getPath(), "$[1]");
    reader.beginObject();
    assertEquals(reader.getPath(), "$[1].");
    reader.endObject();
    assertEquals(reader.getPath(), "$[2]");
    reader.beginObject();
    assertEquals(reader.getPath(), "$[2].");
    reader.endObject();
    assertEquals(reader.getPath(), "$[3]");
    reader.endArray();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void arrayOfArrays() throws IOException {
    JsonReader reader = factory.newReader("[[],[],[]]");
    reader.beginArray();
    assertEquals(reader.getPath(), "$[0]");
    reader.beginArray();
    assertEquals(reader.getPath(), "$[0][0]");
    reader.endArray();
    assertEquals(reader.getPath(), "$[1]");
    reader.beginArray();
    assertEquals(reader.getPath(), "$[1][0]");
    reader.endArray();
    assertEquals(reader.getPath(), "$[2]");
    reader.beginArray();
    assertEquals(reader.getPath(), "$[2][0]");
    reader.endArray();
    assertEquals(reader.getPath(), "$[3]");
    reader.endArray();
    assertEquals(reader.getPath(), "$");
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void objectPath() throws IOException {
    JsonReader reader = factory.newReader("{\"a\":1,\"b\":2}");
    assertEquals(reader.getPath(), "$");

    reader.peek();
    assertEquals(reader.getPath(), "$");
    reader.beginObject();
    assertEquals(reader.getPath(), "$.");

    reader.peek();
    assertEquals(reader.getPath(), "$.");
    reader.nextName();
    assertEquals(reader.getPath(), "$.a");

    reader.peek();
    assertEquals(reader.getPath(), "$.a");
    reader.nextInt();
    assertEquals(reader.getPath(), "$.a");

    reader.peek();
    assertEquals(reader.getPath(), "$.a");
    reader.nextName();
    assertEquals(reader.getPath(), "$.b");

    reader.peek();
    assertEquals(reader.getPath(), "$.b");
    reader.nextInt();
    assertEquals(reader.getPath(), "$.b");

    reader.peek();
    assertEquals(reader.getPath(), "$.b");
    reader.endObject();
    assertEquals(reader.getPath(), "$");

    reader.peek();
    assertEquals(reader.getPath(), "$");
    reader.close();
    assertEquals(reader.getPath(), "$");
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void arrayPath() throws IOException {
    JsonReader reader = factory.newReader("[1,2]");
    assertEquals(reader.getPath(), "$");

    reader.peek();
    assertEquals(reader.getPath(), "$");
    reader.beginArray();
    assertEquals(reader.getPath(), "$[0]");

    reader.peek();
    assertEquals(reader.getPath(), "$[0]");
    reader.nextInt();
    assertEquals(reader.getPath(), "$[1]");

    reader.peek();
    assertEquals(reader.getPath(), "$[1]");
    reader.nextInt();
    assertEquals(reader.getPath(), "$[2]");

    reader.peek();
    assertEquals(reader.getPath(), "$[2]");
    reader.endArray();
    assertEquals(reader.getPath(), "$");

    reader.peek();
    assertEquals(reader.getPath(), "$");
    reader.close();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void multipleTopLevelValuesInOneDocument() throws IOException {
    assumeTrue(factory.encodesToBytes());

    JsonReader reader = factory.newReader("[][]");
    reader.setLenient(true);
    reader.beginArray();
    reader.endArray();
    assertEquals(reader.getPath(), "$");
    reader.beginArray();
    reader.endArray();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void skipArrayElements() throws IOException {
    JsonReader reader = factory.newReader("[1,2,3]");
    reader.beginArray();
    reader.skipValue();
    reader.skipValue();
    assertEquals(reader.getPath(), "$[2]");
  }

  @Test
  public void skipObjectNames() throws IOException {
    JsonReader reader = factory.newReader("{\"a\":1}");
    reader.beginObject();
    reader.skipValue();
    assertEquals(reader.getPath(), "$.null");
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void skipObjectValues() throws IOException {
    JsonReader reader = factory.newReader("{\"a\":1,\"b\":2}");
    reader.beginObject();
    reader.nextName();
    reader.skipValue();
    assertEquals(reader.getPath(), "$.null");
    reader.nextName();
    assertEquals(reader.getPath(), "$.b");
  }

  @Test
  public void skipNestedStructures() throws IOException {
    JsonReader reader = factory.newReader("[[1,2,3],4]");
    reader.beginArray();
    reader.skipValue();
    assertEquals(reader.getPath(), "$[1]");
  }
}
