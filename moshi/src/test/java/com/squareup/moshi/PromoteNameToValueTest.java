/*
 * Copyright (C) 2015 Square, Inc.
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

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class PromoteNameToValueTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
  }

  @Test
  public void readerStringValue() throws Exception {
    JsonReader reader = factory.newReader("{\"a\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.a");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextString(), "a");
    assertEquals(reader.getPath(), "$.a");
    assertEquals(reader.nextInt(), 1);
    assertEquals(reader.getPath(), "$.a");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerIntegerValue() throws Exception {
    JsonReader reader = factory.newReader("{\"5\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.5");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextInt(), 5);
    assertEquals(reader.getPath(), "$.5");
    assertEquals(reader.nextInt(), 1);
    assertEquals(reader.getPath(), "$.5");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerDoubleValue() throws Exception {
    JsonReader reader = factory.newReader("{\"5.5\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.5.5");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(5.5d, reader.nextDouble(), 0);
    assertEquals(reader.getPath(), "$.5.5");
    assertEquals(reader.nextInt(), 1);
    assertEquals(reader.getPath(), "$.5.5");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerBooleanValue() throws Exception {
    JsonReader reader = factory.newReader("{\"true\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.true");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException e) {
      assertThat(
          e.getMessage(),
          anyOf(
              containsString("Expected BOOLEAN but was true, a java.lang.String, at path $.true"),
              containsString("Expected a boolean but was STRING at path $.true")));
    }
    assertEquals(reader.getPath(), "$.true");
    assertEquals(reader.nextString(), "true");
    assertEquals(reader.getPath(), "$.true");
    assertEquals(reader.nextInt(), 1);
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerLongValue() throws Exception {
    JsonReader reader = factory.newReader("{\"5\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.5");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextLong(), 5L);
    assertEquals(reader.getPath(), "$.5");
    assertEquals(reader.nextInt(), 1);
    assertEquals(reader.getPath(), "$.5");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerNullValue() throws Exception {
    JsonReader reader = factory.newReader("{\"null\":1}");
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.null");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    try {
      reader.nextNull();
      fail();
    } catch (JsonDataException e) {
      assertThat(
          e.getMessage(),
          anyOf(
              containsString("Expected NULL but was null, a java.lang.String, at path $.null"),
              containsString("Expected null but was STRING at path $.null")));
    }
    assertEquals(reader.nextString(), "null");
    assertEquals(reader.getPath(), "$.null");
    assertEquals(reader.nextInt(), 1);
    assertEquals(reader.getPath(), "$.null");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerMultipleValueObject() throws Exception {
    JsonReader reader = factory.newReader("{\"a\":1,\"b\":2}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextInt(), 1);
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.b");
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextString(), "b");
    assertEquals(reader.getPath(), "$.b");
    assertEquals(reader.nextInt(), 2);
    assertEquals(reader.getPath(), "$.b");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerEmptyValueObject() throws Exception {
    JsonReader reader = factory.newReader("{}");
    reader.beginObject();
    assertEquals(reader.peek(), JsonReader.Token.END_OBJECT);
    reader.promoteNameToValue();
    assertEquals(reader.getPath(), "$.");
    reader.endObject();
    assertEquals(reader.getPath(), "$");
  }

  @Test
  public void readerUnusedPromotionDoesntPersist() throws Exception {
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
    assertEquals(reader.nextName(), "a");
  }

  @Test
  public void readerUnquotedIntegerValue() throws Exception {
    JsonReader reader = factory.newReader("{5:1}");
    reader.setLenient(true);
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.nextInt(), 5);
    assertEquals(reader.nextInt(), 1);
    reader.endObject();
  }

  @Test
  public void readerUnquotedLongValue() throws Exception {
    JsonReader reader = factory.newReader("{5:1}");
    reader.setLenient(true);
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(reader.nextLong(), 5L);
    assertEquals(reader.nextInt(), 1);
    reader.endObject();
  }

  @Test
  public void readerUnquotedDoubleValue() throws Exception {
    JsonReader reader = factory.newReader("{5:1}");
    reader.setLenient(true);
    reader.beginObject();
    reader.promoteNameToValue();
    assertEquals(5d, reader.nextDouble(), 0);
    assertEquals(reader.nextInt(), 1);
    reader.endObject();
  }

  @Test
  public void writerStringValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value("a");
    assertEquals(writer.getPath(), "$.a");
    writer.value(1);
    assertEquals(writer.getPath(), "$.a");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{\"a\":1}");
  }

  @Test
  public void writerIntegerValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value(5);
    assertEquals(writer.getPath(), "$.5");
    writer.value(1);
    assertEquals(writer.getPath(), "$.5");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{\"5\":1}");
  }

  @Test
  public void writerDoubleValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value(5.5d);
    assertEquals(writer.getPath(), "$.5.5");
    writer.value(1);
    assertEquals(writer.getPath(), "$.5.5");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{\"5.5\":1}");
  }

  @Test
  public void writerBooleanValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.value(true);
      fail();
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Boolean cannot be used as a map key in JSON at path $."));
    }
    writer.value("true");
    assertEquals(writer.getPath(), "$.true");
    writer.value(1);
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{\"true\":1}");
  }

  @Test
  public void writerLongValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    writer.value(5L);
    assertEquals(writer.getPath(), "$.5");
    writer.value(1);
    assertEquals(writer.getPath(), "$.5");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{\"5\":1}");
  }

  @Test
  public void writerNullValue() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.nullValue();
      fail();
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("null cannot be used as a map key in JSON at path $."));
    }
    writer.value("null");
    assertEquals(writer.getPath(), "$.null");
    writer.value(1);
    assertEquals(writer.getPath(), "$.null");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{\"null\":1}");
  }

  @Test
  public void writerMultipleValueObject() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.name("a");
    writer.value(1);
    writer.promoteValueToName();
    writer.value("b");
    assertEquals(writer.getPath(), "$.b");
    writer.value(2);
    assertEquals(writer.getPath(), "$.b");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{\"a\":1,\"b\":2}");
  }

  @Test
  public void writerEmptyValueObject() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    assertEquals(writer.getPath(), "$.");
    writer.endObject();
    assertEquals(writer.getPath(), "$");
    assertEquals(factory.json(), "{}");
  }

  @Test
  public void writerUnusedPromotionDoesntPersist() throws Exception {
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

  @Test
  public void writerSourceValueFails() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.value(new Buffer().writeUtf8("\"a\""));
      fail();
    } catch (IllegalStateException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("BufferedSource cannot be used as a map key in JSON at path $."));
    }
    writer.value("a");
    writer.value("a value");
    writer.endObject();
    assertEquals(factory.json(), "{\"a\":\"a value\"}");
  }

  @Test
  public void writerValueSinkFails() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    writer.promoteValueToName();
    try {
      writer.valueSink();
      fail();
    } catch (IllegalStateException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("BufferedSink cannot be used as a map key in JSON at path $."));
    }
    writer.value("a");
    writer.value("a value");
    writer.endObject();
    assertEquals(factory.json(), "{\"a\":\"a value\"}");
  }
}
