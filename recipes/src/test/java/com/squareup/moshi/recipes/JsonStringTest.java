package com.squareup.moshi.recipes;

import static org.junit.Assert.assertEquals;

import com.squareup.moshi.Moshi;
import java.io.IOException;
import org.junit.Test;

public final class JsonStringTest {

  @Test
  public void testJsonString() throws IOException {
    // language=JSON
    String json = "{\"type\":1,\"rawJson\":{\"a\":2,\"b\":3,\"c\":[1,2,3]}}";

    Moshi moshi = new Moshi.Builder().add(new JsonStringJsonAdapterFactory()).build();

    ExampleClass example = moshi.adapter(ExampleClass.class).fromJson(json);
    assertEquals(1, example.type);
    // language=JSON
    assertEquals("{\"a\":2,\"b\":3,\"c\":[1,2,3]}", example.rawJson);
  }

  static class ExampleClass {
    int type;
    @JsonString String rawJson;
  }
}
