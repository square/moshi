/*
 * Copyright (C) 2021 Square, Inc.
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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A {@link JsonAdapter} that serializes and deserializes {@link Optional}s.
 *
 * <p>This works by coercing absent keys and null values to {@link Optional#empty()}.
 */
public final class OptionalJsonAdapter<T> extends JsonAdapter<Optional<T>> {

  public static final Factory FACTORY =
      new JsonAdapter.Factory() {
        @Nullable
        @Override
        public JsonAdapter<?> create(
            Type type, Set<? extends Annotation> annotations, Moshi moshi) {
          if (type instanceof ParameterizedType && Types.getRawType(type) == Optional.class) {
            return new OptionalJsonAdapter<>(
                moshi.adapter(((ParameterizedType) type).getActualTypeArguments()[0]));
          }
          return null;
        }

        @Override
        public String toString() {
          return "OptionalJsonAdapterFactory";
        }
      };

  private final JsonAdapter<T> delegate;

  private OptionalJsonAdapter(JsonAdapter<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public Optional<T> fromJson(JsonReader reader) throws IOException {
    if (reader.peek() == JsonReader.Token.NULL) {
      reader.nextNull();
      return Optional.empty();
    }
    T value = delegate.fromJson(reader);
    return Optional.ofNullable(value);
  }

  @Override
  public void toJson(JsonWriter writer, @Nullable Optional<T> value) throws IOException {
    if (value != null && value.isPresent()) {
      delegate.toJson(writer, value.get());
    } else {
      writer.nullValue();
    }
  }

  @Override
  public boolean handlesAbsence() {
    return true;
  }

  @Override
  public Optional<T> onAbsence(String key) {
    return Optional.empty();
  }
}
