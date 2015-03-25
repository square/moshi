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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

/**
 * Emits a regular class as a JSON object by mapping Java fields to JSON object properties. Fields
 * of classes in {@code java.*}, {@code javax.*} and {@code android.*} are omitted from both
 * serialization and deserialization unless they are either public or protected.
 */
final class ClassJsonAdapter<T> extends JsonAdapter<T> {
  public static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {
    @Override public JsonAdapter<?> create(Type type, AnnotatedElement annotations, Moshi moshi) {
      Class<?> rawType = Types.getRawType(type);
      if (rawType.isInterface() || rawType.isEnum() || isPlatformType(rawType)) return null;

      if (rawType.getEnclosingClass() != null && !Modifier.isStatic(rawType.getModifiers())) {
        if (rawType.getSimpleName().isEmpty()) {
          throw new IllegalArgumentException(
              "cannot serialize anonymous class " + rawType.getName());
        } else {
          throw new IllegalArgumentException(
              "cannot serialize non-static nested class " + rawType.getName());
        }
      }
      if (Modifier.isAbstract(rawType.getModifiers())) {
        throw new IllegalArgumentException("cannot serialize abstract class " + rawType.getName());
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
        JsonAdapter<Object> adapter = moshi.adapter(fieldType, field);

        // Create the binding between field and JSON.
        field.setAccessible(true);
        FieldBinding<Object> fieldBinding = new FieldBinding<>(field, adapter);

        // Store it using the field's name. If there was already a field with this name, fail!
        FieldBinding<?> replaced = fieldBindings.put(field.getName(), fieldBinding);
        if (replaced != null) {
          throw new IllegalArgumentException("field name collision: '" + field.getName() + "'"
              + " declared by both " + replaced.field.getDeclaringClass().getName()
              + " and superclass " + fieldBinding.field.getDeclaringClass().getName());
        }
      }
    }

    /**
     * Returns true if {@code rawType} is built in. We don't reflect on private fields of platform
     * types because they're unspecified and likely to be different on Java vs. Android.
     */
    private boolean isPlatformType(Class<?> rawType) {
      return rawType.getName().startsWith("java.")
          || rawType.getName().startsWith("javax.")
          || rawType.getName().startsWith("android.");
    }

    /** Returns true if fields with {@code modifiers} are included in the emitted JSON. */
    private boolean includeField(boolean platformType, int modifiers) {
      if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) return false;
      return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)|| !platformType;
    }
  };

  private final ClassFactory<T> classFactory;
  private final Map<String, FieldBinding<?>> jsonFields;

  private ClassJsonAdapter(ClassFactory<T> classFactory, Map<String, FieldBinding<?>> jsonFields) {
    this.classFactory = classFactory;
    this.jsonFields = jsonFields;
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
        String name = reader.nextName();
        FieldBinding<?> fieldBinding = jsonFields.get(name);
        if (fieldBinding != null) {
          fieldBinding.read(reader, result);
        } else {
          reader.skipValue();
        }
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
      for (Map.Entry<String, FieldBinding<?>> entry : jsonFields.entrySet()) {
        writer.name(entry.getKey());
        entry.getValue().write(writer, value);
      }
      writer.endObject();
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    }
  }

  static class FieldBinding<T> {
    private final Field field;
    private final JsonAdapter<T> adapter;

    public FieldBinding(Field field, JsonAdapter<T> adapter) {
      this.field = field;
      this.adapter = adapter;
    }

    private void read(JsonReader reader, Object value) throws IOException, IllegalAccessException {
      T fieldValue = adapter.fromJson(reader);
      field.set(value, fieldValue);
    }

    @SuppressWarnings("unchecked") // We require that field's values are of type T.
    private void write(JsonWriter writer, Object value)
        throws IllegalAccessException, IOException {
      T fieldValue = (T) field.get(value);
      adapter.toJson(writer, fieldValue);
    }
  }
}
