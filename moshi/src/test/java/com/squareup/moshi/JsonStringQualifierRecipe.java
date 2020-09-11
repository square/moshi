package com.squareup.moshi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Type;
import java.util.Set;
import okio.BufferedSink;
import okio.BufferedSource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class JsonStringQualifierRecipe {

  @Test public void testJsonString() throws IOException {
    //language=JSON
    String json = "{\"type\":1,\"rawJson\":{\"a\":2,\"b\":3,\"c\":[1,2,3]}}";

    Moshi moshi = new Moshi.Builder()
        .add(new JsonStringJsonAdapterFactory())
        .build();

    ExampleClass example = moshi.adapter(ExampleClass.class).fromJson(json);
    assertEquals(1, example.type);
    //language=JSON
    assertEquals("{\"a\":2,\"b\":3,\"c\":[1,2,3]}", example.rawJson);
  }

  static class ExampleClass {
    int type;
    @JsonString String rawJson;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @JsonQualifier
  public @interface JsonString {}

  static class JsonStringJsonAdapterFactory implements JsonAdapter.Factory {
    @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (type != String.class) return null;
      Set<? extends Annotation> nextAnnotations = Types.nextAnnotations(annotations, JsonString.class);
      if (nextAnnotations != null) {
        return new JsonAdapter<String>() {
          @Override public String fromJson(JsonReader reader) throws IOException {
            try (BufferedSource source = reader.valueSource()) {
              return source.readUtf8();
            }
          }

          @Override
          public void toJson(JsonWriter writer, String value) throws IOException {
            try (BufferedSink sink = writer.valueSink()) {
              sink.writeUtf8(value);
            }
          }
        };
      }
      return null;
    }
  }
}
