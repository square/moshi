/*
 * Copyright (C) 2018 Square, Inc.
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
package com.squareup.moshi.recipes;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class FallbackEnum {
  @Retention(RUNTIME)
  @JsonQualifier
  public @interface Fallback {
    /**
     * The enum name.
     */
    String value();
  }

  public static final class FallbackEnumJsonAdapter<T extends Enum<T>> extends JsonAdapter<T> {
    public static final Factory FACTORY = new Factory() {
      @Nullable @Override @SuppressWarnings("unchecked")
      public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        Class<?> rawType = Types.getRawType(type);
        if (!rawType.isEnum()) {
          return null;
        }
        if (annotations.size() != 1) {
          return null;
        }
        Annotation annotation = annotations.iterator().next();
        if (!(annotation instanceof Fallback)) {
          return null;
        }
        Class<Enum> enumType = (Class<Enum>) rawType;
        Enum<?> fallback = Enum.valueOf(enumType, ((Fallback) annotation).value());
        return new FallbackEnumJsonAdapter<>(enumType, fallback);
      }
    };

    final Class<T> enumType;
    final String[] nameStrings;
    final T[] constants;
    final JsonReader.Options options;
    final T defaultValue;

    FallbackEnumJsonAdapter(Class<T> enumType, T defaultValue) {
      this.enumType = enumType;
      this.defaultValue = defaultValue;
      try {
        constants = enumType.getEnumConstants();
        nameStrings = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
          T constant = constants[i];
          Json annotation = enumType.getField(constant.name()).getAnnotation(Json.class);
          String name = annotation != null ? annotation.name() : constant.name();
          nameStrings[i] = name;
        }
        options = JsonReader.Options.of(nameStrings);
      } catch (NoSuchFieldException e) {
        throw new AssertionError(e);
      }
    }

    @Override public T fromJson(JsonReader reader) throws IOException {
      int index = reader.selectString(options);
      if (index != -1) return constants[index];
      reader.nextString();
      return defaultValue;
    }

    @Override public void toJson(JsonWriter writer, T value) throws IOException {
      writer.value(nameStrings[value.ordinal()]);
    }

    @Override public String toString() {
      return "JsonAdapter(" + enumType.getName() + ").defaultValue( " + defaultValue + ")";
    }
  }

  static final class Example {
    enum Transportation {
      WALKING, BIKING, TRAINS, PLANES
    }

    @Fallback("WALKING") final Transportation transportation;

    Example(Transportation transportation) {
      this.transportation = transportation;
    }

    @Override public String toString() {
      return transportation.toString();
    }
  }

  public static void main(String[] args) throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(FallbackEnumJsonAdapter.FACTORY)
        .build();
    JsonAdapter<Example> adapter = moshi.adapter(Example.class);
    System.out.println(adapter.fromJson("{\"transportation\":\"CARS\"}"));
  }

  private FallbackEnum() {
  }
}
