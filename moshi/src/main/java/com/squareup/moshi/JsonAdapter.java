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

import com.squareup.moshi.internal.NonNullJsonAdapter;
import com.squareup.moshi.internal.NullSafeJsonAdapter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * Converts Java values to JSON, and JSON values to Java.
 */
public abstract class JsonAdapter<T> {
  @CheckReturnValue public abstract @Nullable T fromJson(JsonReader reader) throws IOException;

  @CheckReturnValue public final @Nullable T fromJson(BufferedSource source) throws IOException {
    return fromJson(JsonReader.of(source));
  }

  @CheckReturnValue public final @Nullable T fromJson(String string) throws IOException {
    JsonReader reader = JsonReader.of(new Buffer().writeUtf8(string));
    T result = fromJson(reader);
    if (!isLenient() && reader.peek() != JsonReader.Token.END_DOCUMENT) {
      throw new JsonDataException("JSON document was not fully consumed.");
    }
    return result;
  }

  public abstract void toJson(JsonWriter writer, @Nullable T value) throws IOException;

  public final void toJson(BufferedSink sink, @Nullable T value) throws IOException {
    JsonWriter writer = JsonWriter.of(sink);
    toJson(writer, value);
  }

  @CheckReturnValue public final String toJson(@Nullable T value) {
    Buffer buffer = new Buffer();
    try {
      toJson(buffer, value);
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
    return buffer.readUtf8();
  }

  /**
   * Encodes {@code value} as a Java value object comprised of maps, lists, strings, numbers,
   * booleans, and nulls.
   *
   * <p>Values encoded using {@code value(double)} or {@code value(long)} are modeled with the
   * corresponding boxed type. Values encoded using {@code value(Number)} are modeled as a
   * {@link Long} for boxed integer types ({@link Byte}, {@link Short}, {@link Integer}, and {@link
   * Long}), as a {@link Double} for boxed floating point types ({@link Float} and {@link Double}),
   * and as a {@link BigDecimal} for all other types.
   */
  @CheckReturnValue public final @Nullable Object toJsonValue(@Nullable T value) {
    JsonValueWriter writer = new JsonValueWriter();
    try {
      toJson(writer, value);
      return writer.root();
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to an object.
    }
  }

  /**
   * Decodes a Java value object from {@code value}, which must be comprised of maps, lists,
   * strings, numbers, booleans and nulls.
   */
  @CheckReturnValue public final @Nullable T fromJsonValue(@Nullable Object value) {
    JsonValueReader reader = new JsonValueReader(value);
    try {
      return fromJson(reader);
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O reading from an object.
    }
  }

  /**
   * Returns a JSON adapter equal to this JSON adapter, but that serializes nulls when encoding
   * JSON.
   */
  @CheckReturnValue public final JsonAdapter<T> serializeNulls() {
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public @Nullable T fromJson(JsonReader reader) throws IOException {
        return delegate.fromJson(reader);
      }
      @Override public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
        boolean serializeNulls = writer.getSerializeNulls();
        writer.setSerializeNulls(true);
        try {
          delegate.toJson(writer, value);
        } finally {
          writer.setSerializeNulls(serializeNulls);
        }
      }
      @Override boolean isLenient() {
        return delegate.isLenient();
      }
      @Override public String toString() {
        return delegate + ".serializeNulls()";
      }
    };
  }

  /**
   * Returns a JSON adapter equal to this JSON adapter, but with support for reading and writing
   * nulls.
   */
  @CheckReturnValue public final JsonAdapter<T> nullSafe() {
    if (this instanceof NullSafeJsonAdapter) {
      return this;
    }
    return new NullSafeJsonAdapter<>(this);
  }

  /**
   * Returns a JSON adapter equal to this JSON adapter, but that refuses null values. If null is
   * read or written this will throw a {@link JsonDataException}.
   *
   * <p>Note that this adapter will not usually be invoked for absent values and so those must be
   * handled elsewhere. This should only be used to fail on explicit nulls.
   */
  @CheckReturnValue public final JsonAdapter<T> nonNull() {
    if (this instanceof NonNullJsonAdapter) {
      return this;
    }
    return new NonNullJsonAdapter<>(this);
  }

  /** Returns a JSON adapter equal to this, but is lenient when reading and writing. */
  @CheckReturnValue public final JsonAdapter<T> lenient() {
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public @Nullable T fromJson(JsonReader reader) throws IOException {
        boolean lenient = reader.isLenient();
        reader.setLenient(true);
        try {
          return delegate.fromJson(reader);
        } finally {
          reader.setLenient(lenient);
        }
      }
      @Override public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
        boolean lenient = writer.isLenient();
        writer.setLenient(true);
        try {
          delegate.toJson(writer, value);
        } finally {
          writer.setLenient(lenient);
        }
      }
      @Override boolean isLenient() {
        return true;
      }
      @Override public String toString() {
        return delegate + ".lenient()";
      }
    };
  }

  /**
   * Returns a JSON adapter equal to this, but that throws a {@link JsonDataException} when
   * {@linkplain JsonReader#setFailOnUnknown(boolean) unknown names and values} are encountered.
   * This constraint applies to both the top-level message handled by this type adapter as well as
   * to nested messages.
   */
  @CheckReturnValue public final JsonAdapter<T> failOnUnknown() {
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public @Nullable T fromJson(JsonReader reader) throws IOException {
        boolean skipForbidden = reader.failOnUnknown();
        reader.setFailOnUnknown(true);
        try {
          return delegate.fromJson(reader);
        } finally {
          reader.setFailOnUnknown(skipForbidden);
        }
      }
      @Override public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
        delegate.toJson(writer, value);
      }
      @Override boolean isLenient() {
        return delegate.isLenient();
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
  @CheckReturnValue public JsonAdapter<T> indent(final String indent) {
    if (indent == null) {
      throw new NullPointerException("indent == null");
    }
    final JsonAdapter<T> delegate = this;
    return new JsonAdapter<T>() {
      @Override public @Nullable T fromJson(JsonReader reader) throws IOException {
        return delegate.fromJson(reader);
      }
      @Override public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
        String originalIndent = writer.getIndent();
        writer.setIndent(indent);
        try {
          delegate.toJson(writer, value);
        } finally {
          writer.setIndent(originalIndent);
        }
      }
      @Override boolean isLenient() {
        return delegate.isLenient();
      }
      @Override public String toString() {
        return delegate + ".indent(\"" + indent + "\")";
      }
    };
  }

  boolean isLenient() {
    return false;
  }

  public interface Factory {
    /**
     * Attempts to create an adapter for {@code type} annotated with {@code annotations}. This
     * returns the adapter if one was created, or null if this factory isn't capable of creating
     * such an adapter.
     *
     * <p>Implementations may use {@link Moshi#adapter} to compose adapters of other types, or
     * {@link Moshi#nextAdapter} to delegate to the underlying adapter of the same type.
     */
    @CheckReturnValue
    @Nullable JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi);
  }
}
