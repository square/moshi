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

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

// TODO: support qualifier annotations.
// TODO: support @Nullable
// TODO: path in JsonWriter.

final class AdapterMethodsFactory implements JsonAdapter.Factory {
  private final Map<Type, ToAdapter> toAdapters;
  private final Map<Type, FromAdapter> fromAdapters;

  AdapterMethodsFactory(Map<Type, ToAdapter> toAdapters, Map<Type, FromAdapter> fromAdapters) {
    this.toAdapters = toAdapters;
    this.fromAdapters = fromAdapters;
  }

  @Override public JsonAdapter<?> create(Type type, AnnotatedElement annotations, final Moshi moshi) {
    final ToAdapter toAdapter = toAdapters.get(type);
    final FromAdapter fromAdapter = fromAdapters.get(type);
    if (toAdapter == null && fromAdapter == null) return null;

    final JsonAdapter<Object> delegate = toAdapter == null || fromAdapter == null
        ? moshi.nextAdapter(this, type, annotations)
        : null;

    return new JsonAdapter<Object>() {
      @Override public void toJson(JsonWriter writer, Object value) throws IOException {
        if (toAdapter == null) {
          delegate.toJson(writer, value);
        } else {
          try {
            toAdapter.toJson(moshi, writer, value);
          } catch (IllegalAccessException e) {
            throw new AssertionError();
          } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw new JsonDataException(e.getCause().getMessage()); // TODO: more context?
          }
        }
      }

      @Override public Object fromJson(JsonReader reader) throws IOException {
        if (fromAdapter == null) {
          return delegate.fromJson(reader);
        } else {
          try {
            return fromAdapter.fromJson(moshi, reader);
          } catch (IllegalAccessException e) {
            throw new AssertionError();
          } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw new JsonDataException(e.getCause().getMessage()); // TODO: more context?
          }
        }
      }
    };
  }

  public static AdapterMethodsFactory get(Object adapter) {
    Map<Type, ToAdapter> toAdapters = new LinkedHashMap<>();
    Map<Type, FromAdapter> fromAdapters = new LinkedHashMap<>();

    for (Class<?> c = adapter.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method m : c.getDeclaredMethods()) {
        if (m.isAnnotationPresent(ToJson.class)) {
          ToAdapter toAdapter = toAdapter(adapter, m);
          ToAdapter replaced = toAdapters.put(toAdapter.type, toAdapter);
          if (replaced != null) {
            throw new IllegalArgumentException("Conflicting @ToJson methods:\n"
                + "    " + replaced.method + "\n"
                + "    " + toAdapter.method);
          }
        }

        if (m.isAnnotationPresent(FromJson.class)) {
          FromAdapter fromAdapter = fromAdapter(adapter, m);
          FromAdapter replaced = fromAdapters.put(fromAdapter.type, fromAdapter);
          if (replaced != null) {
            throw new IllegalArgumentException("Conflicting @FromJson methods:\n"
                + "    " + replaced.method + "\n"
                + "    " + fromAdapter.method);
          }
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
  static ToAdapter toAdapter(Object adapter, Method method) {
    method.setAccessible(true);
    Type[] parameterTypes = method.getGenericParameterTypes();
    final Type returnType = method.getGenericReturnType();

    if (parameterTypes.length == 2
        && parameterTypes[0] == JsonWriter.class
        && returnType == void.class) {
      return new ToAdapter(parameterTypes[1], adapter, method) {
        @Override public void toJson(Moshi moshi, JsonWriter writer, Object value)
            throws IOException, InvocationTargetException, IllegalAccessException {
          method.invoke(adapter, writer, value);
        }
      };

    } else if (parameterTypes.length == 1 && returnType != void.class) {
      return new ToAdapter(parameterTypes[0], adapter, method) {
        @Override public void toJson(Moshi moshi, JsonWriter writer, Object value)
            throws IOException, InvocationTargetException, IllegalAccessException {
          JsonAdapter<Object> delegate = moshi.adapter(returnType, method);
          Object intermediate = method.invoke(adapter, value);
          delegate.toJson(writer, intermediate);
        }
      };

    } else {
      throw new IllegalArgumentException("Unexpected signature for " + method + ".\n"
          + "@ToJson method signatures may have one of the following structures:\n"
          + "    <any access modifier> void toJson(JsonWriter writer, T value) throws <any>;\n"
          + "    <any access modifier> R toJson(T value) throws <any>;\n");
    }
  }

  static abstract class ToAdapter {
    final Type type;
    final Object adapter;
    final Method method;

    public ToAdapter(Type type, Object adapter, Method method) {
      this.type = type;
      this.adapter = adapter;
      this.method = method;
    }

    public abstract void toJson(Moshi moshi, JsonWriter writer, Object value)
        throws IOException, IllegalAccessException, InvocationTargetException;
  }

  /**
   * Returns an object that calls a {@code method} method on {@code adapter} in service of
   * converting an object from JSON.
   */
  static FromAdapter fromAdapter(Object adapter, Method method) {
    method.setAccessible(true);
    final Type[] parameterTypes = method.getGenericParameterTypes();
    final Type returnType = method.getGenericReturnType();

    if (parameterTypes.length == 1
        && parameterTypes[0] == JsonReader.class
        && returnType != void.class) {
      // public Point pointFromJson(JsonReader jsonReader) throws Exception {
      return new FromAdapter(returnType, adapter, method) {
        @Override public Object fromJson(Moshi moshi, JsonReader reader)
            throws IOException, IllegalAccessException, InvocationTargetException {
          return method.invoke(adapter, reader);
        }
      };

    } else if (parameterTypes.length == 1 && returnType != void.class) {
      // public Point pointFromJson(List<Integer> o) throws Exception {
      return new FromAdapter(returnType, adapter, method) {
        @Override public Object fromJson(Moshi moshi, JsonReader reader)
            throws IOException, IllegalAccessException, InvocationTargetException {
          JsonAdapter<Object> delegate = moshi.adapter(parameterTypes[0]);
          Object intermediate = delegate.fromJson(reader);
          return method.invoke(adapter, intermediate);
        }
      };

    } else {
      throw new IllegalArgumentException("Unexpected signature for " + method + ".\n"
          + "@ToJson method signatures may have one of the following structures:\n"
          + "    <any access modifier> void toJson(JsonWriter writer, T value) throws <any>;\n"
          + "    <any access modifier> R toJson(T value) throws <any>;\n");
    }
  }

  static abstract class FromAdapter {
    final Type type;
    final Object adapter;
    final Method method;

    public FromAdapter(Type type, Object adapter, Method method) {
      this.type = type;
      this.adapter = adapter;
      this.method = method;
    }

    public abstract Object fromJson(Moshi moshi, JsonReader reader)
        throws IOException, IllegalAccessException, InvocationTargetException;
  }
}
