/*
 * Copyright (C) 2017 Square, Inc.
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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

public final class DefaultOnDataMismatchAdapter<T> extends JsonAdapter<T> {
  private final JsonAdapter<T> delegate;
  private final T defaultValue;

  private DefaultOnDataMismatchAdapter(JsonAdapter<T> delegate, T defaultValue) {
    this.delegate = delegate;
    this.defaultValue = defaultValue;
  }

  @Override public T fromJson(JsonReader reader) throws IOException {
    // Read the value first so that the reader will be in a known state even if there's an
    // exception. Otherwise it may be awkward to recover: it might be between calls to
    // beginObject() and endObject() for example.
    Object jsonValue = reader.readJsonValue();

    // Use the delegate to convert the JSON value to the target type.
    try {
      return delegate.fromJsonValue(jsonValue);
    } catch (JsonDataException e) {
      return defaultValue;
    }
  }

  @Override public void toJson(JsonWriter writer, T value) throws IOException {
    delegate.toJson(writer, value);
  }

  public static <T> Factory newFactory(final Class<T> type, final T defaultValue) {
    return new Factory() {
      @Override public JsonAdapter<?> create(
          Type requestedType, Set<? extends Annotation> annotations, Moshi moshi) {
        if (type != requestedType) return null;
        JsonAdapter<T> delegate = moshi.nextAdapter(this, type, annotations);
        return new DefaultOnDataMismatchAdapter<>(delegate, defaultValue);
      }
    };
  }
}
