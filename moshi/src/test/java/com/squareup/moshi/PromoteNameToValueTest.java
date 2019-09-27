/*
 * Copyright (C) 2015 Square, Inc.
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

import java.util.List;
import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public final class PromoteNameToValueTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
  }

  @Test public void readerStringValue() throws Exception {
    JsonReader reader = factory.newReader("{\"a\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.a");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo("a");
    assertThat(reader.getPath()).isEqualTo("$.a");
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.getPath()).isEqualTo("$.a");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerIntegerValue() throws Exception {
    JsonReader reader = factory.newReader("{\"5\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.5");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextInt()).isEqualTo(5);
    assertThat(reader.getPath()).isEqualTo("$.5");
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.getPath()).isEqualTo("$.5");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerDoubleValue() throws Exception {
    JsonReader reader = factory.newReader("{\"5.5\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.5.5");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextDouble()).isEqualTo(5.5d);
    assertThat(reader.getPath()).isEqualTo("$.5.5");
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.getPath()).isEqualTo("$.5.5");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerBooleanValue() throws Exception {
    JsonReader reader = factory.newReader("{\"true\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.true");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException e) {
      assertThat(e.getMessage()).isIn(
          "Expected BOOLEAN but was true, a java.lang.String, at path $.true",
          "Expected a boolean but was STRING at path $.true");
    }
    assertThat(reader.getPath()).isEqualTo("$.true");
    assertThat(reader.nextString()).isEqualTo("true");
    assertThat(reader.getPath()).isEqualTo("$.true");
    assertThat(reader.nextInt()).isEqualTo(1);
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerLongValue() throws Exception {
    JsonReader reader = factory.newReader("{\"5\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.5");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextLong()).isEqualTo(5L);
    assertThat(reader.getPath()).isEqualTo("$.5");
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.getPath()).isEqualTo("$.5");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerNullValue() throws Exception {
    JsonReader reader = factory.newReader("{\"null\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.null");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    try {
      reader.nextNull();
      fail();
    } catch (JsonDataException e) {
      assertThat(e.getMessage()).isIn(
          "Expected NULL but was null, a java.lang.String, at path $.null",
          "Expected null but was STRING at path $.null");
    }
    assertThat(reader.nextString()).isEqualTo("null");
    assertThat(reader.getPath()).isEqualTo("$.null");
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.getPath()).isEqualTo("$.null");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerMultipleValueObject() throws Exception {
    JsonReader reader = factory.newReader("{\"a\":1,\"b\":2}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextInt()).isEqualTo(1);
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.b");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo("b");
    assertThat(reader.getPath()).isEqualTo("$.b");
    assertThat(reader.nextInt()).isEqualTo(2);
    assertThat(reader.getPath()).isEqualTo("$.b");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerEmptyValueObject() throws Exception {
    JsonReader reader = factory.newReader("{}");
    reader.beginObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_OBJECT);
    reader.promoteNameToValue();
    assertThat(reader.getPath()).isEqualTo("$.");
    reader.endObject();
    assertThat(reader.getPath()).isEqualTo("$");
  }

  @Test public void readerUnusedPromotionDoesntPersist() throws Exception {
    JsonReader reader = factory.newReader("[{},{\"a\":5}]");
    reader.beginArray();
    reader.beginObject();
    reader.promoteNameToValue();
    reader.endObject();
    reader.beginObject();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextName()).isEqualTo("a");
  }

  @Test public void readerUnquotedIntegerValue() throws Exception {
    JsonReader reader = factory.newReader("{5:1}");
    reader.setLenient(true);
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.nextInt()).isEqualTo(5);
    assertThat(reader.nextInt()).isEqualTo(1);
    reader.endObject();
  }

  @Test public void readerUnquotedLongValue() throws Exception {
    JsonReader reader = factory.newReader("{5:1}");
    reader.setLenient(true);
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.nextLong()).isEqualTo(5L);
    assertThat(reader.nextInt()).isEqualTo(1);
    reader.endObject();
  }

  @Test public void readerUnquotedDoubleValue() throws Exception {
    JsonReader reader = factory.newReader("{5:1}");
    reader.setLenient(true);
    reader.beginObject();
    reader.promoteNameToValue();
    assertThat(reader.nextDouble()).isEqualTo(5d);
    assertThat(reader.nextInt()).isEqualTo(1);
    reader.endObject();
  }

  @Test public void writerStringValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value("a");
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.value(1);
    assertThat(writer.getPath()).isEqualTo("$.a");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{\"a\":1}");
  }

  @Test public void writerIntegerValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value(5);
    assertThat(writer.getPath()).isEqualTo("$.5");
    writer.value(1);
    assertThat(writer.getPath()).isEqualTo("$.5");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{\"5\":1}");
  }

  @Test public void writerDoubleValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value(5.5d);
    assertThat(writer.getPath()).isEqualTo("$.5.5");
    writer.value(1);
    assertThat(writer.getPath()).isEqualTo("$.5.5");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{\"5.5\":1}");
  }

  @Test public void writerBooleanValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.value(true);
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Boolean cannot be used as a map key in JSON at path $.");
    }
    writer.value("true");
    assertThat(writer.getPath()).isEqualTo("$.true");
    writer.value(1);
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{\"true\":1}");
  }

  @Test public void writerLongValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value(5L);
    assertThat(writer.getPath()).isEqualTo("$.5");
    writer.value(1);
    assertThat(writer.getPath()).isEqualTo("$.5");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{\"5\":1}");
  }

  @Test public void writerNullValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.nullValue();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("null cannot be used as a map key in JSON at path $.");
    }
    writer.value("null");
    assertThat(writer.getPath()).isEqualTo("$.null");
    writer.value(1);
    assertThat(writer.getPath()).isEqualTo("$.null");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{\"null\":1}");
  }

  @Test public void writerMultipleValueObject() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    writer.value(1);
    writer.promoteValueToName();
    writer.value("b");
    assertThat(writer.getPath()).isEqualTo("$.b");
    writer.value(2);
    assertThat(writer.getPath()).isEqualTo("$.b");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{\"a\":1,\"b\":2}");
  }

  @Test public void writerEmptyValueObject() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    assertThat(writer.getPath()).isEqualTo("$.");
    writer.endObject();
    assertThat(writer.getPath()).isEqualTo("$");
    assertThat(factory.json()).isEqualTo("{}");
  }

  @Test public void writerUnusedPromotionDoesntPersist() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    writer.beginObject();
    writer.promoteValueToName();
    writer.endObject();
    writer.beginObject();
    try {
      writer.value("a");
      fail();
    } catch (IllegalStateException expected) {
    }
    writer.name("a");
  }

  @Test public void writerSourceValueFails() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.value(new Buffer().writeUtf8("\"a\""));
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage(
          "BufferedSource cannot be used as a map key in JSON at path $.");
    }
    writer.value("a");
    writer.value("a value");
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{\"a\":\"a value\"}");
  }

  @Test public void writerValueSinkFails() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.valueSink();
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage(
          "BufferedSink cannot be used as a map key in JSON at path $.");
    }
    writer.value("a");
    writer.value("a value");
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{\"a\":\"a value\"}");
  }
}
