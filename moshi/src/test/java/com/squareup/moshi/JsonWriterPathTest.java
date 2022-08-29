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
import java.math.BigInteger;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class JsonWriterPathTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
  }

  @Test
  public void path() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertEquals(writer.getPath(), "$");
    writer.beginObject();
    assertEquals(writer.getPath(), "$.");
    writer.name("a");
    assertEquals(writer.getPath(), "$.a");
    writer.beginArray();
    assertEquals(writer.getPath(), "$.a[0]");
    writer.value(2);
    assertEquals(writer.getPath(), "$.a[1]");
    writer.value(true);
    assertEquals(writer.getPath(), "$.a[2]");
    writer.value(false);
    assertEquals(writer.getPath(), "$.a[3]");
    writer.nullValue();
    assertEquals(writer.getPath(), "$.a[4]");
    writer.value("b");
    assertEquals(writer.getPath(), "$.a[5]");
    writer.beginObject();
    assertEquals(writer.getPath(), "$.a[5].");
    writer.name("c");
    assertEquals(writer.getPath(), "$.a[5].c");
    writer.value("d");
    assertEquals(writer.getPath(), "$.a[5].c");
    writer.endObject();
    assertEquals(writer.getPath(), "$.a[6]");
    writer.beginArray();
    assertEquals(writer.getPath(), "$.a[6][0]");
    writer.value(3);
    assertEquals(writer.getPath(), "$.a[6][1]");
    writer.endArray();
    assertEquals(writer.getPath(), "$.a[7]");
    writer.endArray();
    assertEquals(writer.getPath(), "$.a");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void arrayOfObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    assertEquals(writer.getPath(), "$[0]");
    writer.beginObject();
    assertEquals(writer.getPath(), "$[0].");
    writer.endObject();
    assertEquals(writer.getPath(), "$[1]");
    writer.beginObject();
    assertEquals(writer.getPath(), "$[1].");
    writer.endObject();
    assertEquals(writer.getPath(), "$[2]");
    writer.beginObject();
    assertEquals(writer.getPath(), "$[2].");
    writer.endObject();
    assertEquals(writer.getPath(), "$[3]");
    writer.endArray();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void arrayOfArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    assertEquals(writer.getPath(), "$[0]");
    writer.beginArray();
    assertEquals(writer.getPath(), "$[0][0]");
    writer.endArray();
    assertEquals(writer.getPath(), "$[1]");
    writer.beginArray();
    assertEquals(writer.getPath(), "$[1][0]");
    writer.endArray();
    assertEquals(writer.getPath(), "$[2]");
    writer.beginArray();
    assertEquals(writer.getPath(), "$[2][0]");
    writer.endArray();
    assertEquals(writer.getPath(), "$[3]");
    writer.endArray();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void objectPath() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertEquals(writer.getPath(), "$");
    writer.beginObject();
    assertEquals(writer.getPath(), "$.");
    writer.name("a");
    assertEquals(writer.getPath(), "$.a");
    writer.value(1);
    assertEquals(writer.getPath(), "$.a");
    writer.name("b");
    assertEquals(writer.getPath(), "$.b");
    writer.value(2);
    assertEquals(writer.getPath(), "$.b");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    writer.close();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void nestedObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertEquals(writer.getPath(), "$");
    writer.beginObject();
    assertEquals(writer.getPath(), "$.");
    writer.name("a");
    assertEquals(writer.getPath(), "$.a");
    writer.beginObject();
    assertEquals(writer.getPath(), "$.a.");
    writer.name("b");
    assertEquals(writer.getPath(), "$.a.b");
    writer.beginObject();
    assertEquals(writer.getPath(), "$.a.b.");
    writer.name("c");
    assertEquals(writer.getPath(), "$.a.b.c");
    writer.nullValue();
    assertEquals(writer.getPath(), "$.a.b.c");
    writer.endObject();
    assertEquals(writer.getPath(), "$.a.b");
    writer.endObject();
    assertEquals(writer.getPath(), "$.a");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void arrayPath() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertEquals(writer.getPath(), "$");
    writer.beginArray();
    assertEquals(writer.getPath(), "$[0]");
    writer.value(1);
    assertEquals(writer.getPath(), "$[1]");
    writer.value(true);
    assertEquals(writer.getPath(), "$[2]");
    writer.value("a");
    assertEquals(writer.getPath(), "$[3]");
    writer.value(5.5d);
    assertEquals(writer.getPath(), "$[4]");
    writer.value(BigInteger.ONE);
    assertEquals(writer.getPath(), "$[5]");
    writer.endArray();
    assertEquals(writer.getPath(), "$");
    writer.close();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void nestedArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertEquals(writer.getPath(), "$");
    writer.beginArray();
    assertEquals(writer.getPath(), "$[0]");
    writer.beginArray();
    assertEquals(writer.getPath(), "$[0][0]");
    writer.beginArray();
    assertEquals(writer.getPath(), "$[0][0][0]");
    writer.nullValue();
    assertEquals(writer.getPath(), "$[0][0][1]");
    writer.endArray();
    assertEquals(writer.getPath(), "$[0][1]");
    writer.endArray();
    assertEquals(writer.getPath(), "$[1]");
    writer.endArray();
    assertEquals(writer.getPath(), "$");
    writer.close();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void multipleTopLevelValuesInOneDocument() throws IOException {
    assumeTrue(factory.encodesToBytes());

    JsonWriter writer = factory.newWriter();
    writer.setLenient(true);
    writer.beginArray();
    writer.endArray();
    assertEquals(writer.getPath(), "$");
    writer.beginArray();
    writer.endArray();
    assertEquals(writer.getPath(), "$");
  }

  @Test
  public void skipNulls() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setSerializeNulls(false);
    assertEquals(writer.getPath(), "$");
    writer.beginObject();
    assertEquals(writer.getPath(), "$.");
    writer.name("a");
    assertEquals(writer.getPath(), "$.a");
    writer.nullValue();
    assertEquals(writer.getPath(), "$.a");
    writer.name("b");
    assertEquals(writer.getPath(), "$.b");
    writer.nullValue();
    assertEquals(writer.getPath(), "$.b");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
  }
}
