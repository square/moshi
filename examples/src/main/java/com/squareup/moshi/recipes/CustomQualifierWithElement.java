/*
 * Copyright (C) 2018 Square, Inc.
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
package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class CustomQualifierWithElement {
  /**
   * A qualifier to use a deserialized default value for the targeted type instead of the null
   * literal. If the targeted type is <em>absent</em> from an enclosing JSON object, this will have
   * no effect. The deserialized default value will be shared and should be immutable.
   */
  @Retention(RUNTIME)
  @JsonQualifier
  public @interface DefaultReplacesNull {
    String json();
  }

  public static final class DefaultReplacesNullJsonAdapter extends JsonAdapter<Object> {
    final JsonAdapter<Object> delegate;
    final String defaultJson;
    @Nullable volatile Object defaultIfNull; // Lazily computed.

    public static final Factory FACTORY = new Factory() {
      @Nullable @Override
      public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if (annotations.isEmpty()) {
          return null;
        }
        Set<? extends Annotation> delegateAnnotations = null;
        String defaultJson = null;
        for (Annotation annotation : annotations) {
          if (annotation instanceof DefaultReplacesNull) {
            delegateAnnotations = new LinkedHashSet<>(annotations);
            delegateAnnotations.remove(annotation);
            delegateAnnotations = Collections.unmodifiableSet(delegateAnnotations);
            defaultJson = ((DefaultReplacesNull) annotation).json();
            break;
          }
        }
        if (delegateAnnotations == null) {
          return null;
        }
        JsonAdapter<Object> delegate = moshi.adapter(type, delegateAnnotations);
        return new DefaultReplacesNullJsonAdapter(delegate, defaultJson);
      }
    };

    DefaultReplacesNullJsonAdapter(JsonAdapter<Object> delegate, String defaultJson) {
      this.delegate = delegate;
      this.defaultJson = defaultJson;
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      Object result = delegate.fromJson(reader);
      if (result == null) {
        result = defaultIfNull();
      }
      return result;
    }

    @Override public void toJson(JsonWriter writer, @Nullable Object value) throws IOException {
      if (defaultIfNull().equals(value)) {
        delegate.toJson(writer, null);
      } else {
        delegate.toJson(writer, value);
      }
    }

    private Object defaultIfNull() {
      Object defaultIfNull = this.defaultIfNull;
      if (defaultIfNull == null) {
        try {
          defaultIfNull = delegate.fromJson(defaultJson);
        } catch (IOException e) {
          throw new IllegalArgumentException("Malformed default JSON: " + defaultJson, e);
        }
        if (defaultIfNull == null) {
          throw new IllegalArgumentException(
              "Default JSON should not deserialize to null: " + defaultJson);
        }
      }
      this.defaultIfNull = defaultIfNull;
      return defaultIfNull;
    }
  }

  static final class Data {
    @DefaultReplacesNull(json = "\"\"") final String name;
    @DefaultReplacesNull(json = "[]") final List<String> names;

    Data(String name, List<String> names) {
      this.name = name;
      this.names = names;
    }

    @Override public String toString() {
      return "Data{"
          + "name='" + name + '\''
          + ", names=" + names
          + '}';
    }
  }

  public static void main(String[] args) throws IOException {
    JsonAdapter<Data> adapter = new Moshi.Builder().add(DefaultReplacesNullJsonAdapter.FACTORY)
        .build()
        .adapter(Data.class)
        .serializeNulls();
    String json = "{\"name\":null,\"names\":null}";
    System.out.println(adapter.fromJson(json));
    System.out.println(adapter.toJson(new Data("", Collections.<String>emptyList())));
  }

  private CustomQualifierWithElement() {
  }
}
