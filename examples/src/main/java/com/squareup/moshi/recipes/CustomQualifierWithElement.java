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
  @Retention(RUNTIME)
  @JsonQualifier
  public @interface DefaultReplacesNull {
    String json();
  }

  public static final class DefaultReplacesNullJsonAdapter extends JsonAdapter<Object> {
    final JsonAdapter<Object> delegate;
    final Object defaultIfNull;

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
        Object defaultIfNull;
        try {
          defaultIfNull = delegate.fromJson(defaultJson);
        } catch (IOException e) {
          throw new IllegalArgumentException("Malformed default JSON " + defaultJson, e);
        }
        if (defaultIfNull == null) {
          throw new IllegalArgumentException(
              "Default JSON should not deserialize to null: " + defaultJson);
        }
        return new DefaultReplacesNullJsonAdapter(delegate, defaultIfNull);
      }
    };

    DefaultReplacesNullJsonAdapter(JsonAdapter<Object> delegate, Object defaultIfNull) {
      this.delegate = delegate;
      this.defaultIfNull = defaultIfNull;
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      Object result = delegate.fromJson(reader);
      if (result == null) {
        result = defaultIfNull;
      }
      return result;
    }

    @Override public void toJson(JsonWriter writer, @Nullable Object value) throws IOException {
      if (value == null) {
        value = defaultIfNull;
      }
      delegate.toJson(writer, value);
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
    JsonAdapter<Data> adapter =
        new Moshi.Builder().add(DefaultReplacesNullJsonAdapter.FACTORY).build().adapter(Data.class);
    String json = "{\"name\":null,\"names\":null}";
    System.out.println(adapter.fromJson(json));
    System.out.println(adapter.toJson(new Data(null, null)));
  }

  private CustomQualifierWithElement() {
  }
}
