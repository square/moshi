/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;

public final class CustomAdapterFactory {
  public void run() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new SortedSetAdapterFactory())
        .build();
    JsonAdapter<SortedSet<String>> jsonAdapter = moshi.adapter(
        Types.newParameterizedType(SortedSet.class, String.class));

    TreeSet<String> model = new TreeSet<>();
    model.add("a");
    model.add("b");
    model.add("c");

    String json = jsonAdapter.toJson(model);
    System.out.println(json);
  }

  /**
   * This class composes an adapter for any element type into an adapter for a sorted set of those
   * elements. For example, given a {@code JsonAdapter<MovieTicket>}, use this to get a
   * {@code JsonAdapter<SortedSet<MovieTicket>>}. It works by looping over the input elements when
   * both reading and writing.
   */
  static final class SortedSetAdapter<T> extends JsonAdapter<SortedSet<T>> {
    private final JsonAdapter<T> elementAdapter;

    SortedSetAdapter(JsonAdapter<T> elementAdapter) {
      this.elementAdapter = elementAdapter;
    }

    @Override public SortedSet<T> fromJson(JsonReader reader) throws IOException {
      TreeSet<T> result = new TreeSet<>();
      reader.beginArray();
      while (reader.hasNext()) {
        result.add(elementAdapter.fromJson(reader));
      }
      reader.endArray();
      return result;
    }

    @Override public void toJson(JsonWriter writer, SortedSet<T> set) throws IOException {
      writer.beginArray();
      for (T element : set) {
        elementAdapter.toJson(writer, element);
      }
      writer.endArray();
    }
  }

  /**
   * Moshi asks this class to create JSON adapters. It only knows how to create JSON adapters for
   * {@code SortedSet} types, so it returns null for all other requests. When it does get a request
   * for a {@code SortedSet<X>}, it asks Moshi for an adapter of the element type {@code X} and then
   * uses that to create an adapter for the set.
   */
  static class SortedSetAdapterFactory implements JsonAdapter.Factory {
    @Override public @Nullable JsonAdapter<?> create(
        Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (!annotations.isEmpty()) {
        return null; // Annotations? This factory doesn't apply.
      }

      if (!(type instanceof ParameterizedType)) {
        return null; // No type parameter? This factory doesn't apply.
      }

      ParameterizedType parameterizedType = (ParameterizedType) type;
      if (parameterizedType.getRawType() != SortedSet.class) {
        return null; // Not a sorted set? This factory doesn't apply.
      }

      Type elementType = parameterizedType.getActualTypeArguments()[0];
      JsonAdapter<Object> elementAdapter = moshi.adapter(elementType);

      return new SortedSetAdapter<>(elementAdapter).nullSafe();
    }
  }

  public static void main(String[] args) throws Exception {
    new CustomAdapterFactory().run();
  }
}
