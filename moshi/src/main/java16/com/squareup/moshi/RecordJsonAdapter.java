/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi;

import static java.lang.invoke.MethodType.methodType;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link JsonAdapter} that supports Java {@code record} classes via reflection.
 *
 * <p><em>NOTE:</em> Java records require JDK 16 or higher.
 */
final class RecordJsonAdapter<T> extends JsonAdapter<T> {

  static final JsonAdapter.Factory FACTORY =
      (type, annotations, moshi) -> {
        if (!annotations.isEmpty()) {
          return null;
        }

        if (!(type instanceof Class) && !(type instanceof ParameterizedType)) {
          return null;
        }

        var rawType = Types.getRawType(type);
        if (!rawType.isRecord()) {
          return null;
        }

        Map<String, Type> mappedTypeArgs = null;
        if (type instanceof ParameterizedType parameterizedType) {
          Type[] typeArgs = parameterizedType.getActualTypeArguments();
          var typeVars = rawType.getTypeParameters();
          mappedTypeArgs = new LinkedHashMap<>(typeArgs.length);
          for (int i = 0; i < typeArgs.length; ++i) {
            var typeVarName = typeVars[i].getName();
            var materialized = typeArgs[i];
            mappedTypeArgs.put(typeVarName, materialized);
          }
        }
        var components = rawType.getRecordComponents();
        var bindings = new LinkedHashMap<String, ComponentBinding<?>>();
        var constructorParams = new Class<?>[components.length];
        var lookup = MethodHandles.lookup();
        for (int i = 0, componentsLength = components.length; i < componentsLength; i++) {
          RecordComponent component = components[i];
          constructorParams[i] = component.getType();
          var name = component.getName();
          var componentType = component.getGenericType();
          if (componentType instanceof TypeVariable<?> typeVariable) {
            var typeVarName = typeVariable.getName();
            if (mappedTypeArgs == null) {
              throw new AssertionError(
                  "No mapped type arguments found for type '" + typeVarName + "'");
            }
            var mappedType = mappedTypeArgs.get(typeVarName);
            if (mappedType == null) {
              throw new AssertionError(
                  "No materialized type argument found for type '" + typeVarName + "'");
            }
            componentType = mappedType;
          }
          var jsonName = name;
          Set<Annotation> qualifiers = null;
          for (var annotation : component.getDeclaredAnnotations()) {
            if (annotation instanceof Json jsonAnnotation) {
              var annotationName = jsonAnnotation.name();
              if (!Json.UNSET_NAME.equals(annotationName)) {
                jsonName = jsonAnnotation.name();
              }
            } else {
              if (annotation.annotationType().isAnnotationPresent(JsonQualifier.class)) {
                if (qualifiers == null) {
                  qualifiers = new LinkedHashSet<>();
                }
                qualifiers.add(annotation);
              }
            }
          }
          if (qualifiers == null) {
            qualifiers = Collections.emptySet();
          }
          var adapter = moshi.adapter(componentType, qualifiers);
          MethodHandle accessor;
          try {
            accessor = lookup.unreflect(component.getAccessor());
          } catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
          var componentBinding = new ComponentBinding<>(name, jsonName, adapter, accessor);
          var replaced = bindings.put(jsonName, componentBinding);
          if (replaced != null) {
            throw new IllegalArgumentException(
                "Conflicting components:\n"
                    + "    "
                    + replaced.name
                    + "\n"
                    + "    "
                    + componentBinding.name);
          }
        }

        MethodHandle constructor;
        try {
          constructor = lookup.findConstructor(rawType, methodType(void.class, constructorParams));
        } catch (NoSuchMethodException | IllegalAccessException e) {
          throw new AssertionError(e);
        }
        return new RecordJsonAdapter<>(constructor, rawType.getSimpleName(), bindings).nullSafe();
      };

  private static record ComponentBinding<T>(
      String name, String jsonName, JsonAdapter<T> adapter, MethodHandle accessor) {}

  private final String targetClass;
  private final MethodHandle constructor;
  private final ComponentBinding<Object>[] componentBindingsArray;
  private final JsonReader.Options options;

  @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
  public RecordJsonAdapter(
      MethodHandle constructor,
      String targetClass,
      Map<String, ComponentBinding<?>> componentBindings) {
    this.constructor = constructor;
    this.targetClass = targetClass;
    //noinspection unchecked
    this.componentBindingsArray =
        componentBindings.values().toArray(new ComponentBinding[componentBindings.size()]);
    this.options =
        JsonReader.Options.of(
            componentBindings.keySet().toArray(new String[componentBindings.size()]));
  }

  @Override
  public T fromJson(JsonReader reader) throws IOException {
    var resultsArray = new Object[componentBindingsArray.length];

    reader.beginObject();
    while (reader.hasNext()) {
      int index = reader.selectName(options);
      if (index == -1) {
        reader.skipName();
        reader.skipValue();
        continue;
      }
      var result = componentBindingsArray[index].adapter.fromJson(reader);
      resultsArray[index] = result;
    }
    reader.endObject();

    try {
      //noinspection unchecked
      return (T) constructor.invokeWithArguments(resultsArray);
    } catch (Throwable e) {
      if (e instanceof InvocationTargetException ite) {
        Throwable cause = ite.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new RuntimeException(cause);
      } else {
        throw new AssertionError(e);
      }
    }
  }

  @Override
  public void toJson(JsonWriter writer, T value) throws IOException {
    writer.beginObject();

    for (var binding : componentBindingsArray) {
      writer.name(binding.jsonName);
      try {
        binding.adapter.toJson(writer, binding.accessor.invoke(value));
      } catch (Throwable e) {
        if (e instanceof InvocationTargetException ite) {
          Throwable cause = ite.getCause();
          if (cause instanceof RuntimeException) throw (RuntimeException) cause;
          if (cause instanceof Error) throw (Error) cause;
          throw new RuntimeException(cause);
        } else {
          throw new AssertionError(e);
        }
      }
    }

    writer.endObject();
  }

  @Override
  public String toString() {
    return "JsonAdapter(" + targetClass + ")";
  }
}
