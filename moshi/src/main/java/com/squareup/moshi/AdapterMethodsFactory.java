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
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class AdapterMethodsFactory implements JsonAdapter.Factory {
  private final List<AdapterMethod> toAdapters;
  private final List<AdapterMethod> fromAdapters;

  public AdapterMethodsFactory(List<AdapterMethod> toAdapters, List<AdapterMethod> fromAdapters) {
    this.toAdapters = toAdapters;
    this.fromAdapters = fromAdapters;
  }

  @Override public JsonAdapter<?> create(
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
            + type + " annotated " + annotations);
      }
    } else {
      delegate = null;
    }

    return new JsonAdapter<Object>() {
      @Override public void toJson(JsonWriter writer, Object value) throws IOException {
        if (toAdapter == null) {
          delegate.toJson(writer, value);
        } else if (!toAdapter.nullable && value == null) {
          writer.nullValue();
        } else {
          try {
            toAdapter.toJson(moshi, writer, value);
          } catch (IllegalAccessException e) {
            throw new AssertionError();
          } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw new JsonDataException(e.getCause() + " at " + writer.getPath());
          }
        }
      }

      @Override public Object fromJson(JsonReader reader) throws IOException {
        if (fromAdapter == null) {
          return delegate.fromJson(reader);
        } else if (!fromAdapter.nullable && reader.peek() == JsonReader.Token.NULL) {
          reader.nextNull();
          return null;
        } else {
          try {
            return fromAdapter.fromJson(moshi, reader);
          } catch (IllegalAccessException e) {
            throw new AssertionError();
          } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw new JsonDataException(e.getCause() + " at " + reader.getPath());
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
    Type[] parameterTypes = method.getGenericParameterTypes();
    final Type returnType = method.getGenericReturnType();

    if (parameterTypes.length == 2
        && parameterTypes[0] == JsonWriter.class
        && returnType == void.class) {
      // public void pointToJson(JsonWriter jsonWriter, Point point) throws Exception {
      Set<? extends Annotation> parameterAnnotations
          = Util.jsonAnnotations(method.getParameterAnnotations()[1]);
      return new AdapterMethod(parameterTypes[1], parameterAnnotations, adapter, method, false) {
        @Override public void toJson(Moshi moshi, JsonWriter writer, Object value)
            throws IOException, InvocationTargetException, IllegalAccessException {
          method.invoke(adapter, writer, value);
        }
      };

    } else if (parameterTypes.length == 1 && returnType != void.class) {
      // public List<Integer> pointToJson(Point point) throws Exception {
      final Set<? extends Annotation> returnTypeAnnotations = Util.jsonAnnotations(method);
      Annotation[][] parameterAnnotations = method.getParameterAnnotations();
      Set<? extends Annotation> qualifierAnnotations =
          Util.jsonAnnotations(parameterAnnotations[0]);
      boolean nullable = Util.hasNullable(parameterAnnotations[0]);
      return new AdapterMethod(parameterTypes[0], qualifierAnnotations, adapter, method, nullable) {
        @Override public void toJson(Moshi moshi, JsonWriter writer, Object value)
            throws IOException, InvocationTargetException, IllegalAccessException {
          JsonAdapter<Object> delegate = moshi.adapter(returnType, returnTypeAnnotations);
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

  /**
   * Returns an object that calls a {@code method} method on {@code adapter} in service of
   * converting an object from JSON.
   */
  static AdapterMethod fromAdapter(Object adapter, Method method) {
    method.setAccessible(true);
    final Type[] parameterTypes = method.getGenericParameterTypes();
    final Type returnType = method.getGenericReturnType();

    if (parameterTypes.length == 1
        && parameterTypes[0] == JsonReader.class
        && returnType != void.class) {
      // public Point pointFromJson(JsonReader jsonReader) throws Exception {
      Set<? extends Annotation> returnTypeAnnotations = Util.jsonAnnotations(method);
      return new AdapterMethod(returnType, returnTypeAnnotations, adapter, method, false) {
        @Override public Object fromJson(Moshi moshi, JsonReader reader)
            throws IOException, IllegalAccessException, InvocationTargetException {
          return method.invoke(adapter, reader);
        }
      };

    } else if (parameterTypes.length == 1 && returnType != void.class) {
      // public Point pointFromJson(List<Integer> o) throws Exception {
      Set<? extends Annotation> returnTypeAnnotations = Util.jsonAnnotations(method);
      Annotation[][] parameterAnnotations = method.getParameterAnnotations();
      final Set<? extends Annotation> qualifierAnnotations
          = Util.jsonAnnotations(parameterAnnotations[0]);
      boolean nullable = Util.hasNullable(parameterAnnotations[0]);
      return new AdapterMethod(returnType, returnTypeAnnotations, adapter, method, nullable) {
        @Override public Object fromJson(Moshi moshi, JsonReader reader)
            throws IOException, IllegalAccessException, InvocationTargetException {
          JsonAdapter<Object> delegate = moshi.adapter(parameterTypes[0], qualifierAnnotations);
          Object intermediate = delegate.fromJson(reader);
          return method.invoke(adapter, intermediate);
        }
      };

    } else {
      throw new IllegalArgumentException("Unexpected signature for " + method + ".\n"
          + "@FromJson method signatures may have one of the following structures:\n"
          + "    <any access modifier> void fromJson(JsonReader jsonReader) throws <any>;\n"
          + "    <any access modifier> R fromJson(T value) throws <any>;\n");
    }
  }

  /** Returns the matching adapter method from the list. */
  private static AdapterMethod get(
      List<AdapterMethod> adapterMethods, Type type, Set<? extends Annotation> annotations) {
    for (int i = 0, size = adapterMethods.size(); i < size; i++) {
      AdapterMethod adapterMethod = adapterMethods.get(i);
      if (adapterMethod.type.equals(type) && adapterMethod.annotations.equals(annotations)) {
        return adapterMethod;
      }
    }
    return null;
  }

  static abstract class AdapterMethod {
    final Type type;
    final Set<? extends Annotation> annotations;
    final Object adapter;
    final Method method;
    final boolean nullable;

    public AdapterMethod(Type type,
        Set<? extends Annotation> annotations, Object adapter, Method method, boolean nullable) {
      this.type = Types.canonicalize(type);
      this.annotations = annotations;
      this.adapter = adapter;
      this.method = method;
      this.nullable = nullable;
    }

    public void toJson(Moshi moshi, JsonWriter writer, Object value)
        throws IOException, IllegalAccessException, InvocationTargetException {
      throw new AssertionError();
    }

    public Object fromJson(Moshi moshi, JsonReader reader)
        throws IOException, IllegalAccessException, InvocationTargetException {
      throw new AssertionError();
    }
  }
}
