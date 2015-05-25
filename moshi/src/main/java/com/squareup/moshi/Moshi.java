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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinates binding between JSON values and Java objects.
 */
public final class Moshi {
  private final List<JsonAdapter.Factory> factories;
  private final ThreadLocal<List<DeferredAdapter<?>>> reentrantCalls = new ThreadLocal<>();

  private Moshi(Builder builder) {
    List<JsonAdapter.Factory> factories = new ArrayList<>();
    factories.addAll(builder.factories);
    factories.add(StandardJsonAdapters.FACTORY);
    factories.add(CollectionJsonAdapter.FACTORY);
    factories.add(MapJsonAdapter.FACTORY);
    factories.add(ArrayJsonAdapter.FACTORY);
    factories.add(ClassJsonAdapter.FACTORY);
    this.factories = Collections.unmodifiableList(factories);
  }

  public <T> JsonAdapter<T> adapter(Type type) {
    return adapter(type, Util.NO_ANNOTATIONS);
  }

  /** Returns a JSON adapter for {@code type}, creating it if necessary. */
  public <T> JsonAdapter<T> adapter(Class<T> type) {
    // TODO: cache created JSON adapters.
    return adapter(type, Util.NO_ANNOTATIONS);
  }

  public <T> JsonAdapter<T> adapter(Type type, AnnotatedElement annotations) {
    // TODO: support re-entrant calls.
    return createAdapter(0, type, annotations);
  }

  public <T> JsonAdapter<T> nextAdapter(JsonAdapter.Factory skipPast, Type type,
      AnnotatedElement annotations) {
    return createAdapter(factories.indexOf(skipPast) + 1, type, annotations);
  }

  @SuppressWarnings("unchecked") // Factories are required to return only matching JsonAdapters.
  private <T> JsonAdapter<T> createAdapter(
      int firstIndex, Type type, AnnotatedElement annotations) {
    List<DeferredAdapter<?>> deferredAdapters = reentrantCalls.get();
    if (deferredAdapters == null) {
      deferredAdapters = new ArrayList<>();
      reentrantCalls.set(deferredAdapters);
    } else if (firstIndex == 0) {
      // If this is a regular adapter lookup, check that this isn't a reentrant call.
      for (DeferredAdapter<?> deferredAdapter : deferredAdapters) {
        if (deferredAdapter.type.equals(type) && deferredAdapter.annotations.equals(annotations)) {
          return (JsonAdapter<T>) deferredAdapter;
        }
      }
    }

    DeferredAdapter<T> deferredAdapter = new DeferredAdapter<>(type, annotations);
    deferredAdapters.add(deferredAdapter);
    try {
      for (int i = firstIndex, size = factories.size(); i < size; i++) {
        JsonAdapter<T> result = (JsonAdapter<T>) factories.get(i).create(type, annotations, this);
        if (result != null) {
          deferredAdapter.ready(result);
          return result;
        }
      }
    } finally {
      deferredAdapters.remove(deferredAdapters.size() - 1);
    }

    throw new IllegalArgumentException("no JsonAdapter for " + type);
  }

  public static final class Builder {
    private final List<JsonAdapter.Factory> factories = new ArrayList<>();

    public <T> Builder add(final Type type, final JsonAdapter<T> jsonAdapter) {
      if (type == null) throw new IllegalArgumentException("type == null");
      if (jsonAdapter == null) throw new IllegalArgumentException("jsonAdapter == null");

      return add(new JsonAdapter.Factory() {
        @Override public JsonAdapter<?> create(
            Type targetType, AnnotatedElement annotations, Moshi moshi) {
          return Util.typesMatch(type, targetType) ? jsonAdapter : null;
        }
      });
    }

    public <T> Builder add(final Type type, final Class<? extends Annotation> annotation,
        final JsonAdapter<T> jsonAdapter) {
      if (type == null) throw new IllegalArgumentException("type == null");
      if (annotation == null) throw new IllegalArgumentException("annotation == null");
      if (jsonAdapter == null) throw new IllegalArgumentException("jsonAdapter == null");

      return add(new JsonAdapter.Factory() {
        @Override public JsonAdapter<?> create(
            Type targetType, AnnotatedElement annotations, Moshi moshi) {
          return Util.typesMatch(type, targetType) && annotations.isAnnotationPresent(annotation)
              ? jsonAdapter
              : null;
        }
      });
    }

    public Builder add(JsonAdapter.Factory jsonAdapter) {
      // TODO: define precedence order. Last added wins? First added wins?
      factories.add(jsonAdapter);
      return this;
    }

    public Moshi build() {
      return new Moshi(this);
    }
  }

  /**
   * Sometimes a type adapter factory depends on its own product; either directly or indirectly.
   * To make this work, we offer this type adapter stub while the final adapter is being computed.
   * When it is ready, we wire this to delegate to that finished adapter.
   *
   * <p>Typically this is necessary in self-referential object models, such as an {@code Employee}
   * class that has a {@code List<Employee>} field for an organization's management hierarchy.
   */
  private static class DeferredAdapter<T> extends JsonAdapter<T> {
    private Type type;
    private AnnotatedElement annotations;
    private JsonAdapter<T> delegate;

    public DeferredAdapter(Type type, AnnotatedElement annotations) {
      this.type = type;
      this.annotations = annotations;
    }

    public void ready(JsonAdapter<T> delegate) {
      this.delegate = delegate;

      // Null out the type and annotations so they can be garbage collected.
      this.type = null;
      this.annotations = null;
    }

    @Override public T fromJson(JsonReader reader) throws IOException {
      if (delegate == null) throw new IllegalStateException("type adapter isn't ready");
      return delegate.fromJson(reader);
    }

    @Override public void toJson(JsonWriter writer, T value) throws IOException {
      if (delegate == null) throw new IllegalStateException("type adapter isn't ready");
      delegate.toJson(writer, value);
    }
  }
}
