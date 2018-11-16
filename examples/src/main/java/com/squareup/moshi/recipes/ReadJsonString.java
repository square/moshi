package com.squareup.moshi.recipes;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import java.io.IOException;
import java.lang.annotation.Retention;
import okio.Buffer;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class ReadJsonString {
  @Retention(RUNTIME)
  @JsonQualifier
  public @interface JsonString {
    Object ADAPTER = new Object() {
      @FromJson @JsonString String fromJson(JsonReader reader) throws IOException {
        return reader.readJsonString().utf8();
      }

      @ToJson void toJson(JsonWriter writer, @JsonString String value) throws IOException {
        writer.value(new Buffer().writeUtf8(value));
      }
    };
  }

  static final class Location {
    final String name;
    final @JsonString String metadata;

    Location(String name, String metadata) {
      this.name = name;
      this.metadata = metadata;
    }
  }

  void run() throws IOException {
    Moshi moshi = new Moshi.Builder()
        .add(JsonString.ADAPTER)
        .build();
    JsonAdapter<Location> locationAdapter = moshi.adapter(Location.class);
    String json = "{\n"
        + "  \"name\": \"Niagara Falls\",\n"
        + "  \"metadata\": {\n"
        + "    \"latitude\": 43.0962,\n"
        + "    \"longitude\": -79.0377\n"
        + "  }\n"
        + "}";
    Location location = locationAdapter.fromJson(json);
    System.out.println(location.metadata);
    System.out.println(locationAdapter.toJson(new Location("Niagara Falls", "{\n"
        + "    \"latitude\": 43.0962,\n"
        + "    \"longitude\": -79.0377\n"
        + "  }")));
  }

  public static void main(String[] args) throws IOException {
    new ReadJsonString().run();
  }
}
