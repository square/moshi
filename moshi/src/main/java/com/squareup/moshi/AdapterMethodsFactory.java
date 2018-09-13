/*
 * Copyright (C) 2015 Square, Inc.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import static com.squareup.moshi.internal.Util.canonicalize;
import static com.squareup.moshi.internal.Util.jsonAnnotations;
import static com.squareup.moshi.internal.Util.typeAnnotatedWithAnnotations;

final class AdapterMethodsFactory implements JsonAdapter.Factory {
  private final List<AdapterMethod> toAdapters;
  private final List<AdapterMethod> fromAdapters;

  AdapterMethodsFactory(List<AdapterMethod> toAdapters, List<AdapterMethod> fromAdapters) {
    this.toAdapters = toAdapters;
    this.fromAdapters = fromAdapters;
  }

  @Override public @Nullable JsonAdapter<?> create(
      final Type type, final Set<? extends Annotation> annotations, final Moshi moshi) {
    final AdapterMethod toAdapter = get(toAdapters, type, annotations);
    final AdapterMethod fromAdapter = get(fromAdapters, type, annotations);
    if (toAdapter == null && fromAdapter == null) return null;

    final JsonAdapter<Object> delegate;
    if (toAdapter == null || fromAdapter == null) {
      try {
        delegate = moshi.nextAdapter(this, type, annotations);
      } catch (IllegalArgumentException e) {
        String missingAnnotation = toAdapter == null ? "@ToJson" : "@FromJson";
        throw new IllegalArgumentException("No " + missingAnnotation + " adapter for "
            + typeAnnotatedWithAnnotations(type, annotations), e);
      }
    } else {
      delegate = null;
    }

    if (toAdapter != null) toAdapter.bind(moshi, this);
    if (fromAdapter != null) fromAdapter.bind(moshi, this);

    return new JsonAdapter<Object>() {
      @Override public void toJson(JsonWriter writer, @Nullable Object value) throws IOException {
        if (toAdapter == null) {
          delegate.toJson(writer, value);
        } else if (!toAdapter.nullable && value == null) {
          writer.nullValue();
        } else {
          try {
            toAdapter.toJson(moshi, writer, value);
          } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new JsonDataException(cause + " at " + writer.getPath(), cause);
          }
        }
      }

      @Override public @Nullable Object fromJson(JsonReader reader) throws IOException {
        if (fromAdapter == null) {
          return delegate.fromJson(reader);
        } else if (!fromAdapter.nullable && reader.peek() == JsonReader.Token.NULL) {
          reader.nextNull();
          return null;
        } else {
          try {
            return fromAdapter.fromJson(moshi, reader);
          } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new JsonDataException(cause + " at " + reader.getPath(), cause);
          }
        }
      }

      @Override public String toString() {
        return "JsonAdapter" + annotations + "(" + type + ")";
      }
    };
  }

  public static AdapterMethodsFactory get(Object adapter) {
    List<AdapterMethod> toAdapters = new ArrayList<>();
    List<AdapterMethod> fromAdapters = new ArrayList<>();

    for (Class<?> c = adapter.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method m : c.getDeclaredMethods()) {
        if (m.isAnnotationPresent(ToJson.class)) {
          AdapterMethod toAdapter = toAdapter(adapter, m);
          AdapterMethod conflicting = get(toAdapters, toAdapter.type, toAdapter.annotations);
          if (conflicting != null) {
            throw new IllegalArgumentException("Conflicting @ToJson methods:\n"
                + "    " + conflicting.method + "\n"
                + "    " + toAdapter.method);
          }
          toAdapters.add(toAdapter);
        }

        if (m.isAnnotationPresent(FromJson.class)) {
          AdapterMethod fromAdapter = fromAdapter(adapter, m);
          AdapterMethod conflicting = get(fromAdapters, fromAdapter.type, fromAdapter.annotations);
          if (conflicting != null) {
            throw new IllegalArgumentException("Conflicting @FromJson methods:\n"
                + "    " + conflicting.method + "\n"
                + "    " + fromAdapter.method);
          }
          fromAdapters.add(fromAdapter);
        }
      }
    }

    if (toAdapters.isEmpty() && fromAdapters.isEmpty()) {
      throw new IllegalArgumentException("Expected at least one @ToJson or @FromJson method on "
          + adapter.getClass().getName());
    }

    return new AdapterMethodsFactory(toAdapters, fromAdapters);
  }

  /**
   * Returns an object that calls a {@code method} method on {@code adapter} in service of
   * converting an object to JSON.
   */
  static AdapterMethod toAdapter(Object adapter, Method method) {
    method.setAccessible(true);
    final Type returnType = method.getGenericReturnType();
    final Type[] parameterTypes = method.getGenericParameterTypes();
    final Annotation[][] parameterAnnotations = method.getParameterAnnotations();

    if (parameterTypes.length >= 2
        && parameterTypes[0] == JsonWriter.class
        && returnType == void.class
        && parametersAreJsonAdapters(2, parameterTypes)) {
      // void pointToJson(JsonWriter jsonWriter, Point point) {
      // void pointToJson(JsonWriter jsonWriter, Point point, JsonAdapter<?> adapter, ...) {
      Set<? extends Annotation> qualifierAnnotations = jsonAnnotations(parameterAnnotations[1]);
      return new AdapterMethod(parameterTypes[1], qualifierAnnotations, adapter, method,
          parameterTypes.length, 2, true) {
        @Override public void toJson(Moshi moshi, JsonWriter writer, @Nullable Object value)
            throws IOException, InvocationTargetException {
          invoke(writer, value);
        }
      };

    } else if (parameterTypes.length == 1 && returnType != void.class) {
      // List<Integer> pointToJson(Point point) {
      final Set<? extends Annotation> returnTypeAnnotations = jsonAnnotations(method);
      final Set<? extends Annotation> qualifierAnnotations =
          jsonAnnotations(parameterAnnotations[0]);
      boolean nullable = Util.hasNullable(parameterAnnotations[0]);
      return new AdapterMethod(parameterTypes[0], qualifierAnnotations, adapter, method,
          parameterTypes.length, 1, nullable) {
        private JsonAdapter<Object> delegate;

        @Override public void bind(Moshi moshi, JsonAdapter.Factory factory) {
          super.bind(moshi, factory);
          delegate = Types.equals(parameterTypes[0], returnType)
              && qualifierAnnotations.equals(returnTypeAnnotations)
              ? moshi.nextAdapter(factory, returnType, returnTypeAnnotations)
              : moshi.adapter(returnType, returnTypeAnnotations);
        }

        @Override public void toJson(Moshi moshi, JsonWriter writer, @Nullable Object value)
            throws IOException, InvocationTargetException {
          Object intermediate = invoke(value);
          delegate.toJson(writer, intermediate);
        }
      };

    } else {
      throw new IllegalArgumentException("Unexpected signature for " + method + ".\n"
          + "@ToJson method signatures may have one of the following structures:\n"
          + "    <any access modifier> void toJson(JsonWriter writer, T value) throws <any>;\n"
          + "    <any access modifier> void toJson(JsonWriter writer, T value,"
          + " JsonAdapter<any> delegate, <any more delegates>) throws <any>;\n"
          + "    <any access modifier> R toJson(T value) throws <any>;\n");
    }
  }

  /** Returns true if {@code parameterTypes[offset..]} contains only JsonAdapters. */
  private static boolean parametersAreJsonAdapters(int offset, Type[] parameterTypes) {
    for (int i = offset, length = parameterTypes.length; i < length; i++) {
      if (!(parameterTypes[i] instanceof ParameterizedType)) return false;
      if (((ParameterizedType) parameterTypes[i]).getRawType() != JsonAdapter.class) return false;
    }
    return true;
  }

  /**
   * Returns an object that calls a {@code method} method on {@code adapter} in service of
   * converting an object from JSON.
   */
  static AdapterMethod fromAdapter(Object adapter, Method method) {
    method.setAccessible(true);
    final Type returnType = method.getGenericReturnType();
    final Set<? extends Annotation> returnTypeAnnotations = jsonAnnotations(method);
    final Type[] parameterTypes = method.getGenericParameterTypes();
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();

    if (parameterTypes.length >= 1
        && parameterTypes[0] == JsonReader.class
        && returnType != void.class
        && parametersAreJsonAdapters(1, parameterTypes)) {
      // Point pointFromJson(JsonReader jsonReader) {
      // Point pointFromJson(JsonReader jsonReader, JsonAdapter<?> adapter, ...) {
      return new AdapterMethod(returnType, returnTypeAnnotations, adapter, method,
          parameterTypes.length, 1, true) {
        @Override public Object fromJson(Moshi moshi, JsonReader reader)
            throws IOException, InvocationTargetException {
          return invoke(reader);
        }
      };

    } else if (parameterTypes.length == 1 && returnType != void.class) {
      // Point pointFromJson(List<Integer> o) {
      final Set<? extends Annotation> qualifierAnnotations
          = jsonAnnotations(parameterAnnotations[0]);
      boolean nullable = Util.hasNullable(parameterAnnotations[0]);
      return new AdapterMethod(returnType, returnTypeAnnotations, adapter, method,
          parameterTypes.length, 1, nullable) {
        JsonAdapter<Object> delegate;

        @Override public void bind(Moshi moshi, JsonAdapter.Factory factory) {
          super.bind(moshi, factory);
          delegate = Types.equals(parameterTypes[0], returnType)
              && qualifierAnnotations.equals(returnTypeAnnotations)
              ? moshi.nextAdapter(factory, parameterTypes[0], qualifierAnnotations)
              : moshi.adapter(parameterTypes[0], qualifierAnnotations);
        }

        @Override public Object fromJson(Moshi moshi, JsonReader reader)
            throws IOException, InvocationTargetException {
          Object intermediate = delegate.fromJson(reader);
          return invoke(intermediate);
        }
      };

    } else {
      throw new IllegalArgumentException("Unexpected signature for " + method + ".\n"
          + "@FromJson method signatures may have one of the following structures:\n"
          + "    <any access modifier> R fromJson(JsonReader jsonReader) throws <any>;\n"
          + "    <any access modifier> R fromJson(JsonReader jsonReader,"
          + " JsonAdapter<any> delegate, <any more delegates>) throws <any>;\n"
          + "    <any access modifier> R fromJson(T value) throws <any>;\n");
    }
  }

  /** Returns the matching adapter method from the list. */
  private static @Nullable AdapterMethod get(
      List<AdapterMethod> adapterMethods, Type type, Set<? extends Annotation> annotations) {
    for (int i = 0, size = adapterMethods.size(); i < size; i++) {
      AdapterMethod adapterMethod = adapterMethods.get(i);
      if (Types.equals(adapterMethod.type, type) && adapterMethod.annotations.equals(annotations)) {
        return adapterMethod;
      }
    }
    return null;
  }

  abstract static class AdapterMethod {
    final Type type;
    final Set<? extends Annotation> annotations;
    final Object adapter;
    final Method method;
    final int adaptersOffset;
    final JsonAdapter<?>[] jsonAdapters;
    final boolean nullable;

    AdapterMethod(Type type, Set<? extends Annotation> annotations, Object adapter,
        Method method, int parameterCount, int adaptersOffset, boolean nullable) {
      this.type = canonicalize(type);
      this.annotations = annotations;
      this.adapter = adapter;
      this.method = method;
      this.adaptersOffset = adaptersOffset;
      this.jsonAdapters = new JsonAdapter[parameterCount - adaptersOffset];
      this.nullable = nullable;
    }

    public void bind(Moshi moshi, JsonAdapter.Factory factory) {
      if (jsonAdapters.length > 0) {
        Type[] parameterTypes = method.getGenericParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = adaptersOffset, size = parameterTypes.length; i < size; i++) {
          Type type = ((ParameterizedType) parameterTypes[i]).getActualTypeArguments()[0];
          Set<? extends Annotation> jsonAnnotations = jsonAnnotations(parameterAnnotations[i]);
          jsonAdapters[i - adaptersOffset] =
              Types.equals(this.type, type) && annotations.equals(jsonAnnotations)
                  ? moshi.nextAdapter(factory, type, jsonAnnotations)
                  : moshi.adapter(type, jsonAnnotations);
        }
      }
    }

    public void toJson(Moshi moshi, JsonWriter writer, @Nullable Object value)
        throws IOException, InvocationTargetException {
      throw new AssertionError();
    }

    public @Nullable Object fromJson(Moshi moshi, JsonReader reader)
        throws IOException, InvocationTargetException {
      throw new AssertionError();
    }

    /** Invoke the method with one fixed argument, plus any number of JSON adapter arguments. */
    protected @Nullable Object invoke(@Nullable Object a1) throws InvocationTargetException {
      Object[] args = new Object[1 + jsonAdapters.length];
      args[0] = a1;
      System.arraycopy(jsonAdapters, 0, args, 1, jsonAdapters.length);

      try {
        return method.invoke(adapter, args);
      } catch (IllegalAccessException e) {
        throw new AssertionError();
      }
    }

    /** Invoke the method with two fixed arguments, plus any number of JSON adapter arguments. */
    protected Object invoke(@Nullable Object a1, @Nullable Object a2)
        throws InvocationTargetException {
      Object[] args = new Object[2 + jsonAdapters.length];
      args[0] = a1;
      args[1] = a2;
      System.arraycopy(jsonAdapters, 0, args, 2, jsonAdapters.length);

      try {
        return method.invoke(adapter, args);
      } catch (IllegalAccessException e) {
        throw new AssertionError();
      }
    }
  }
}
