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

import static com.squareup.moshi.internal.Util.rethrowCause;
import static java.lang.invoke.MethodType.methodType;

import com.squareup.moshi.internal.Util;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link JsonAdapter} that supports Java {@code record} classes via reflection.
 *
 * <p><em>NOTE:</em> Java records require JDK 16 or higher.
 */
final class RecordJsonAdapter<T> extends JsonAdapter<T> {

  static final JsonAdapter.Factory FACTORY =
      new Factory() {
        @Override
        public JsonAdapter<?> create(
            Type type, Set<? extends Annotation> annotations, Moshi moshi) {
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

          var components = rawType.getRecordComponents();
          var bindings = new LinkedHashMap<String, ComponentBinding<?>>();
          var componentRawTypes = new Class<?>[components.length];
          var lookup = MethodHandles.lookup();
          for (int i = 0, componentsLength = components.length; i < componentsLength; i++) {
            RecordComponent component = components[i];
            componentRawTypes[i] = component.getType();
            ComponentBinding<Object> componentBinding =
                createComponentBinding(type, rawType, moshi, lookup, component);
            var replaced = bindings.put(componentBinding.jsonName, componentBinding);
            if (replaced != null) {
              throw new IllegalArgumentException(
                  "Conflicting components:\n"
                      + "    "
                      + replaced.componentName
                      + "\n"
                      + "    "
                      + componentBinding.componentName);
            }
          }

          MethodHandle constructor;
          try {
            constructor =
                lookup.findConstructor(rawType, methodType(void.class, componentRawTypes));
          } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
          }

          return new RecordJsonAdapter<>(constructor, rawType.getSimpleName(), bindings).nullSafe();
        }

        private static ComponentBinding<Object> createComponentBinding(
            Type type,
            Class<?> rawType,
            Moshi moshi,
            MethodHandles.Lookup lookup,
            RecordComponent component) {
          var componentName = component.getName();
          var jsonName = Util.jsonName(componentName, component);

          var componentType = Util.resolve(type, rawType, component.getGenericType());
          Set<? extends Annotation> qualifiers = Util.jsonAnnotations(component);
          var adapter = moshi.adapter(componentType, qualifiers);

          MethodHandle accessor;
          try {
            accessor = lookup.unreflect(component.getAccessor());
          } catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }

          return new ComponentBinding<>(componentName, jsonName, adapter, accessor);
        }
      };

  private static record ComponentBinding<T>(
      String componentName, String jsonName, JsonAdapter<T> adapter, MethodHandle accessor) {}

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
      resultsArray[index] = componentBindingsArray[index].adapter.fromJson(reader);
    }
    reader.endObject();

    try {
      //noinspection unchecked
      return (T) constructor.invokeWithArguments(resultsArray);
    } catch (InvocationTargetException e) {
      throw rethrowCause(e);
    } catch (Throwable e) {
      // Don't throw a fatal error if it's just an absent primitive.
      for (int i = 0, limit = componentBindingsArray.length; i < limit; i++) {
        if (resultsArray[i] == null
            && componentBindingsArray[i].accessor.type().returnType().isPrimitive()) {
          throw Util.missingProperty(
              componentBindingsArray[i].componentName, componentBindingsArray[i].jsonName, reader);
        }
      }
      throw new AssertionError(e);
    }
  }

  @Override
  public void toJson(JsonWriter writer, T value) throws IOException {
    writer.beginObject();

    for (var binding : componentBindingsArray) {
      writer.name(binding.jsonName);
      Object componentValue;
      try {
        componentValue = binding.accessor.invoke(value);
      } catch (InvocationTargetException e) {
        throw Util.rethrowCause(e);
      } catch (Throwable e) {
        throw new AssertionError(e);
      }
      binding.adapter.toJson(writer, componentValue);
    }

    writer.endObject();
  }

  @Override
  public String toString() {
    return "JsonAdapter(" + targetClass + ")";
  }
}
