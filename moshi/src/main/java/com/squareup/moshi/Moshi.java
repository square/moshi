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

  private Moshi(Builder builder) {
    List<JsonAdapter.Factory> factories = new ArrayList<JsonAdapter.Factory>();
    factories.addAll(builder.factories);
    factories.add(new StandardJsonAdapterFactory());
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
    for (int i = firstIndex, size = factories.size(); i < size; i++) {
      JsonAdapter<?> result = factories.get(i).create(type, annotations, this);
      if (result != null) return (JsonAdapter<T>) result;
    }
    throw new IllegalArgumentException("no JsonAdapter for " + type);
  }

  public static final class Builder {
    private final List<JsonAdapter.Factory> factories = new ArrayList<JsonAdapter.Factory>();

    public <T> Builder add(final Type type, final JsonAdapter<T> jsonAdapter) {
      return add(new JsonAdapter.Factory() {
        @Override public JsonAdapter<?> create(
            Type targetType, AnnotatedElement annotations, Moshi moshi) {
          return Util.typesMatch(type, targetType) ? jsonAdapter : null;
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
}
