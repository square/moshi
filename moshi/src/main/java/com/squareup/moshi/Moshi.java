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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Coordinates binding between JSON values and Java objects.
 */
public final class Moshi {
  static final List<JsonAdapter.Factory> BUILT_IN_FACTORIES = new ArrayList<>(5);

  static {
    BUILT_IN_FACTORIES.add(StandardJsonAdapters.FACTORY);
    BUILT_IN_FACTORIES.add(CollectionJsonAdapter.FACTORY);
    BUILT_IN_FACTORIES.add(MapJsonAdapter.FACTORY);
    BUILT_IN_FACTORIES.add(ArrayJsonAdapter.FACTORY);
    BUILT_IN_FACTORIES.add(ClassJsonAdapter.FACTORY);
  }

  private final List<JsonAdapter.Factory> factories;
  private final ThreadLocal<List<DeferredAdapter<?>>> reentrantCalls = new ThreadLocal<>();
  private final Map<Object, JsonAdapter<?>> adapterCache = new LinkedHashMap<>();

  Moshi(Builder builder) {
    List<JsonAdapter.Factory> factories = new ArrayList<>(
        builder.factories.size() + BUILT_IN_FACTORIES.size());
    factories.addAll(builder.factories);
    factories.addAll(BUILT_IN_FACTORIES);
    this.factories = Collections.unmodifiableList(factories);
  }

  /** Returns a JSON adapter for {@code type}, creating it if necessary. */
  public <T> JsonAdapter<T> adapter(Type type) {
    return adapter(type, Util.NO_ANNOTATIONS);
  }

  public <T> JsonAdapter<T> adapter(Class<T> type) {
    return adapter(type, Util.NO_ANNOTATIONS);
  }

  public <T> JsonAdapter<T> adapter(Type type, Class<? extends Annotation> annotationType) {
    return adapter(type,
        Collections.singleton(Types.createJsonQualifierImplementation(annotationType)));
  }

  @SuppressWarnings("unchecked") // Factories are required to return only matching JsonAdapters.
  public <T> JsonAdapter<T> adapter(Type type, Set<? extends Annotation> annotations) {
    type = Types.canonicalize(type);

    // If there's an equivalent adapter in the cache, we're done!
    Object cacheKey = cacheKey(type, annotations);
    synchronized (adapterCache) {
      JsonAdapter<?> result = adapterCache.get(cacheKey);
      if (result != null) return (JsonAdapter<T>) result;
    }

    // Short-circuit if this is a reentrant call.
    List<DeferredAdapter<?>> deferredAdapters = reentrantCalls.get();
    if (deferredAdapters != null) {
      for (int i = 0, size = deferredAdapters.size(); i < size; i++) {
        DeferredAdapter<?> deferredAdapter = deferredAdapters.get(i);
        if (deferredAdapter.cacheKey.equals(cacheKey)) {
          return (JsonAdapter<T>) deferredAdapter;
        }
      }
    } else {
      deferredAdapters = new ArrayList<>();
      reentrantCalls.set(deferredAdapters);
    }

    // Prepare for re-entrant calls, then ask each factory to create a type adapter.
    DeferredAdapter<T> deferredAdapter = new DeferredAdapter<>(cacheKey);
    deferredAdapters.add(deferredAdapter);
    try {
      for (int i = 0, size = factories.size(); i < size; i++) {
        JsonAdapter<T> result = (JsonAdapter<T>) factories.get(i).create(type, annotations, this);
        if (result != null) {
          deferredAdapter.ready(result);
          synchronized (adapterCache) {
            adapterCache.put(cacheKey, result);
          }
          return result;
        }
      }
    } finally {
      deferredAdapters.remove(deferredAdapters.size() - 1);
      if (deferredAdapters.isEmpty()) {
        reentrantCalls.remove();
      }
    }

    throw new IllegalArgumentException("No JsonAdapter for " + type + " annotated " + annotations);
  }

  @SuppressWarnings("unchecked") // Factories are required to return only matching JsonAdapters.
  public <T> JsonAdapter<T> nextAdapter(JsonAdapter.Factory skipPast, Type type,
      Set<? extends Annotation> annotations) {
    type = Types.canonicalize(type);

    int skipPastIndex = factories.indexOf(skipPast);
    if (skipPastIndex == -1) {
      throw new IllegalArgumentException("Unable to skip past unknown factory " + skipPast);
    }
    for (int i = skipPastIndex + 1, size = factories.size(); i < size; i++) {
      JsonAdapter<T> result = (JsonAdapter<T>) factories.get(i).create(type, annotations, this);
      if (result != null) return result;
    }
    throw new IllegalArgumentException("No next JsonAdapter for "
        + type + " annotated " + annotations);
  }

  /** Returns a new builder containing all custom factories used by the current instance. */
  public Moshi.Builder newBuilder() {
    int fullSize = factories.size();
    int tailSize = BUILT_IN_FACTORIES.size();
    List<JsonAdapter.Factory> customFactories = factories.subList(0, fullSize - tailSize);
    return new Builder().addAll(customFactories);
  }

  /** Returns an opaque object that's equal if the type and annotations are equal. */
  private Object cacheKey(Type type, Set<? extends Annotation> annotations) {
    if (annotations.isEmpty()) return type;
    return Arrays.asList(type, annotations);
  }

  public static final class Builder {
    final List<JsonAdapter.Factory> factories = new ArrayList<>();

    public <T> Builder add(final Type type, final JsonAdapter<T> jsonAdapter) {
      if (type == null) throw new IllegalArgumentException("type == null");
      if (jsonAdapter == null) throw new IllegalArgumentException("jsonAdapter == null");

      return add(new JsonAdapter.Factory() {
        @Override public @Nullable JsonAdapter<?> create(
            Type targetType, Set<? extends Annotation> annotations, Moshi moshi) {
          return annotations.isEmpty() && Util.typesMatch(type, targetType) ? jsonAdapter : null;
        }
      });
    }

    public <T> Builder add(final Type type, final Class<? extends Annotation> annotation,
        final JsonAdapter<T> jsonAdapter) {
      if (type == null) throw new IllegalArgumentException("type == null");
      if (annotation == null) throw new IllegalArgumentException("annotation == null");
      if (jsonAdapter == null) throw new IllegalArgumentException("jsonAdapter == null");
      if (!annotation.isAnnotationPresent(JsonQualifier.class)) {
        throw new IllegalArgumentException(annotation + " does not have @JsonQualifier");
      }
      if (annotation.getDeclaredMethods().length > 0) {
        throw new IllegalArgumentException("Use JsonAdapter.Factory for annotations with elements");
      }

      return add(new JsonAdapter.Factory() {
        @Override public @Nullable JsonAdapter<?> create(
            Type targetType, Set<? extends Annotation> annotations, Moshi moshi) {
          if (Util.typesMatch(type, targetType)
              && annotations.size() == 1
              && Util.isAnnotationPresent(annotations, annotation)) {
            return jsonAdapter;
          }
          return null;
        }
      });
    }

    public Builder add(JsonAdapter.Factory factory) {
      if (factory == null) throw new IllegalArgumentException("factory == null");
      factories.add(factory);
      return this;
    }

    public Builder add(Object adapter) {
      if (adapter == null) throw new IllegalArgumentException("adapter == null");
      return add(AdapterMethodsFactory.get(adapter));
    }

    Builder addAll(List<JsonAdapter.Factory> factories) {
      this.factories.addAll(factories);
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
    @Nullable Object cacheKey;
    private @Nullable JsonAdapter<T> delegate;

    DeferredAdapter(Object cacheKey) {
      this.cacheKey = cacheKey;
    }

    void ready(JsonAdapter<T> delegate) {
      this.delegate = delegate;
      this.cacheKey = null;
    }

    @Override public T fromJson(JsonReader reader) throws IOException {
      if (delegate == null) throw new IllegalStateException("Type adapter isn't ready");
      return delegate.fromJson(reader);
    }

    @Override public void toJson(JsonWriter writer, T value) throws IOException {
      if (delegate == null) throw new IllegalStateException("Type adapter isn't ready");
      delegate.toJson(writer, value);
    }
  }
}
