package com.squareup.moshi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

// This is just a simple shim for linking in StandardJsonAdapters and excluded from the final jar.
final class RecordJsonAdapter<T> extends JsonAdapter<T> {

  static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {

    @Nullable @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      return null;
    }
  };

  @Nullable @Override public T fromJson(JsonReader reader) throws IOException {
    throw new AssertionError();
  }

  @Override public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
    throw new AssertionError();
  }
}
