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
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckReturnValue;

/**
 * A JsonAdapter factory for polymorphic types. This is useful when the type is not known before
 * decoding the JSON. This factory's adapters expect JSON in the format of a JSON object with a
 * key whose value is a label that determines the type to which to map the JSON object. To use, add
 * this factory to your {@link Moshi.Builder}:
 *
 * <pre> {@code
 *
 *   Moshi moshi = new Moshi.Builder()
 *       .add(RuntimeJsonAdapterFactory.of(Message.class, "type")
 *           .withSubtype(Success.class, "success")
 *           .withSubtype(Error.class, "error"))
 *       .build();
 * }</pre>
 */

public final class RuntimeJsonAdapterFactory<T> implements JsonAdapter.Factory {
  final Class<T> baseType;
  final String labelKey;
  final List<String> labels;
  final List<Type> subtypes;

  RuntimeJsonAdapterFactory(
      Class<T> baseType, String labelKey, List<String> labels, List<Type> subtypes) {
    this.baseType = baseType;
    this.labelKey = labelKey;
    this.labels = labels;
    this.subtypes = subtypes;
  }

  /**
   * @param baseType The base type for which this factory will create adapters. Cannot be Object.
   * @param labelKey The key in the JSON object whose value determines the type to which to map the
   *     JSON object.
   */
  @CheckReturnValue
  public static <T> RuntimeJsonAdapterFactory<T> of(Class<T> baseType, String labelKey) {
    if (baseType == null) throw new NullPointerException("baseType == null");
    if (labelKey == null) throw new NullPointerException("labelKey == null");
    if (baseType == Object.class) {
      throw new IllegalArgumentException(
          "The base type must not be Object. Consider using a marker interface.");
    }
    return new RuntimeJsonAdapterFactory<>(
        baseType, labelKey, Collections.<String>emptyList(), Collections.<Type>emptyList());
  }

  /**
   * Returns a new factory that decodes instances of {@code subtype}. When an unknown type is found
   * during encoding an {@linkplain IllegalArgumentException} will be thrown. When an unknown label
   * is found during decoding a {@linkplain JsonDataException} will be thrown.
   */
  public RuntimeJsonAdapterFactory<T> withSubtype(Class<? extends T> subtype, String label) {
    if (subtype == null) throw new NullPointerException("subtype == null");
    if (label == null) throw new NullPointerException("label == null");
    if (labels.contains(label) || subtypes.contains(subtype)) {
      throw new IllegalArgumentException("Subtypes and labels must be unique.");
    }
    List<String> newLabels = new ArrayList<>(labels);
    newLabels.add(label);
    List<Type> newSubtypes = new ArrayList<>(subtypes);
    newSubtypes.add(subtype);
    return new RuntimeJsonAdapterFactory<>(baseType, labelKey, newLabels, newSubtypes);
  }

  @Override
  public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
    if (Types.getRawType(type) != baseType || !annotations.isEmpty()) {
      return null;
    }

    List<JsonAdapter<Object>> jsonAdapters = new ArrayList<>(subtypes.size());
    for (int i = 0, size = subtypes.size(); i < size; i++) {
      jsonAdapters.add(moshi.adapter(subtypes.get(i)));
    }

    JsonAdapter<Object> objectJsonAdapter = moshi.adapter(Object.class);
    return new RuntimeJsonAdapter(
        labelKey, labels, subtypes, jsonAdapters, objectJsonAdapter).nullSafe();
  }

  static final class RuntimeJsonAdapter extends JsonAdapter<Object> {
    final String labelKey;
    final List<String> labels;
    final List<Type> subtypes;
    final List<JsonAdapter<Object>> jsonAdapters;
    final JsonAdapter<Object> objectJsonAdapter;

    /** Single-element options containing the label's key only. */
    final JsonReader.Options labelKeyOptions;
    /** Corresponds to subtypes. */
    final JsonReader.Options labelOptions;

    RuntimeJsonAdapter(String labelKey, List<String> labels,
        List<Type> subtypes, List<JsonAdapter<Object>> jsonAdapters,
        JsonAdapter<Object> objectJsonAdapter) {
      this.labelKey = labelKey;
      this.labels = labels;
      this.subtypes = subtypes;
      this.jsonAdapters = jsonAdapters;
      this.objectJsonAdapter = objectJsonAdapter;

      this.labelKeyOptions = JsonReader.Options.of(labelKey);
      this.labelOptions = JsonReader.Options.of(labels.toArray(new String[0]));
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      int labelIndex = labelIndex(reader.peekJson());
      return jsonAdapters.get(labelIndex).fromJson(reader);
    }

    private int labelIndex(JsonReader reader) throws IOException {
      reader.beginObject();
      while (reader.hasNext()) {
        if (reader.selectName(labelKeyOptions) == -1) {
          reader.skipName();
          reader.skipValue();
          continue;
        }

        int labelIndex = reader.selectString(labelOptions);
        if (labelIndex == -1) {
          throw new JsonDataException("Expected one of "
              + labels
              + " for key '"
              + labelKey
              + "' but found '"
              + reader.nextString()
              + "'. Register a subtype for this label.");
        }
        reader.close();
        return labelIndex;
      }

      throw new JsonDataException("Missing label for " + labelKey);
    }

    @Override public void toJson(JsonWriter writer, Object value) throws IOException {
      Class<?> type = value.getClass();
      int labelIndex = subtypes.indexOf(type);
      if (labelIndex == -1) {
        throw new IllegalArgumentException("Expected one of "
            + subtypes
            + " but found "
            + value
            + ", a "
            + value.getClass()
            + ". Register this subtype.");
      }
      JsonAdapter<Object> adapter = jsonAdapters.get(labelIndex);
      writer.beginObject();
      writer.name(labelKey).value(labels.get(labelIndex));
      int flattenToken = writer.beginFlatten();
      adapter.toJson(writer, value);
      writer.endFlatten(flattenToken);
      writer.endObject();
    }

    @Override public String toString() {
      return "RuntimeJsonAdapter(" + labelKey + ")";
    }
  }
}
