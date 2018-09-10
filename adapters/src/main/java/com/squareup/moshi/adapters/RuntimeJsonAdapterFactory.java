/*
 * Copyright (C) 2011 Google Inc.
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
package com.squareup.moshi.adapters;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.internal.Util;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckReturnValue;

/**
 * A JsonAdapter factory for polymorphic types. This is useful when the type is not known before
 * decoding the JSON. This factory's adapters expect JSON in the format of a JSON object with a
 * key whose value is a label that determines the type to which to map the JSON object.
 */
public final class RuntimeJsonAdapterFactory<T> implements JsonAdapter.Factory {
  final Class<T> baseType;
  final String labelKey;
  final Map<String, Type> labelToType = new LinkedHashMap<>();

  /**
   * @param baseType The base type for which this factory will create adapters.
   * @param labelKey The key in the JSON object whose value determines the type to which to map the
   *     JSON object.
   */
  @CheckReturnValue
  public static <T> RuntimeJsonAdapterFactory<T> of(Class<T> baseType, String labelKey) {
    if (baseType == null) throw new NullPointerException("baseType == null");
    if (labelKey == null) throw new NullPointerException("labelKey == null");
    return new RuntimeJsonAdapterFactory<>(baseType, labelKey);
  }

  RuntimeJsonAdapterFactory(Class<T> baseType, String labelKey) {
    this.baseType = baseType;
    this.labelKey = labelKey;
  }

  /**
   * Register the subtype that can be created based on the label. When an unknown type is found
   * during encoding an {@linkplain IllegalArgumentException} will be thrown. When an unknown label
   * is found during decoding a {@linkplain JsonDataException} will be thrown.
   */
  public RuntimeJsonAdapterFactory<T> registerSubtype(Class<? extends T> subtype, String label) {
    if (subtype == null) throw new NullPointerException("subtype == null");
    if (label == null) throw new NullPointerException("label == null");
    if (labelToType.containsKey(label) || labelToType.containsValue(subtype)) {
      throw new IllegalArgumentException("Subtypes and labels must be unique.");
    }
    labelToType.put(label, subtype);
    return this;
  }

  @Override
  public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
    if (Types.getRawType(type) != baseType || !annotations.isEmpty()) {
      return null;
    }
    int size = labelToType.size();
    Map<String, JsonAdapter<Object>> labelToAdapter = new LinkedHashMap<>(size);
    Map<Type, String> typeToLabel = new LinkedHashMap<>(size);
    for (Map.Entry<String, Type> entry : labelToType.entrySet()) {
      String label = entry.getKey();
      Type typeValue = entry.getValue();
      typeToLabel.put(typeValue, label);
      labelToAdapter.put(label, moshi.adapter(typeValue));
    }
    JsonAdapter<Object> objectJsonAdapter = moshi.nextAdapter(
        this, Object.class, Util.NO_ANNOTATIONS);
    return new RuntimeJsonAdapter(labelKey, labelToAdapter, typeToLabel, objectJsonAdapter)
        .nullSafe();
  }

  static final class RuntimeJsonAdapter extends JsonAdapter<Object> {
    final String labelKey;
    final Map<String, JsonAdapter<Object>> labelToAdapter;
    final Map<Type, String> typeToLabel;
    final JsonAdapter<Object> objectJsonAdapter;

    RuntimeJsonAdapter(String labelKey, Map<String, JsonAdapter<Object>> labelToAdapter,
        Map<Type, String> typeToLabel, JsonAdapter<Object> objectJsonAdapter) {
      this.labelKey = labelKey;
      this.labelToAdapter = labelToAdapter;
      this.typeToLabel = typeToLabel;
      this.objectJsonAdapter = objectJsonAdapter;
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      JsonReader.Token peekedToken = reader.peek();
      if (peekedToken != JsonReader.Token.BEGIN_OBJECT) {
        throw new JsonDataException("Expected BEGIN_OBJECT but was " + peekedToken
            + " at path " + reader.getPath());
      }
      Object jsonValue = reader.readJsonValue();
      Map<String, Object> jsonObject = (Map<String, Object>) jsonValue;
      Object label = jsonObject.get(labelKey);
      if (label == null) {
        throw new JsonDataException("Missing label for " + labelKey);
      }
      if (!(label instanceof String)) {
        throw new JsonDataException("Label for '"
            + labelKey
            + "' must be a string but was "
            + label
            + ", a "
            + label.getClass());
      }
      JsonAdapter<Object> adapter = labelToAdapter.get(label);
      if (adapter == null) {
        throw new JsonDataException("Expected one of "
            + labelToAdapter.keySet()
            + " for key '"
            + labelKey
            + "' but found '"
            + label
            + "'. Register a subtype for this label.");
      }
      return adapter.fromJsonValue(jsonValue);
    }

    @Override public void toJson(JsonWriter writer, Object value) throws IOException {
      Class<?> type = value.getClass();
      String label = typeToLabel.get(type);
      if (label == null) {
        throw new IllegalArgumentException("Expected one of "
            + typeToLabel.keySet()
            + " but found "
            + value
            + ", a "
            + value.getClass()
            + ". Register this subtype.");
      }
      JsonAdapter<Object> adapter = labelToAdapter.get(label);
      Map<String, Object> jsonValue = (Map<String, Object>) adapter.toJsonValue(value);

      Map<String, Object> valueWithLabel = new LinkedHashMap<>(1 + jsonValue.size());
      valueWithLabel.put(labelKey, label);
      valueWithLabel.putAll(jsonValue);
      objectJsonAdapter.toJson(writer, valueWithLabel);
    }

    @Override public String toString() {
      return "RuntimeJsonAdapter(" + labelKey + ")";
    }
  }
}
