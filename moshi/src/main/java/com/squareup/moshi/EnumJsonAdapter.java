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

/**
 * Convert Enum types to JSON string values
 *
 * @param <T> Enum class to adapt
 */
final class EnumJsonAdapter<T extends Enum<T>> extends JsonAdapter<T> {
  public static final Factory FACTORY = new Factory() {
    @Override public JsonAdapter<? extends Enum> create(Type type, AnnotatedElement annotations,
        Moshi moshi) {
      Class<?> rawType = Types.getRawType(type);
      if (Enum.class.isAssignableFrom(rawType)) {
        //noinspection unchecked
        Class<? extends Enum> enumType = (Class<? extends Enum>) rawType;
        return new EnumJsonAdapter<>(enumType.getEnumConstants()).nullSafe();
      }
      return null;
    }
  };

  private final T[] values;

  private EnumJsonAdapter(T[] values) {
    this.values = values;
  }

  @Override
  public T fromJson(JsonReader reader) throws IOException {
    String name = reader.nextString();
    for (T value : values) {
      if (value.name().equals(name)) {
        return value;
      }
    }
    return null;
  }

  @Override
  public void toJson(JsonWriter writer, T value) throws IOException {
    writer.value(value.name());
  }
}
