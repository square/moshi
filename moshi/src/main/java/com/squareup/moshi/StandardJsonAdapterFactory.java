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

final class StandardJsonAdapterFactory implements JsonAdapter.Factory {
  static final JsonAdapter<Boolean> BOOLEAN_JSON_ADAPTER = new JsonAdapter<Boolean>() {
    @Override public Boolean fromJson(JsonReader reader) throws IOException {
      return reader.nextBoolean();
    }
    @Override public void toJson(JsonWriter writer, Boolean value) throws IOException {
      writer.value(value);
    }
  };

  static final JsonAdapter<Double> DOUBLE_JSON_ADAPTER = new JsonAdapter<Double>() {
    @Override public Double fromJson(JsonReader reader) throws IOException {
      return reader.nextDouble();
    }
    @Override public void toJson(JsonWriter writer, Double value) throws IOException {
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

  static final JsonAdapter<String> STRING_JSON_ADAPTER = new JsonAdapter<String>() {
    @Override public String fromJson(JsonReader reader) throws IOException {
      return reader.nextString();
    }
    @Override public void toJson(JsonWriter writer, String value) throws IOException {
      writer.value(value);
    }
  };

  @Override public JsonAdapter<?> create(
      Type type, AnnotatedElement annotations, Moshi moshi) {
    // TODO: support all 8 primitive types.
    if (type == boolean.class) return BOOLEAN_JSON_ADAPTER;
    if (type == double.class) return DOUBLE_JSON_ADAPTER;
    if (type == int.class) return INTEGER_JSON_ADAPTER;
    if (type == Boolean.class) return BOOLEAN_JSON_ADAPTER.nullSafe();
    if (type == Double.class) return DOUBLE_JSON_ADAPTER.nullSafe();
    if (type == Integer.class) return INTEGER_JSON_ADAPTER.nullSafe();
    if (type == String.class) return STRING_JSON_ADAPTER.nullSafe();
    return null;
  }
}
