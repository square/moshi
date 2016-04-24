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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StandardJsonAdapters {
  public static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {
    @Override public JsonAdapter<?> create(
        Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (!annotations.isEmpty()) return null;
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
      if (type == Object.class) return new ObjectJsonAdapter(moshi).nullSafe();

      Class<?> rawType = Types.getRawType(type);
      if (rawType.isEnum()) {
        //noinspection unchecked
        return new EnumJsonAdapter<>((Class<? extends Enum>) rawType).nullSafe();
      }
      return null;
    }
  };

  private static final String ERROR_FORMAT = "Expected %s but was %s at path %s";

  static int rangeCheckNextInt(JsonReader reader, String typeMessage, int min, int max)
      throws IOException {
    int value = reader.nextInt();
    if (value < min || value > max) {
      throw new JsonDataException(
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

    @Override public String toString() {
      return "JsonAdapter(Boolean)";
    }
  };

  static final JsonAdapter<Byte> BYTE_JSON_ADAPTER = new JsonAdapter<Byte>() {
    @Override public Byte fromJson(JsonReader reader) throws IOException {
      return (byte) rangeCheckNextInt(reader, "a byte", Byte.MIN_VALUE, 0xff);
    }

    @Override public void toJson(JsonWriter writer, Byte value) throws IOException {
      writer.value(value.intValue() & 0xff);
    }

    @Override public String toString() {
      return "JsonAdapter(Byte)";
    }
  };

  static final JsonAdapter<Character> CHARACTER_JSON_ADAPTER = new JsonAdapter<Character>() {
    @Override public Character fromJson(JsonReader reader) throws IOException {
      String value = reader.nextString();
      if (value.length() > 1) {
        throw new JsonDataException(
            String.format(ERROR_FORMAT, "a char", '"' + value + '"', reader.getPath()));
      }
      return value.charAt(0);
    }

    @Override public void toJson(JsonWriter writer, Character value) throws IOException {
      writer.value(value.toString());
    }

    @Override public String toString() {
      return "JsonAdapter(Character)";
    }
  };

  static final JsonAdapter<Double> DOUBLE_JSON_ADAPTER = new JsonAdapter<Double>() {
    @Override public Double fromJson(JsonReader reader) throws IOException {
      return reader.nextDouble();
    }

    @Override public void toJson(JsonWriter writer, Double value) throws IOException {
      writer.value(value.doubleValue());
    }

    @Override public String toString() {
      return "JsonAdapter(Double)";
    }
  };

  static final JsonAdapter<Float> FLOAT_JSON_ADAPTER = new JsonAdapter<Float>() {
    @Override public Float fromJson(JsonReader reader) throws IOException {
      float value = (float) reader.nextDouble();
      // Double check for infinity after float conversion; many doubles > Float.MAX
      if (!reader.isLenient() && Float.isInfinite(value)) {
        throw new JsonDataException("JSON forbids NaN and infinities: " + value
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

    @Override public String toString() {
      return "JsonAdapter(Float)";
    }
  };

  static final JsonAdapter<Integer> INTEGER_JSON_ADAPTER = new JsonAdapter<Integer>() {
    @Override public Integer fromJson(JsonReader reader) throws IOException {
      return reader.nextInt();
    }

    @Override public void toJson(JsonWriter writer, Integer value) throws IOException {
      writer.value(value.intValue());
    }

    @Override public String toString() {
      return "JsonAdapter(Integer)";
    }
  };

  static final JsonAdapter<Long> LONG_JSON_ADAPTER = new JsonAdapter<Long>() {
    @Override public Long fromJson(JsonReader reader) throws IOException {
      return reader.nextLong();
    }

    @Override public void toJson(JsonWriter writer, Long value) throws IOException {
      writer.value(value.longValue());
    }

    @Override public String toString() {
      return "JsonAdapter(Long)";
    }
  };

  static final JsonAdapter<Short> SHORT_JSON_ADAPTER = new JsonAdapter<Short>() {
    @Override public Short fromJson(JsonReader reader) throws IOException {
      return (short) rangeCheckNextInt(reader, "a short", Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override public void toJson(JsonWriter writer, Short value) throws IOException {
      writer.value(value.intValue());
    }

    @Override public String toString() {
      return "JsonAdapter(Short)";
    }
  };

  static final JsonAdapter<String> STRING_JSON_ADAPTER = new JsonAdapter<String>() {
    @Override public String fromJson(JsonReader reader) throws IOException {
      return reader.nextString();
    }

    @Override public void toJson(JsonWriter writer, String value) throws IOException {
      writer.value(value);
    }

    @Override public String toString() {
      return "JsonAdapter(String)";
    }
  };

  static final class EnumJsonAdapter<T extends Enum<T>> extends JsonAdapter<T> {
    private final Class<T> enumType;
    private final Map<String, T> nameConstantMap;
    private final String[] nameStrings;
    private final T[] constants;
    private final JsonReader.Options options;

    public EnumJsonAdapter(Class<T> enumType) {
      this.enumType = enumType;
      try {
        constants = enumType.getEnumConstants();
        nameConstantMap = new LinkedHashMap<>();
        nameStrings = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
          T constant = constants[i];
          Json annotation = enumType.getField(constant.name()).getAnnotation(Json.class);
          String name = annotation != null ? annotation.name() : constant.name();
          nameConstantMap.put(name, constant);
          nameStrings[i] = name;
        }
        options = JsonReader.Options.of(nameStrings);
      } catch (NoSuchFieldException e) {
        throw new AssertionError("Missing field in " + enumType.getName(), e);
      }
    }

    @Override public T fromJson(JsonReader reader) throws IOException {
      int index = reader.selectString(options);
      if (index != -1) return constants[index];

      String name = reader.nextString();
      T constant = nameConstantMap.get(name);
      if (constant != null) return constant;
      throw new JsonDataException("Expected one of "
          + nameConstantMap.keySet() + " but was " + name + " at path "
          + reader.getPath());
    }

    @Override public void toJson(JsonWriter writer, T value) throws IOException {
      writer.value(nameStrings[value.ordinal()]);
    }

    @Override public String toString() {
      return "JsonAdapter(" + enumType.getName() + ")";
    }
  }

  /**
   * This adapter is used when the declared type is {@code java.lang.Object}. Typically the runtime
   * type is something else, and when encoding JSON this delegates to the runtime type's adapter.
   * For decoding (where there is no runtime type to inspect), this uses maps and lists.
   *
   * <p>This adapter needs a Moshi instance to look up the appropriate adapter for runtime types as
   * they are encountered.
   */
  static final class ObjectJsonAdapter extends JsonAdapter<Object> {
    private final Moshi moshi;

    public ObjectJsonAdapter(Moshi moshi) {
      this.moshi = moshi;
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      switch (reader.peek()) {
        case BEGIN_ARRAY:
          List<Object> list = new ArrayList<>();
          reader.beginArray();
          while (reader.hasNext()) {
            list.add(fromJson(reader));
          }
          reader.endArray();
          return list;

        case BEGIN_OBJECT:
          Map<String, Object> map = new LinkedHashTreeMap<>();
          reader.beginObject();
          while (reader.hasNext()) {
            map.put(reader.nextName(), fromJson(reader));
          }
          reader.endObject();
          return map;

        case STRING:
          return reader.nextString();

        case NUMBER:
          return reader.nextDouble();

        case BOOLEAN:
          return reader.nextBoolean();

        case NULL:
          return reader.nextNull();

        default:
          throw new IllegalStateException("Expected a value but was " + reader.peek()
              + " at path " + reader.getPath());
      }
    }

    @Override public void toJson(JsonWriter writer, Object value) throws IOException {
      Class<?> valueClass = value.getClass();
      if (valueClass == Object.class) {
        // Don't recurse infinitely when the runtime type is also Object.class.
        writer.beginObject();
        writer.endObject();
      } else {
        moshi.adapter(toJsonType(valueClass), Util.NO_ANNOTATIONS).toJson(writer, value);
      }
    }

    /**
     * Returns the type to look up a type adapter for when writing {@code value} to JSON. Without
     * this, attempts to emit standard types like `LinkedHashMap` would fail because Moshi doesn't
     * provide built-in adapters for implementation types. It knows how to <strong>write</strong>
     * those types, but lacks a mechanism to read them because it doesn't know how to find the
     * appropriate constructor.
     */
    private Class<?> toJsonType(Class<?> valueClass) {
      if (Map.class.isAssignableFrom(valueClass)) return Map.class;
      if (Collection.class.isAssignableFrom(valueClass)) return Collection.class;
      return valueClass;
    }

    @Override public String toString() {
      return "JsonAdapter(Object)";
    }
  }
}
