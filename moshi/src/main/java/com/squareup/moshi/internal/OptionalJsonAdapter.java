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
package com.squareup.moshi.internal;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A {@link JsonAdapter} that serializes and deserializes {@link Optional}s.
 *
 * <p>This is one of two sides to supporting {@link Optional}s in Moshi and focuses solely on
 * handling explicit null values. The other side is reflective support for setting absent values.
 */
public final class OptionalJsonAdapter<T> extends JsonAdapter<Optional<T>> {

  private final JsonAdapter<T> delegate;

  public OptionalJsonAdapter(JsonAdapter<T> delegate) {
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
}
