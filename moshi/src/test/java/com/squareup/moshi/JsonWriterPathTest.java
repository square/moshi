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
import java.math.BigInteger;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public final class JsonWriterPathTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
  }

  @Test public void path() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$.");
    writer.name("a");
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$.a[0]");
    writer.value(2);
    assertThat(writer.getPath()).isEqualTo("$.a[1]");
    writer.value(true);
    assertThat(writer.getPath()).isEqualTo("$.a[2]");
    writer.value(false);
    assertThat(writer.getPath()).isEqualTo("$.a[3]");
    writer.nullValue();
    assertThat(writer.getPath()).isEqualTo("$.a[4]");
    writer.value("b");
    assertThat(writer.getPath()).isEqualTo("$.a[5]");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$.a[5].");
    writer.name("c");
    assertThat(writer.getPath()).isEqualTo("$.a[5].c");
    writer.value("d");
    assertThat(writer.getPath()).isEqualTo("$.a[5].c");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$.a[6]");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$.a[6][0]");
    writer.value(3);
    assertThat(writer.getPath()).isEqualTo("$.a[6][1]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$.a[7]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void arrayOfObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[0]");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$[0].");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$[1]");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$[1].");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$[2]");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$[2].");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$[3]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void arrayOfArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[0]");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[0][0]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$[1]");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[1][0]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$[2]");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[2][0]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$[3]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void objectPath() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$.");
    writer.name("a");
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.value(1);
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.name("b");
    assertThat(writer.getPath()).isEqualTo("$.b");
    writer.value(2);
    assertThat(writer.getPath()).isEqualTo("$.b");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.close();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void nestedObjects() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$.");
    writer.name("a");
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$.a.");
    writer.name("b");
    assertThat(writer.getPath()).isEqualTo("$.a.b");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$.a.b.");
    writer.name("c");
    assertThat(writer.getPath()).isEqualTo("$.a.b.c");
    writer.nullValue();
    assertThat(writer.getPath()).isEqualTo("$.a.b.c");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$.a.b");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void arrayPath() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[0]");
    writer.value(1);
    assertThat(writer.getPath()).isEqualTo("$[1]");
    writer.value(true);
    assertThat(writer.getPath()).isEqualTo("$[2]");
    writer.value("a");
    assertThat(writer.getPath()).isEqualTo("$[3]");
    writer.value(5.5d);
    assertThat(writer.getPath()).isEqualTo("$[4]");
    writer.value(BigInteger.ONE);
    assertThat(writer.getPath()).isEqualTo("$[5]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.close();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void nestedArrays() throws IOException {
    JsonWriter writer = factory.newWriter();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[0]");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[0][0]");
    writer.beginArray();
    assertThat(writer.getPath()).isEqualTo("$[0][0][0]");
    writer.nullValue();
    assertThat(writer.getPath()).isEqualTo("$[0][0][1]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$[0][1]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$[1]");
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.close();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void multipleTopLevelValuesInOneDocument() throws IOException {
    assumeTrue(factory.encodesToBytes());

    JsonWriter writer = factory.newWriter();
    writer.setLenient(true);
    writer.beginArray();
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$");
    writer.beginArray();
    writer.endArray();
    assertThat(writer.getPath()).isEqualTo("$");
  }

  @Test public void skipNulls() throws IOException {
    JsonWriter writer = factory.newWriter();
    writer.setSerializeNulls(false);
    assertThat(writer.getPath()).isEqualTo("$");
    writer.beginObject();
    assertThat(writer.getPath()).isEqualTo("$.");
    writer.name("a");
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.nullValue();
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.name("b");
    assertThat(writer.getPath()).isEqualTo("$.b");
    writer.nullValue();
    assertThat(writer.getPath()).isEqualTo("$.b");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
  }
}
