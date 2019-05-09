package com.squareup.moshi.internal;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import javax.annotation.Nullable;

public final class NullSafeJsonAdapter<T> extends JsonAdapter<T> {

  private final JsonAdapter<T> delegate;

  public NullSafeJsonAdapter(JsonAdapter<T> delegate) {
    this.delegate = delegate;
  }

  public JsonAdapter<T> delegate() {
    return delegate;
  }

  @Override public @Nullable T fromJson(JsonReader reader) throws IOException {
    if (reader.peek() == JsonReader.Token.NULL) {
      return reader.nextNull();
    } else {
      return delegate.fromJson(reader);
    }
  }

  @Override public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
    if (value == null) {
      writer.nullValue();
    } else {
      delegate.toJson(writer, value);
    }
  }

  @Override public String toString() {
    return delegate + ".nullSafe()";
  }
}
