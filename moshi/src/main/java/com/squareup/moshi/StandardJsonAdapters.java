/*
 * Copyright (C) 2014 Square, Inc.
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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;

final class StandardJsonAdapters {
  public static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {
    @Override public JsonAdapter<?> create(Type type, AnnotatedElement annotations, Moshi moshi) {
      if (type == boolean.class) return BOOLEAN_JSON_ADAPTER;
      if (type == byte.class) return BYTE_JSON_ADAPTER;
      if (type == char.class) return CHARACTER_JSON_ADAPTER;
      if (type == double.class) return DOUBLE_JSON_ADAPTER;
      if (type == float.class) return FLOAT_JSON_ADAPTER;
      if (type == int.class) return INTEGER_JSON_ADAPTER;
      if (type == long.class) return LONG_JSON_ADAPTER;
      if (type == short.class) return SHORT_JSON_ADAPTER;
      if (type == Boolean.class) return BOOLEAN_JSON_ADAPTER.nullSafe();
      if (type == Byte.class) return BYTE_JSON_ADAPTER.nullSafe();
      if (type == Character.class) return CHARACTER_JSON_ADAPTER.nullSafe();
      if (type == Double.class) return DOUBLE_JSON_ADAPTER.nullSafe();
      if (type == Float.class) return FLOAT_JSON_ADAPTER.nullSafe();
      if (type == Integer.class) return INTEGER_JSON_ADAPTER.nullSafe();
      if (type == Long.class) return LONG_JSON_ADAPTER.nullSafe();
      if (type == Short.class) return SHORT_JSON_ADAPTER.nullSafe();
      if (type == String.class) return STRING_JSON_ADAPTER.nullSafe();
      return null;
    }
  };

  private static final String ERROR_FORMAT = "Expected %s but was %s at path %s";

  private static int rangeCheckNextInt(JsonReader reader, String typeMessage, int min, int max)
      throws IOException {
    int value = reader.nextInt();
    if (value < min || value > max) {
      throw new NumberFormatException(
          String.format(ERROR_FORMAT, typeMessage, value, reader.getPath()));
    }
    return value;
  }

  static final JsonAdapter<Boolean> BOOLEAN_JSON_ADAPTER = new JsonAdapter<Boolean>() {
    @Override public Boolean fromJson(JsonReader reader) throws IOException {
      return reader.nextBoolean();
    }

    @Override public void toJson(JsonWriter writer, Boolean value) throws IOException {
      writer.value(value);
    }
  };

  static final JsonAdapter<Byte> BYTE_JSON_ADAPTER = new JsonAdapter<Byte>() {
    @Override public Byte fromJson(JsonReader reader) throws IOException {
      return (byte) rangeCheckNextInt(reader, "a byte", Byte.MIN_VALUE, 0xFF);
    }

    @Override public void toJson(JsonWriter writer, Byte value) throws IOException {
      writer.value(value.intValue() & 0xFF);
    }
  };

  static final JsonAdapter<Character> CHARACTER_JSON_ADAPTER = new JsonAdapter<Character>() {
    @Override public Character fromJson(JsonReader reader) throws IOException {
      String value = reader.nextString();
      if (value.length() > 1) {
        throw new IllegalStateException(
            String.format(ERROR_FORMAT, "a char", '"' + value + '"', reader.getPath()));
      }
      return value.charAt(0);
    }

    @Override public void toJson(JsonWriter writer, Character value) throws IOException {
      writer.value(value.toString());
    }
  };

  static final JsonAdapter<Double> DOUBLE_JSON_ADAPTER = new JsonAdapter<Double>() {
    @Override public Double fromJson(JsonReader reader) throws IOException {
      return reader.nextDouble();
    }

    @Override public void toJson(JsonWriter writer, Double value) throws IOException {
      writer.value(value.doubleValue());
    }
  };

  static final JsonAdapter<Float> FLOAT_JSON_ADAPTER = new JsonAdapter<Float>() {
    @Override public Float fromJson(JsonReader reader) throws IOException {
      float value = (float) reader.nextDouble();
      // Double check for infinity after float conversion; many doubles > Float.MAX
      if (!reader.isLenient() && Float.isInfinite(value)) {
        throw new NumberFormatException("JSON forbids NaN and infinities: " + value
            + " at path " + reader.getPath());
      }
      return value;
    }

    @Override public void toJson(JsonWriter writer, Float value) throws IOException {
      // Manual null pointer check for the float.class adapter.
      if (value == null) {
        throw new NullPointerException();
      }
      // Use the Number overload so we write out float precision instead of double precision.
      writer.value(value);
    }
  };

  static final JsonAdapter<Integer> INTEGER_JSON_ADAPTER = new JsonAdapter<Integer>() {
    @Override public Integer fromJson(JsonReader reader) throws IOException {
      return reader.nextInt();
    }

    @Override public void toJson(JsonWriter writer, Integer value) throws IOException {
      writer.value(value.intValue());
    }
  };

  static final JsonAdapter<Long> LONG_JSON_ADAPTER = new JsonAdapter<Long>() {
    @Override public Long fromJson(JsonReader reader) throws IOException {
      return reader.nextLong();
    }

    @Override public void toJson(JsonWriter writer, Long value) throws IOException {
      writer.value(value.longValue());
    }
  };

  static final JsonAdapter<Short> SHORT_JSON_ADAPTER = new JsonAdapter<Short>() {
    @Override public Short fromJson(JsonReader reader) throws IOException {
      return (short) rangeCheckNextInt(reader, "a short", Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override public void toJson(JsonWriter writer, Short value) throws IOException {
      writer.value(value.intValue());
    }
  };

  static final JsonAdapter<String> STRING_JSON_ADAPTER = new JsonAdapter<String>() {
    @Override public String fromJson(JsonReader reader) throws IOException {
      return reader.nextString();
    }

    @Override public void toJson(JsonWriter writer, String value) throws IOException {
      writer.value(value);
    }
  };
}
