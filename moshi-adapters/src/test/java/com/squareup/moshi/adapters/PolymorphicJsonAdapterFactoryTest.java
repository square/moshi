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

import static com.google.common.truth.Truth.assertThat;
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

    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"))
        .isEqualTo(new Success("Okay!"));
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
        .isEqualTo(new Error(Collections.<String, Object>singletonMap("order", 66d)));
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

    assertThat(adapter.toJson(new Success("Okay!")))
        .isEqualTo("{\"type\":\"success\",\"value\":\"Okay!\"}");
    assertThat(adapter.toJson(new Error(Collections.<String, Object>singletonMap("order", 66))))
        .isEqualTo("{\"type\":\"error\",\"error_logs\":{\"order\":66}}");
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
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(
              "Expected one of [success, error] for key 'type' but found"
                  + " 'data'. Register a subtype for this label.");
    }
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.BEGIN_OBJECT);
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
    assertThat(message).isSameInstanceAs(fallbackError);
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
    assertThat(message).isNull();
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
                            assertThat(reader.nextName()).isEqualTo("type");
                            assertThat(reader.nextString()).isEqualTo("data");
                            assertThat(reader.nextName()).isEqualTo("value");
                            assertThat(reader.nextString()).isEqualTo("Okay!");
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
    assertThat(message).isInstanceOf(EmptyMessage.class);
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(
              "Expected one of [class"
                  + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Success, class"
                  + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Error] but found"
                  + " EmptyMessage, a class"
                  + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$EmptyMessage. Register"
                  + " this subtype.");
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
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(
              "Expected one of [class"
                  + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Success, class"
                  + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$Error] but found"
                  + " EmptyMessage, a class"
                  + " com.squareup.moshi.adapters.PolymorphicJsonAdapterFactoryTest$EmptyMessage. Register"
                  + " this subtype.");
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
    assertThat(json).isEqualTo("{\"type\":\"injected by fallbackJsonAdapter\"}");
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
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Expected a string but was BEGIN_OBJECT at path $.type");
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
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Expected BEGIN_OBJECT but was STRING at path $");
    }
    assertThat(reader.nextString()).isEqualTo("Failure");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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

    assertThat(adapter.fromJson("{\"type\":\"success\",\"value\":\"Okay!\"}"))
        .isEqualTo(new Success("Okay!"));
    assertThat(adapter.fromJson("{\"type\":\"data\",\"value\":\"Data!\"}"))
        .isEqualTo(new Success("Data!"));
    assertThat(adapter.fromJson("{\"type\":\"error\",\"error_logs\":{\"order\":66}}"))
        .isEqualTo(new Error(Collections.<String, Object>singletonMap("order", 66d)));
    assertThat(adapter.toJson(new Success("Data!")))
        .isEqualTo("{\"type\":\"success\",\"value\":\"Data!\"}");
  }

  @Test
  public void uniqueLabels() {
    PolymorphicJsonAdapterFactory<Message> factory =
        PolymorphicJsonAdapterFactory.of(Message.class, "type").withSubtype(Success.class, "data");
    try {
      factory.withSubtype(Error.class, "data");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("Labels must be unique.");
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
    assertThat(adapter.fromJson(reader)).isNull();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
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

    assertThat(adapter.toJson(new MessageWithUnportableTypes(9007199254740993L)))
        .isEqualTo("{\"type\":\"unportable\",\"long_value\":9007199254740993}");
    MessageWithUnportableTypes decoded =
        (MessageWithUnportableTypes)
            adapter.fromJson("{\"type\":\"unportable\",\"long_value\":9007199254740993}");
    assertThat(decoded.long_value).isEqualTo(9007199254740993L);
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
    assertThat(decoded.value).isEqualTo("Okay!");
  }

  @Test
  public void toJsonAutoTypeSerializationOn() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(MessageWithType.class, "customType"))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    assertThat(adapter.toJson(new Success("Okay!")))
        .isEqualTo("{\"type\":\"success\",\"value\":\"Okay!\"}");

    // Ensures that when the `type` label auto serialization is on,
    // it does continue to serialize the `type` along with class property `type` (twice)
    assertThat(adapter.toJson(new MessageWithType("customType", "Object with type property")))
        .isEqualTo(
            "{\"type\":\"customType\",\"type\":\"customType\",\"value\":\"Object with type property\"}");
  }

  @Test
  public void toJsonAutoTypeSerializationOff() throws IOException {
    Moshi moshi =
        new Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Message.class, "type")
                    .withSubtype(Success.class, "success")
                    .withSubtype(MessageWithType.class, "customType")
                    .autoSerializeLabel(false))
            .build();
    JsonAdapter<Message> adapter = moshi.adapter(Message.class);

    // Validates that when `type` label auto serialization is off,
    // it does NOT serialize the type label key and value
    assertThat(adapter.toJson(new Success("Okay!"))).isEqualTo("{\"value\":\"Okay!\"}");

    // Validates that when the `type` label auto serialization is off,
    // it continues to serialize the class property `type` but not from the factory
    assertThat(adapter.toJson(new MessageWithType("customType", "Object with type property")))
        .isEqualTo("{\"type\":\"customType\",\"value\":\"Object with type property\"}");
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
