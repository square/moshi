package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
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
import java.util.Set;
import javax.annotation.Nullable;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class CustomQualifierWithElement {
  @Retention(RUNTIME)
  @JsonQualifier
  public @interface Nested {
    String[] keys();
  }

  public static final class NestingJsonAdapter extends JsonAdapter<Object> {
    final String[] keys;
    final JsonAdapter<Object> delegate;

    public static final Factory FACTORY = new Factory() {
      @Nullable @Override
      public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if (annotations.isEmpty()) {
          return null;
        }
        Set<? extends Annotation> delegateAnnotations = null;
        String[] keys = null;
        for (Annotation annotation : annotations) {
          if (annotation instanceof Nested) {
            delegateAnnotations = new LinkedHashSet<>(annotations);
            delegateAnnotations.remove(annotation);
            delegateAnnotations = Collections.unmodifiableSet(delegateAnnotations);
            keys = ((Nested) annotation).keys();
            break;
          }
        }
        if (delegateAnnotations == null) {
          return null;
        }
        JsonAdapter<Object> delegate = moshi.adapter(type, delegateAnnotations);
        return new NestingJsonAdapter(keys, delegate);
      }
    };

    NestingJsonAdapter(String[] keys, JsonAdapter<Object> delegate) {
      this.keys = keys;
      this.delegate = delegate;
    }

    @Nullable @Override public Object fromJson(JsonReader reader) throws IOException {
      return fromJson(reader, 0);
    }

    @Override public void toJson(JsonWriter writer, @Nullable Object value) throws IOException {
      toJson(writer, 0, value);
    }

    void toJson(JsonWriter writer, int index, @Nullable Object value) throws IOException {
      if (index == keys.length) {
        delegate.toJson(writer, value);
        return;
      }
      writer.beginObject();
      writer.name(keys[index]);
      toJson(writer, index + 1, value);
      writer.endObject();
    }

    @Nullable Object fromJson(JsonReader reader, int index) throws IOException {
      if (index == keys.length) {
        return delegate.fromJson(reader);
      }
      String key = keys[index];
      reader.beginObject();
      while (reader.hasNext()) {
        if (!reader.nextName().equals(key)) {
          reader.skipValue();
          continue;
        }
        Object result = fromJson(reader, index + 1);
        while (reader.hasNext()) {
          reader.nextName();
          reader.skipValue();
        }
        reader.endObject();
        return result;
      }
      throw new JsonDataException("Nesting key '" + key + "' not found.");
    }
  }

  static final class Data {
    @Nested(keys = {"one", "two"}) final String nested;
    @Nested(keys = {}) final String not_really_nested;

    Data(String nested, String not_really_nested) {
      this.nested = nested;
      this.not_really_nested = not_really_nested;
    }

    @Override public String toString() {
      return "Data{"
          + "nested='" + nested + '\''
          + ", not_really_nested='" + not_really_nested + '\''
          + '}';
    }
  }

  public static void main(String[] args) throws IOException {
    JsonAdapter<Data> adapter =
        new Moshi.Builder().add(NestingJsonAdapter.FACTORY).build().adapter(Data.class);
    String json = "{\"nested\":"
        + "{\"one\":{\"unused\":[],\"two\":\"hello\"},\"unused\":[]},"
        + "\"not_really_nested\":\"world\""
        + "}";
    System.out.println(adapter.fromJson(json));
    System.out.println(adapter.toJson(new Data("hello", "world")));
  }

  private CustomQualifierWithElement() {
  }
}
