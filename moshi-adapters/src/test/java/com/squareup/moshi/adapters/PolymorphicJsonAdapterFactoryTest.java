/*
 * Copyright (C) 2018 Square, Inc.
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
package com.squareup.moshi.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import okio.Buffer;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

@SuppressWarnings("CheckReturnValue")
public final class PolymorphicJsonAdapterFactoryTest {
  @Test
  public void fromJson() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    assertEquals(
        adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"), new Success("Okay!"));
    assertEquals(
        adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"),
        new Error(Collections.<String, Object>singletonMap("order", 66d)));
  }

  @Test
  public void toJson() {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    assertEquals(
        adapter.toJson(new Success("Okay!")), "{\"type\":\"success\",\"value\":\"Okay!\"}");
    assertEquals(
        adapter.toJson(new Error(Collections.singletonMap("order", 66))),
        "{\"type\":\"error\",\"error_logs\":{\"order\":66}}");
  }

  @Test
  public void unregisteredLabelValue() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    JsonReader reader =
        JsonReader.of(new Buffer().writeUtf8("{\"type\":\"data\",\"value\":\"Okay!\"}"));
    try {
      adapter.fromJson(reader);
      fail();
    } catch (JsonDataException expected) {
      assertEquals(
          "Expected one of [success, error] for key 'type' but found"
              + " 'data'. Register a subtype for this label.",
          expected.getMessage());
    }
    assertEquals(reader.peek(), JsonReader.Token.BEGIN_OBJECT);
  }

  @Test
  public void specifiedFallbackSubtype() throws IOException {
    Error fallbackError = new Error(Collections.<String, Object>emptyMap());
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error")
                    .withDefaultValue(fallbackError))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    Message message = adapter.fromJson("{\"type\":\"data\",\"value\":\"Okay!\"}");
    assertEquals(message.getClass(), fallbackError.getClass());
    assertEquals(message.getClass().getName(), fallbackError.getClass().getName());
  }

  @Test
  public void specifiedNullFallbackSubtype() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error")
                    .withDefaultValue(null))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    Message message = adapter.fromJson("{\"type\":\"data\",\"value\":\"Okay!\"}");
    assertNull(message);
  }

  @Test
  public void specifiedFallbackJsonAdapter() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error")
                    .withFallbackJsonAdapter(
                        new JsonAdapter<Object>() {
                          @Override
                          public Object fromJson(JsonReader reader) throws IOException {
                            reader.beginObject();
                            assertEquals(reader.nextName(), "type");
                            assertEquals(reader.nextString(), "data");
                            assertEquals(reader.nextName(), "value");
                            assertEquals(reader.nextString(), "Okay!");
                            reader.endObject();
                            return new EmptyMessage();
                          }

                          @Override
                          public void toJson(JsonWriter writer, @Nullable Object value) {
                            throw new AssertionError();
                          }
                        }))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    JsonReader reader =
        JsonReader.of(new Buffer().writeUtf8("{\"type\":\"data\",\"value\":\"Okay!\"}"));

    Message message = adapter.fromJson(reader);
    assertTrue(message instanceof EmptyMessage);
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void unregisteredSubtype() {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    try {
      adapter.toJson(new EmptyMessage());
    } catch (IllegalArgumentException expected) {
      assertEquals(
          "Expected one of [class"
              + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Success, class"
              + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Error] but found"
              + " EmptyMessage, a class"
              + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$EmptyMessage. Register"
              + " this subtype.",
          expected.getMessage());
    }
  }

  @Test
  public void unregisteredSubtypeWithDefaultValue() {
    Error fallbackError = new Error(Collections.<String, Object>emptyMap());
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error")
                    .withDefaultValue(fallbackError))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    try {
      adapter.toJson(new EmptyMessage());
    } catch (IllegalArgumentException expected) {
      assertEquals(
          "Expected one of [class"
              + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Success, class"
              + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Error] but found"
              + " EmptyMessage, a class"
              + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$EmptyMessage. Register"
              + " this subtype.",
          expected.getMessage());
    }
  }

  @Test
  public void unregisteredSubtypeWithFallbackJsonAdapter() {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error")
                    .withFallbackJsonAdapter(
                        new JsonAdapter<Object>() {
                          @Override
                          public Object fromJson(JsonReader reader) {
                            throw new RuntimeException(
                                "Not implemented as not needed for the test");
                          }

                          @Override
                          public void toJson(JsonWriter writer, Object value) throws IOException {
                            writer.name("type").value("injected by fallbackJsonAdapter");
                          }
                        }))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    String json = adapter.toJson(new EmptyMessage());
    assertEquals(json, "{\"type\":\"injected by fallbackJsonAdapter\"}");
  }

  @Test
  public void nonStringLabelValue() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    try {
      adapter.fromJson("{\"type\":{},\"value\":\"Okay!\"}");
      fail();
    } catch (JsonDataException expected) {
      assertEquals("Expected a string but was BEGIN_OBJECT at path $.type", expected.getMessage());
    }
  }

  @Test
  public void nonObjectDoesNotConsume() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    JsonReader reader = JsonReader.of(new Buffer().writeUtf8("\"Failure\""));
    try {
      adapter.fromJson(reader);
      fail();
    } catch (JsonDataException expected) {
      assertEquals("Expected BEGIN_OBJECT but was STRING at path $", expected.getMessage());
    }
    assertEquals(reader.nextString(), "Failure");
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void nonUniqueSubtypes() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Success.class, "data")
                    .withSubtype(Error.class, "error"))
            .build();

    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    assertEquals(
        adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"), new Success("Okay!"));
    assertEquals(adapter.fromJson("{\"type\":\"data\",\"value\":\"Data!\"}"), new Success("Data!"));
    assertEquals(
        adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"),
        new Error(Collections.<String, Object>singletonMap("order", 66d)));
    assertEquals(
        adapter.toJson(new Success("Data!")), "{\"type\":\"success\",\"value\":\"Data!\"}");
  }

  @Test
  public void uniqueLabels() {
    PolymorphicJsonAdapterFactory<Message> factory =
        PolymorphicJsonAdapterFactory.of(Message.class, "type").withSubtype(Success.class, "data");
    try {
      factory.withSubtype(Error.class, "data");
      fail();
    } catch (IllegalArgumentException expected) {
      assertEquals("Labels must be unique.", expected.getMessage());
    }
  }

  @Test
  public void nullSafe() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(Error.class, "error"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    JsonReader reader = JsonReader.of(new Buffer().writeUtf8("null"));
    assertNull(adapter.fromJson(reader));
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  /**
   * Longs that do not have an exact double representation are problematic for JSON. It is a bad
   * idea to use JSON for these values! But Moshi tries to retain long precision where possible.
   */
  @Test
  public void unportableTypes() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(MessageWithUnportableTypes.class, "unportable"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    assertEquals(
        adapter.toJson(new MessageWithUnportableTypes(9007199254740993L)),
        "{\"type\":\"unportable\",\"long_value\":9007199254740993}");
    MessageWithUnportableTypes decoded =
        (MessageWithUnportableTypes)
            adapter.fromJson("{\"type\":\"unportable\",\"long_value\":9007199254740993}");
    assertEquals(decoded.long_value, 9007199254740993L);
  }

  @Test
  public void failOnUnknownMissingTypeLabel() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(MessageWithType.class, "success"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class).failOnUnknown();

    MessageWithType decoded =
        (MessageWithType) adapter.fromJson("{\"value\":\"Okay!\",\"type\":\"success\"}");
    assertEquals(decoded.value, "Okay!");
  }

  interface Message {}

  static final class Success implements Message {
    final String value;

    Success(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Success)) return false;
      Success success = (Success) o;
      return value.equals(success.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  static final class Error implements Message {
    final Map<String, Object> error_logs;

    Error(Map<String, Object> error_logs) {
      this.error_logs = error_logs;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Error)) return false;
      Error error = (Error) o;
      return error_logs.equals(error.error_logs);
    }

    @Override
    public int hashCode() {
      return error_logs.hashCode();
    }
  }

  static final class EmptyMessage implements Message {
    @Override
    public String toString() {
      return "EmptyMessage";
    }
  }

  static final class MessageWithUnportableTypes implements Message {
    final long long_value;

    MessageWithUnportableTypes(long long_value) {
      this.long_value = long_value;
    }
  }

  static final class MessageWithType implements Message {
    final String type;
    final String value;

    MessageWithType(String type, String value) {
      this.type = type;
      this.value = value;
    }
  }
}
