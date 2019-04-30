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

import com.squareup.moshi.internal.Util;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

import static com.squareup.moshi.internal.Util.canonicalize;
import static com.squareup.moshi.internal.Util.removeSubtypeWildcard;
import static com.squareup.moshi.internal.Util.typeAnnotatedWithAnnotations;

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
  private final ThreadLocal<LookupChain> lookupChainThreadLocal = new ThreadLocal<>();
  private final Map<Object, JsonAdapter<?>> adapterCache = new LinkedHashMap<>();

  Moshi(Builder builder) {
    List<JsonAdapter.Factory> factories = new ArrayList<>(
        builder.factories.size() + BUILT_IN_FACTORIES.size());
    factories.addAll(builder.factories);
    factories.addAll(BUILT_IN_FACTORIES);
    this.factories = Collections.unmodifiableList(factories);
  }

  /** Returns a JSON adapter for {@code type}, creating it if necessary. */
  @CheckReturnValue public <T> JsonAdapter<T> adapter(Type type) {
    return adapter(type, Util.NO_ANNOTATIONS);
  }

  @CheckReturnValue public <T> JsonAdapter<T> adapter(Class<T> type) {
    return adapter(type, Util.NO_ANNOTATIONS);
  }

  @CheckReturnValue
  public <T> JsonAdapter<T> adapter(Type type, Class<? extends Annotation> annotationType) {
    if (annotationType == null) {
      throw new NullPointerException("annotationType == null");
    }
    return adapter(type,
        Collections.singleton(Types.createJsonQualifierImplementation(annotationType)));
  }

  @CheckReturnValue
  public <T> JsonAdapter<T> adapter(Type type, Class<? extends Annotation>... annotationTypes) {
    if (annotationTypes.length == 1) {
      return adapter(type, annotationTypes[0]);
    }
    Set<Annotation> annotations = new LinkedHashSet<>(annotationTypes.length);
    for (Class<? extends Annotation> annotationType : annotationTypes) {
      annotations.add(Types.createJsonQualifierImplementation(annotationType));
    }
    return adapter(type, Collections.unmodifiableSet(annotations));
  }

  @CheckReturnValue
  public <T> JsonAdapter<T> adapter(Type type, Set<? extends Annotation> annotations) {
    return adapter(type, annotations, null);
  }

  /**
   * @param fieldName An optional field name associated with this type. The field name is used as a
   * hint for better adapter lookup error messages for nested structures.
   */
  @CheckReturnValue
  @SuppressWarnings("unchecked") // Factories are required to return only matching JsonAdapters.
  public <T> JsonAdapter<T> adapter(Type type, Set<? extends Annotation> annotations,
      @Nullable String fieldName) {
    if (type == null) {
      throw new NullPointerException("type == null");
    }
    if (annotations == null) {
      throw new NullPointerException("annotations == null");
    }

    type = removeSubtypeWildcard(canonicalize(type));

    // If there's an equivalent adapter in the cache, we're done!
    Object cacheKey = cacheKey(type, annotations);
    synchronized (adapterCache) {
      JsonAdapter<?> result = adapterCache.get(cacheKey);
      if (result != null) return (JsonAdapter<T>) result;
    }

    LookupChain lookupChain = lookupChainThreadLocal.get();
    if (lookupChain == null) {
      lookupChain = new LookupChain();
      lookupChainThreadLocal.set(lookupChain);
    }

    boolean success = false;
    JsonAdapter<T> adapterFromCall = lookupChain.push(type, fieldName, cacheKey);
    try {
      if (adapterFromCall != null) return adapterFromCall;

      // Ask each factory to create the JSON adapter.
      for (int i = 0, size = factories.size(); i < size; i++) {
        JsonAdapter<T> result = (JsonAdapter<T>) factories.get(i).create(type, annotations, this);
        if (result == null) continue;

        // Success! Notify the LookupChain so it is cached and can be used by re-entrant calls.
        lookupChain.adapterFound(result);
        success = true;
        return result;
      }

      throw new IllegalArgumentException(
          "No JsonAdapter for " + typeAnnotatedWithAnnotations(type, annotations));
    } catch (IllegalArgumentException e) {
      throw lookupChain.exceptionWithLookupStack(e);
    } finally {
      lookupChain.pop(success);
    }
  }

  @CheckReturnValue
  @SuppressWarnings("unchecked") // Factories are required to return only matching JsonAdapters.
  public <T> JsonAdapter<T> nextAdapter(JsonAdapter.Factory skipPast, Type type,
      Set<? extends Annotation> annotations) {
    if (annotations == null) throw new NullPointerException("annotations == null");

    type = removeSubtypeWildcard(canonicalize(type));

    int skipPastIndex = factories.indexOf(skipPast);
    if (skipPastIndex == -1) {
      throw new IllegalArgumentException("Unable to skip past unknown factory " + skipPast);
    }
    for (int i = skipPastIndex + 1, size = factories.size(); i < size; i++) {
      JsonAdapter<T> result = (JsonAdapter<T>) factories.get(i).create(type, annotations, this);
      if (result != null) return result;
    }
    throw new IllegalArgumentException("No next JsonAdapter for "
        + typeAnnotatedWithAnnotations(type, annotations));
  }

  /** Returns a new builder containing all custom factories used by the current instance. */
  @CheckReturnValue public Moshi.Builder newBuilder() {
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

    @CheckReturnValue public Moshi build() {
      return new Moshi(this);
    }
  }

  /**
   * A possibly-reentrant chain of lookups for JSON adapters.
   *
   * <p>We keep track of the current stack of lookups: we may start by looking up the JSON adapter
   * for Employee, re-enter looking for the JSON adapter of HomeAddress, and re-enter again looking
   * up the JSON adapter of PostalCode. If any of these lookups fail we can provide a stack trace
   * with all of the lookups.
   *
   * <p>Sometimes a JSON adapter factory depends on its own product; either directly or indirectly.
   * To make this work, we offer a JSON adapter stub while the final adapter is being computed.
   * When it is ready, we wire the stub to that finished adapter. This is necessary in
   * self-referential object models, such as an {@code Employee} class that has a {@code
   * List<Employee>} field for an organization's management hierarchy.
   *
   * <p>This class defers putting any JSON adapters in the cache until the topmost JSON adapter has
   * successfully been computed. That way we don't pollute the cache with incomplete stubs, or
   * adapters that may transitively depend on incomplete stubs.
   */
  final class LookupChain {
    final List<Lookup<?>> callLookups = new ArrayList<>();
    final Deque<Lookup<?>> stack = new ArrayDeque<>();
    boolean exceptionAnnotated;

    /**
     * Returns a JSON adapter that was already created for this call, or null if this is the first
     * time in this call that the cache key has been requested in this call. This may return a
     * lookup that isn't yet ready if this lookup is reentrant.
     */
    <T> JsonAdapter<T> push(Type type, @Nullable String fieldName, Object cacheKey) {
      // Try to find a lookup with the same key for the same call.
      for (int i = 0, size = callLookups.size(); i < size; i++) {
        Lookup<?> lookup = callLookups.get(i);
        if (lookup.cacheKey.equals(cacheKey)) {
          Lookup<T> hit = (Lookup<T>) lookup;
          stack.add(hit);
          return hit.adapter != null ? hit.adapter : hit;
        }
      }

      // We might need to know about this cache key later in this call. Prepare for that.
      Lookup<Object> lookup = new Lookup<>(type, fieldName, cacheKey);
      callLookups.add(lookup);
      stack.add(lookup);
      return null;
    }

    /** Sets the adapter result of the current lookup. */
    <T> void adapterFound(JsonAdapter<T> result) {
      Lookup<T> currentLookup = (Lookup<T>) stack.getLast();
      currentLookup.adapter = result;
    }

    /**
     * Completes the current lookup by removing a stack frame.
     *
     * @param success true if the adapter cache should be populated if this is the topmost lookup.
     */
    void pop(boolean success) {
      stack.removeLast();
      if (!stack.isEmpty()) return;

      lookupChainThreadLocal.remove();

      if (success) {
        synchronized (adapterCache) {
          for (int i = 0, size = callLookups.size(); i < size; i++) {
            Lookup<?> lookup = callLookups.get(i);
            JsonAdapter<?> replaced = adapterCache.put(lookup.cacheKey, lookup.adapter);
            if (replaced != null) {
              ((Lookup<Object>) lookup).adapter = (JsonAdapter<Object>) replaced;
              adapterCache.put(lookup.cacheKey, replaced);
            }
          }
        }
      }
    }

    IllegalArgumentException exceptionWithLookupStack(IllegalArgumentException e) {
      // Don't add the lookup stack to more than one exception; the deepest is sufficient.
      if (exceptionAnnotated) return e;
      exceptionAnnotated = true;

      int size = stack.size();
      if (size == 1 && stack.getFirst().fieldName == null) return e;

      StringBuilder errorMessageBuilder = new StringBuilder(e.getMessage());
      for (Iterator<Lookup<?>> i = stack.descendingIterator(); i.hasNext(); ) {
        Lookup<?> lookup = i.next();
        errorMessageBuilder
            .append("\nfor ")
            .append(lookup.type);
        if (lookup.fieldName != null) {
          errorMessageBuilder
              .append(' ')
              .append(lookup.fieldName);
        }
      }

      return new IllegalArgumentException(errorMessageBuilder.toString(), e);
    }
  }

  /** This class implements {@code JsonAdapter} so it can be used as a stub for re-entrant calls. */
  static final class Lookup<T> extends JsonAdapter<T> {
    final Type type;
    final @Nullable String fieldName;
    final Object cacheKey;
    @Nullable JsonAdapter<T> adapter;

    Lookup(Type type, @Nullable String fieldName, Object cacheKey) {
      this.type = type;
      this.fieldName = fieldName;
      this.cacheKey = cacheKey;
    }

    @Override public T fromJson(JsonReader reader) throws IOException {
      if (adapter == null) throw new IllegalStateException("JsonAdapter isn't ready");
      return adapter.fromJson(reader);
    }

    @Override public void toJson(JsonWriter writer, T value) throws IOException {
      if (adapter == null) throw new IllegalStateException("JsonAdapter isn't ready");
      adapter.toJson(writer, value);
    }

    @Override public String toString() {
      return adapter != null ? adapter.toString() : super.toString();
    }
  }
}
