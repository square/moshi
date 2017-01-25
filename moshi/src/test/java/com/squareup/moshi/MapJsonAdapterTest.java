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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import okio.Buffer;
import org.assertj.core.data.MapEntry;
import org.junit.Test;

import static com.squareup.moshi.TestUtil.newReader;
import static com.squareup.moshi.Util.NO_ANNOTATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class MapJsonAdapterTest {
  private final Moshi moshi = new Moshi.Builder().build();

  @Test public void map() throws Exception {
    Map<String, Boolean> map = new LinkedHashMap<>();
    map.put("a", true);
    map.put("b", false);
    map.put("c", null);

    String toJson = mapToJson(String.class, Boolean.class, map);
    assertThat(toJson).isEqualTo("{\"a\":true,\"b\":false,\"c\":null}");

    Map<String, Boolean> fromJson = mapFromJson(
        String.class, Boolean.class, "{\"a\":true,\"b\":false,\"c\":null}");
    assertThat(fromJson).containsExactly(
        MapEntry.entry("a", true), MapEntry.entry("b", false), MapEntry.entry("c", null));
  }

  @Test public void mapWithNullKeyFailsToEmit() throws Exception {
    Map<String, Boolean> map = new LinkedHashMap<>();
    map.put(null, true);

    try {
      mapToJson(String.class, Boolean.class, map);
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Map key is null at $.");
    }
  }

  @Test public void emptyMap() throws Exception {
    Map<String, Boolean> map = new LinkedHashMap<>();

    String toJson = mapToJson(String.class, Boolean.class, map);
    assertThat(toJson).isEqualTo("{}");

    Map<String, Boolean> fromJson = mapFromJson(String.class, Boolean.class, "{}");
    assertThat(fromJson).isEmpty();
  }

  @Test public void nullMap() throws Exception {
    JsonAdapter<?> jsonAdapter = mapAdapter(String.class, Boolean.class);

    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.setLenient(true);
    jsonAdapter.toJson(jsonWriter, null);
    assertThat(buffer.readUtf8()).isEqualTo("null");

    JsonReader jsonReader = newReader("null");
    jsonReader.setLenient(true);
    assertThat(jsonAdapter.fromJson(jsonReader)).isEqualTo(null);
  }

  @Test public void orderIsRetained() throws Exception {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("c", 1);
    map.put("a", 2);
    map.put("d", 3);
    map.put("b", 4);

    String toJson = mapToJson(String.class, Integer.class, map);
    assertThat(toJson).isEqualTo("{\"c\":1,\"a\":2,\"d\":3,\"b\":4}");

    Map<String, Integer> fromJson = mapFromJson(
        String.class, Integer.class, "{\"c\":1,\"a\":2,\"d\":3,\"b\":4}");
    assertThat(new ArrayList<Object>(fromJson.keySet()))
        .isEqualTo(Arrays.asList("c", "a", "d", "b"));
  }

  @Test public void duplicatesAreForbidden() throws Exception {
    try {
      mapFromJson(String.class, Integer.class, "{\"c\":1,\"c\":2}");
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Map key 'c' has multiple values at path $.c: 1 and 2");
    }
  }

  /** This leans on {@code promoteNameToValue} to do the heavy lifting. */
  @Test public void mapWithNonStringKeys() throws Exception {
    Map<Integer, Boolean> map = new LinkedHashMap<>();
    map.put(5, true);
    map.put(6, false);
    map.put(7, null);

    String toJson = mapToJson(Integer.class, Boolean.class, map);
    assertThat(toJson).isEqualTo("{\"5\":true,\"6\":false,\"7\":null}");

    Map<String, Boolean> fromJson = mapFromJson(
        Integer.class, Boolean.class, "{\"5\":true,\"6\":false,\"7\":null}");
    assertThat(fromJson).containsExactly(
        MapEntry.entry(5, true), MapEntry.entry(6, false), MapEntry.entry(7, null));
  }

  /** If the requested type is a raw Map with enum keys, use EnumMap. */
  @Test public void mapWithEnumKeys() throws Exception {
    Map<Rating, String> map = new LinkedHashMap<>();
    map.put(Rating.PG, "Star Wars");
    map.put(Rating.NC_17, null);
    map.put(Rating.PG_13, "Spectre");

    String toJson = mapToJson(Rating.class, String.class, map);
    assertThat(toJson).isEqualTo("{"
        + "\"PG\":\"Star Wars\","
        + "\"NC_17\":null,"
        + "\"PG_13\":\"Spectre\""
        + "}");

    Map<Rating, String> fromJson = mapFromJson(Rating.class, String.class, toJson);
    assertThat(fromJson).containsExactly(
        MapEntry.entry(Rating.PG, "Star Wars"), MapEntry.entry(Rating.PG_13, "Spectre"),
        MapEntry.entry(Rating.NC_17, null));
    assertThat(fromJson).isInstanceOf(EnumMap.class);
  }

  @Test public void enumMap() throws Exception {
    EnumMap<Rating, String> map = new EnumMap<>(Rating.class);
    map.put(Rating.G, "Lion King");
    map.put(Rating.PG_13, "Scream");
    map.put(Rating.R, null);
    map.put(Rating.NC_17, "Saw");

    String toJson = mapToJson(Rating.class, String.class, map);
    assertThat(toJson).isEqualTo("{"
        + "\"G\":\"Lion King\","
        + "\"PG_13\":\"Scream\","
        + "\"R\":null,"
        + "\"NC_17\":\"Saw\""
        + "}");

    EnumMap<Rating, String> fromJson = enumMapFromJson(Rating.class, String.class, toJson);
    assertThat(fromJson).containsExactly(
        MapEntry.entry(Rating.G, "Lion King"), MapEntry.entry(Rating.PG_13, "Scream"),
        MapEntry.entry(Rating.R, null), MapEntry.entry(Rating.NC_17, "Saw"));
  }

  private <K, V> String mapToJson(Type keyType, Type valueType, Map<K, V> value)
      throws IOException {
    JsonAdapter<Map<K, V>> jsonAdapter = mapAdapter(keyType, valueType);
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.setSerializeNulls(true);
    jsonAdapter.toJson(jsonWriter, value);
    return buffer.readUtf8();
  }

  private <K, V> Map<K, V> mapFromJson(Type keyType, Type valueType, String json)
      throws IOException {
    JsonAdapter<Map<K, V>> mapJsonAdapter = mapAdapter(keyType, valueType);
    return mapJsonAdapter.fromJson(json);
  }

  @SuppressWarnings("unchecked") // It's the caller's responsibility to make sure K and V match.
  private <K, V> JsonAdapter<Map<K, V>> mapAdapter(Type keyType, Type valueType) {
    return (JsonAdapter<Map<K, V>>) MapJsonAdapter.FACTORY.create(
        Types.newParameterizedType(Map.class, keyType, valueType), NO_ANNOTATIONS, moshi);
  }

  private <K extends Enum<K>, V> EnumMap<K, V> enumMapFromJson(Type keyType, Type valueType,
      String json) throws IOException {
    JsonAdapter<EnumMap<K, V>> mapJsonAdapter = enumMapAdapter(keyType, valueType);
    return mapJsonAdapter.fromJson(json);
  }

  @SuppressWarnings("unchecked") // Compiler will block the caller form doing anything funky.
  private <K extends Enum<K>, V> JsonAdapter<EnumMap<K, V>> enumMapAdapter(
      Type keyType, Type valueType) {
    return (JsonAdapter<EnumMap<K, V>>) MapJsonAdapter.FACTORY.create(
        Types.newParameterizedType(EnumMap.class, keyType, valueType), NO_ANNOTATIONS, moshi);
  }

  private enum Rating {
    G, PG, PG_13, R, NC_17
  }
}
