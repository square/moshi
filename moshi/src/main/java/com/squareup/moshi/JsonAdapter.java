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
import java.util.Set;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * Converts Java values to JSON, and JSON values to Java.
 */
public abstract class JsonAdapter<T> {
  public abstract T fromJson(JsonReader reader) throws IOException;

  public final T fromJson(BufferedSource source) throws IOException {
    return fromJson(JsonReader.of(source));
  }

  public final T fromJson(String string) throws IOException {
    return fromJson(new Buffer().writeUtf8(string));
  }

  public abstract void toJson(JsonWriter writer, T value) throws IOException;

  public final void toJson(BufferedSink sink, T value) throws IOException {
    JsonWriter writer = JsonWriter.of(sink);
    toJson(writer, value);
  }

  public final String toJson(T value) {
    Buffer buffer = new Buffer();
    try {
      toJson(buffer, value);
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
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
      @Override public String toString() {
        return delegate + ".nullSafe()";
      }
    };
  }

  /** Returns a JSON adapter equal to this, but is lenient when reading and writing. */
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
      @Override public String toString() {
        return delegate + ".lenient()";
      }
    };
  }

  /**
   * Returns a JSON adapter equal to this, but that throws a {@link JsonDataException} when
   * {@linkplain JsonReader#setFailOnUnknown(boolean) unknown values} are encountered. This
   * constraint applies to both the top-level message handled by this type adapter as well as to
   * nested messages.
   */
  public final JsonAdapter<T> failOnUnknown() {
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public T fromJson(JsonReader reader) throws IOException {
        boolean skipForbidden = reader.failOnUnknown();
        reader.setFailOnUnknown(true);
        try {
          return delegate.fromJson(reader);
        } finally {
          reader.setFailOnUnknown(skipForbidden);
        }
      }
      @Override public void toJson(JsonWriter writer, T value) throws IOException {
        delegate.toJson(writer, value);
      }
      @Override public String toString() {
        return delegate + ".failOnUnknown()";
      }
    };
  }

  /**
   * Return a JSON adapter equal to this, but using {@code indent} to control how the result is
   * formatted. The {@code indent} string to be repeated for each level of indentation in the
   * encoded document. If {@code indent.isEmpty()} the encoded document will be compact. Otherwise
   * the encoded document will be more human-readable.
   *
   * @param indent a string containing only whitespace.
   */
  public JsonAdapter<T> indent(final String indent) {
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public T fromJson(JsonReader reader) throws IOException {
        return delegate.fromJson(reader);
      }
      @Override public void toJson(JsonWriter writer, T value) throws IOException {
        String originalIndent = writer.getIndent();
        writer.setIndent(indent);
        try {
          delegate.toJson(writer, value);
        } finally {
          writer.setIndent(originalIndent);
        }
      }
      @Override public String toString() {
        return delegate + ".indent(\"" + indent + "\")";
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
    JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi);
  }
}
