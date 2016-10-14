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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Emits a regular class as a JSON object by mapping Java fields to JSON object properties.
 *
 * <h3>Platform Types</h3>
 * Fields from platform classes are omitted from both serialization and deserialization unless
 * they are either public or protected. This includes the following packages and their subpackages:
 *
 * <ul>
 *   <li>android.*
 *   <li>java.*
 *   <li>javax.*
 *   <li>kotlin.*
 *   <li>scala.*
 * </ul>
 */
final class ClassJsonAdapter<T> extends JsonAdapter<T> {
  public static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {
    @Override public JsonAdapter<?> create(
        Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      Class<?> rawType = Types.getRawType(type);
      if (rawType.isInterface() || rawType.isEnum()) return null;
      if (isPlatformType(rawType)) {
        throw new IllegalArgumentException("Platform "
            + type
            + " annotated "
            + annotations
            + " requires explicit JsonAdapter to be registered");
      }
      if (!annotations.isEmpty()) return null;

      if (rawType.getEnclosingClass() != null && !Modifier.isStatic(rawType.getModifiers())) {
        if (rawType.getSimpleName().isEmpty()) {
          throw new IllegalArgumentException(
              "Cannot serialize anonymous class " + rawType.getName());
        } else {
          throw new IllegalArgumentException(
              "Cannot serialize non-static nested class " + rawType.getName());
        }
      }
      if (Modifier.isAbstract(rawType.getModifiers())) {
        throw new IllegalArgumentException("Cannot serialize abstract class " + rawType.getName());
      }

      ClassFactory<Object> classFactory = ClassFactory.get(rawType);
      Map<String, FieldBinding<?>> fields = new TreeMap<>();
      for (Type t = type; t != Object.class; t = Types.getGenericSuperclass(t)) {
        createFieldBindings(moshi, t, fields);
      }
      return new ClassJsonAdapter<>(classFactory, fields).nullSafe();
    }

    /** Creates a field binding for each of declared field of {@code type}. */
    private void createFieldBindings(
        Moshi moshi, Type type, Map<String, FieldBinding<?>> fieldBindings) {
      Class<?> rawType = Types.getRawType(type);
      boolean platformType = isPlatformType(rawType);
      for (Field field : rawType.getDeclaredFields()) {
        if (!includeField(platformType, field.getModifiers())) continue;

        // Look up a type adapter for this type.
        Type fieldType = Types.resolve(type, rawType, field.getGenericType());
        Set<? extends Annotation> annotations = Util.jsonAnnotations(field);
        JsonAdapter<Object> adapter = moshi.adapter(fieldType, annotations);

        // Create the binding between field and JSON.
        field.setAccessible(true);

        // Store it using the field's name. If there was already a field with this name, fail!
        Json jsonAnnotation = field.getAnnotation(Json.class);
        String name = jsonAnnotation != null ? jsonAnnotation.name() : field.getName();
        FieldBinding<Object> fieldBinding = new FieldBinding<>(name, field, adapter);
        FieldBinding<?> replaced = fieldBindings.put(name, fieldBinding);
        if (replaced != null) {
          throw new IllegalArgumentException("Conflicting fields:\n"
              + "    " + replaced.field + "\n"
              + "    " + fieldBinding.field);
        }
      }
    }

    /**
     * Returns true if {@code rawType} is built in. We don't reflect on private fields of platform
     * types because they're unspecified and likely to be different on Java vs. Android.
     */
    private boolean isPlatformType(Class<?> rawType) {
      String name = rawType.getName();
      return name.startsWith("android.")
          || name.startsWith("java.")
          || name.startsWith("javax.")
          || name.startsWith("kotlin.")
          || name.startsWith("scala.");
    }

    /** Returns true if fields with {@code modifiers} are included in the emitted JSON. */
    private boolean includeField(boolean platformType, int modifiers) {
      if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) return false;
      return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)|| !platformType;
    }
  };

  private final ClassFactory<T> classFactory;
  private final Map<String, FieldBinding<?>> fieldsMap;
  private final FieldBinding<?>[] fieldsArray;
  private final JsonReader.Options options;

  ClassJsonAdapter(ClassFactory<T> classFactory, Map<String, FieldBinding<?>> fieldsMap) {
    this.classFactory = classFactory;
    this.fieldsMap = new LinkedHashMap<>(fieldsMap);
    this.fieldsArray = fieldsMap.values().toArray(new FieldBinding[fieldsMap.size()]);
    this.options = JsonReader.Options.of(
        fieldsMap.keySet().toArray(new String[fieldsMap.size()]));
  }

  @Override public T fromJson(JsonReader reader) throws IOException {
    T result;
    try {
      result = classFactory.newInstance();
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      Throwable targetException = e.getTargetException();
      if (targetException instanceof RuntimeException) throw (RuntimeException) targetException;
      if (targetException instanceof Error) throw (Error) targetException;
      throw new RuntimeException(targetException);
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    }

    try {
      reader.beginObject();
      while (reader.hasNext()) {
        int index = reader.selectName(options);
        FieldBinding<?> fieldBinding;
        if (index != -1) {
          fieldBinding = fieldsArray[index];
        } else {
          String name = reader.nextName();
          fieldBinding = fieldsMap.get(name);
          if (fieldBinding == null) {
            reader.skipValue();
            continue;
          }
        }
        fieldBinding.read(reader, result);
      }
      reader.endObject();
      return result;
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    }
  }

  @Override public void toJson(JsonWriter writer, T value) throws IOException {
    try {
      writer.beginObject();
      for (FieldBinding<?> fieldBinding : fieldsArray) {
        writer.name(fieldBinding.name);
        fieldBinding.write(writer, value);
      }
      writer.endObject();
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    }
  }

  @Override public String toString() {
    return "JsonAdapter(" + classFactory + ")";
  }

  static class FieldBinding<T> {
    final String name;
    final Field field;
    final JsonAdapter<T> adapter;

    public FieldBinding(String name, Field field, JsonAdapter<T> adapter) {
      this.name = name;
      this.field = field;
      this.adapter = adapter;
    }

    void read(JsonReader reader, Object value) throws IOException, IllegalAccessException {
      T fieldValue = adapter.fromJson(reader);
      field.set(value, fieldValue);
    }

    @SuppressWarnings("unchecked") // We require that field's values are of type T.
    void write(JsonWriter writer, Object value) throws IllegalAccessException, IOException {
      T fieldValue = (T) field.get(value);
      adapter.toJson(writer, fieldValue);
    }
  }
}
