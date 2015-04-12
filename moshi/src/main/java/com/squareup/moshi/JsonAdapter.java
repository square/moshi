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
import okio.Buffer;
import okio.BufferedSource;
import okio.Sink;

/**
 * Converts Java values to JSON, and JSON values to Java.
 */
public abstract class JsonAdapter<T> {
  public abstract T fromJson(JsonReader reader) throws IOException;

  public final T fromJson(BufferedSource source) throws IOException {
    return fromJson(new JsonReader(source));
  }

  public final T fromJson(String string) throws IOException {
    return fromJson(new Buffer().writeUtf8(string));
  }

  public abstract void toJson(JsonWriter writer, T value) throws IOException;

  public final void toJson(Sink sink, T value) throws IOException {
    JsonWriter writer = new JsonWriter(sink);
    toJson(writer, value);
    writer.flush();
  }

  public final String toJson(T value) throws IOException {
    Buffer buffer = new Buffer();
    toJson(buffer, value);
    return buffer.readUtf8();
  }

  /**
   * Returns a JSON adapter equal to this JSON adapter, but with support for reading and writing
   * nulls.
   */
  public final JsonAdapter<T> nullSafe() {
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public T fromJson(JsonReader reader) throws IOException {
        if (reader.peek() == JsonReader.Token.NULL) {
          return reader.nextNull();
        } else {
          return delegate.fromJson(reader);
        }
      }
      @Override public void toJson(JsonWriter writer, T value) throws IOException {
        if (value == null) {
          writer.nullValue();
        } else {
          delegate.toJson(writer, value);
        }
      }
    };
  }

  /** Returns a JSON adapter equal to this JSON adapter, but is lenient when reading and writing. */
  public final JsonAdapter<T> lenient() {
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public T fromJson(JsonReader reader) throws IOException {
        boolean lenient = reader.isLenient();
        reader.setLenient(true);
        try {
          return delegate.fromJson(reader);
        } finally {
          reader.setLenient(lenient);
        }
      }
      @Override public void toJson(JsonWriter writer, T value) throws IOException {
        boolean lenient = writer.isLenient();
        writer.setLenient(true);
        try {
          delegate.toJson(writer, value);
        } finally {
          writer.setLenient(lenient);
        }
      }
    };
  }

  public interface Factory {
    /**
     * Attempts to create an adapter for {@code type} annotated with {@code annotations}. This
     * returns the adapter if one was created, or null if this factory isn't capable of creating
     * such an adapter.
     *
     * <p>Implementations may use to {@link Moshi#adapter} to compose adapters of other types, or
     * {@link Moshi#nextAdapter} to delegate to the underlying adapter of the same type.
     */
    JsonAdapter<?> create(Type type, AnnotatedElement annotations, Moshi moshi);
  }
}
