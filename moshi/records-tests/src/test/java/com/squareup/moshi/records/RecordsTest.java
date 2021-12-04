/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.records;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;

public final class RecordsTest {

  private final Moshi moshi = new Moshi.Builder().build();

  @Test
  public void smokeTest() throws IOException {
    var stringAdapter = moshi.adapter(String.class);
    var adapter =
        moshi
            .newBuilder()
            .add(CharSequence.class, stringAdapter)
            .add(Types.subtypeOf(CharSequence.class), stringAdapter)
            .add(Types.supertypeOf(CharSequence.class), stringAdapter)
            .build()
            .adapter(SmokeTestType.class);
    var instance =
        new SmokeTestType(
            "John",
            "Smith",
            25,
            List.of("American"),
            70.5f,
            null,
            true,
            List.of("super wildcards!"),
            List.of("extend wildcards!"),
            List.of("unbounded"),
            List.of("objectList"),
            new int[] {1, 2, 3},
            new String[] {"fav", "arrays"},
            Map.of("italian", "pasta"),
            Set.of(List.of(Map.of("someKey", new int[] {1}))),
            new Map[] {Map.of("Hello", "value")});
    var json = adapter.toJson(instance);
    var deserialized = adapter.fromJson(json);
    assertThat(deserialized).isEqualTo(instance);
  }

  public static record SmokeTestType(
      @Json(name = "first_name") String firstName,
      @Json(name = "last_name") String lastName,
      int age,
      List<String> nationalities,
      float weight,
      Boolean tattoos, // Boxed primitive test
      boolean hasChildren,
      List<? super CharSequence> superWildcard,
      List<? extends CharSequence> extendsWildcard,
      List<?> unboundedWildcard,
      List<Object> objectList,
      int[] favoriteThreeNumbers,
      String[] favoriteArrayValues,
      Map<String, String> foodPreferences,
      Set<List<Map<String, int[]>>> setListMapArrayInt,
      Map<String, Object>[] nestedArray) {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SmokeTestType that = (SmokeTestType) o;
      return age == that.age
          && Float.compare(that.weight, weight) == 0
          && hasChildren == that.hasChildren
          && firstName.equals(that.firstName)
          && lastName.equals(that.lastName)
          && nationalities.equals(that.nationalities)
          && Objects.equals(tattoos, that.tattoos)
          && superWildcard.equals(that.superWildcard)
          && extendsWildcard.equals(that.extendsWildcard)
          && unboundedWildcard.equals(that.unboundedWildcard)
          && objectList.equals(that.objectList)
          && Arrays.equals(favoriteThreeNumbers, that.favoriteThreeNumbers)
          && Arrays.equals(favoriteArrayValues, that.favoriteArrayValues)
          && foodPreferences.equals(that.foodPreferences)
          // && setListMapArrayInt.equals(that.setListMapArrayInt) // Nested array equality doesn't
          // carry over
          && Arrays.equals(nestedArray, that.nestedArray);
    }

    @Override
    public int hashCode() {
      int result =
          Objects.hash(
              firstName,
              lastName,
              age,
              nationalities,
              weight,
              tattoos,
              hasChildren,
              superWildcard,
              extendsWildcard,
              unboundedWildcard,
              objectList,
              foodPreferences,
              setListMapArrayInt);
      result = 31 * result + Arrays.hashCode(favoriteThreeNumbers);
      result = 31 * result + Arrays.hashCode(favoriteArrayValues);
      result = 31 * result + Arrays.hashCode(nestedArray);
      return result;
    }
  }

  @Test
  public void genericRecord() throws IOException {
    var adapter =
        moshi.<GenericRecord<String>>adapter(
            Types.newParameterizedTypeWithOwner(
                RecordsTest.class, GenericRecord.class, String.class));
    assertThat(adapter.fromJson("{\"value\":\"Okay!\"}")).isEqualTo(new GenericRecord<>("Okay!"));
  }

  public static record GenericRecord<T>(T value) {}

  @Test
  public void genericBoundedRecord() throws IOException {
    var adapter =
        moshi.<GenericBoundedRecord<Integer>>adapter(
            Types.newParameterizedTypeWithOwner(
                RecordsTest.class, GenericBoundedRecord.class, Integer.class));
    assertThat(adapter.fromJson("{\"value\":4}")).isEqualTo(new GenericBoundedRecord<>(4));
  }

  @Test
  public void indirectGenerics() throws IOException {
    var value =
        new HasIndirectGenerics(
            new IndirectGenerics<>(1L, List.of(2L, 3L, 4L), Map.of("five", 5L)));
    var jsonAdapter = moshi.adapter(HasIndirectGenerics.class);
    var json = "{\"value\":{\"single\":1,\"list\":[2,3,4],\"map\":{\"five\":5}}}";
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json);
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value);
  }

  public static record IndirectGenerics<T>(T single, List<T> list, Map<String, T> map) {}

  public static record HasIndirectGenerics(IndirectGenerics<Long> value) {}

  @Test
  public void qualifiedValues() throws IOException {
    var adapter = moshi.newBuilder().add(new ColorAdapter()).build().adapter(QualifiedValues.class);
    assertThat(adapter.fromJson("{\"value\":\"#ff0000\"}"))
        .isEqualTo(new QualifiedValues(16711680));
  }

  public static record QualifiedValues(@HexColor int value) {}

  @Retention(RUNTIME)
  @JsonQualifier
  @interface HexColor {}

  /** Converts strings like #ff0000 to the corresponding color ints. */
  public static class ColorAdapter {
    @ToJson
    public String toJson(@HexColor int rgb) {
      return String.format("#%06x", rgb);
    }

    @FromJson
    @HexColor
    public int fromJson(String rgb) {
      return Integer.parseInt(rgb.substring(1), 16);
    }
  }

  public static record GenericBoundedRecord<T extends Number>(T value) {}

  @Test
  public void jsonName() throws IOException {
    var adapter = moshi.adapter(JsonName.class);
    assertThat(adapter.fromJson("{\"actualValue\":3}")).isEqualTo(new JsonName(3));
  }

  public static record JsonName(@Json(name = "actualValue") int value) {}
}
