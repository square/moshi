package com.squareup.moshi;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

public class OptionalTypeAdapter<T> extends JsonAdapter<T> {
  public static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {
    @Override
    public @Nullable
    JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (!(type instanceof OptionalType)) {
        return null;
      }
      OptionalType optionalType = (OptionalType) type;
      Type rawType = optionalType.getRawType();
      JsonAdapter<?> delegate = moshi.adapter(rawType);
      return new OptionalTypeAdapter<>(((OptionalType) type).isOptional(), delegate);
    }
  };

  private final JsonAdapter<T> delegate;

  public OptionalTypeAdapter(boolean isOptional, JsonAdapter<T> delegate) {
    if (isOptional) {
      this.delegate = delegate.nullSafe();
    } else {
      this.delegate = delegate.nonNull();
    }
  }

  @Nullable
  @Override
  public T fromJson(JsonReader reader) throws IOException {
    return delegate.fromJson(reader);
  }

  @Override
  public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
    delegate.toJson(writer, value);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
