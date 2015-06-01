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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class AdapterMethodsTest {
  @Test public void toAndFromJsonViaListOfIntegers() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointAsListOfIntegersJsonAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class);
    assertThat(pointAdapter.toJson(new Point(5, 8))).isEqualTo("[5,8]");
    assertThat(pointAdapter.fromJson("[5,8]")).isEqualTo(new Point(5, 8));
  }

  static class PointAsListOfIntegersJsonAdapter {
    @ToJson List<Integer> pointToJson(Point point) {
      return Arrays.asList(point.x, point.y);
    }

    @FromJson Point pointFromJson(List<Integer> o) throws Exception {
      if (o.size() != 2) throw new Exception("Expected 2 elements but was " + o);
      return new Point(o.get(0), o.get(1));
    }
  }

  @Test public void toAndFromJsonWithWriterAndReader() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointWriterAndReaderJsonAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class);
    assertThat(pointAdapter.toJson(new Point(5, 8))).isEqualTo("[5,8]");
    assertThat(pointAdapter.fromJson("[5,8]")).isEqualTo(new Point(5, 8));
  }

  static class PointWriterAndReaderJsonAdapter {
    @ToJson void pointToJson(JsonWriter writer, Point point) throws IOException {
      writer.beginArray();
      writer.value(point.x);
      writer.value(point.y);
      writer.endArray();
    }

    @FromJson Point pointFromJson(JsonReader reader) throws Exception {
      reader.beginArray();
      int x = reader.nextInt();
      int y = reader.nextInt();
      reader.endArray();
      return new Point(x, y);
    }
  }

  @Test public void toJsonOnly() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointAsListOfIntegersToAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class);
    assertThat(pointAdapter.toJson(new Point(5, 8))).isEqualTo("[5,8]");
    assertThat(pointAdapter.fromJson("{\"x\":5,\"y\":8}")).isEqualTo(new Point(5, 8));
  }

  static class PointAsListOfIntegersToAdapter {
    @ToJson List<Integer> pointToJson(Point point) {
      return Arrays.asList(point.x, point.y);
    }
  }

  @Test public void fromJsonOnly() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointAsListOfIntegersFromAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class);
    assertThat(pointAdapter.toJson(new Point(5, 8))).isEqualTo("{\"x\":5,\"y\":8}");
    assertThat(pointAdapter.fromJson("[5,8]")).isEqualTo(new Point(5, 8));
  }

  static class PointAsListOfIntegersFromAdapter {
    @FromJson Point pointFromJson(List<Integer> o) throws Exception {
      if (o.size() != 2) throw new Exception("Expected 2 elements but was " + o);
      return new Point(o.get(0), o.get(1));
    }
  }

  @Test public void multipleLayersOfAdapters() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new MultipleLayersJsonAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class).lenient();
    assertThat(pointAdapter.toJson(new Point(5, 8))).isEqualTo("\"5 8\"");
    assertThat(pointAdapter.fromJson("\"5 8\"")).isEqualTo(new Point(5, 8));
  }

  static class MultipleLayersJsonAdapter {
    @ToJson List<Integer> pointToJson(Point point) {
      return Arrays.asList(point.x, point.y);
    }

    @ToJson String integerListToJson(List<Integer> list) {
      StringBuilder result = new StringBuilder();
      for (Integer i : list) {
        if (result.length() != 0) result.append(" ");
        result.append(i.intValue());
      }
      return result.toString();
    }

    @FromJson Point pointFromJson(List<Integer> o) throws Exception {
      if (o.size() != 2) throw new Exception("Expected 2 elements but was " + o);
      return new Point(o.get(0), o.get(1));
    }

    @FromJson List<Integer> listOfIntegersFromJson(String list) throws Exception {
      List<Integer> result = new ArrayList<>();
      for (String part : list.split(" ")) {
        result.add(Integer.parseInt(part));
      }
      return result;
    }
  }

  @Test public void conflictingToAdapters() throws Exception {
    Moshi.Builder builder = new Moshi.Builder();
    try {
      builder.add(new ConflictingsToJsonAdapter());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains(
          "Conflicting @ToJson methods:", "pointToJson1", "pointToJson2");
    }
  }

  static class ConflictingsToJsonAdapter {
    @ToJson List<Integer> pointToJson1(Point point) {
      throw new AssertionError();
    }

    @ToJson String pointToJson2(Point point) {
      throw new AssertionError();
    }
  }

  @Test public void conflictingFromAdapters() throws Exception {
    Moshi.Builder builder = new Moshi.Builder();
    try {
      builder.add(new ConflictingsFromJsonAdapter());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains(
          "Conflicting @FromJson methods:", "pointFromJson1", "pointFromJson2");
    }
  }

  static class ConflictingsFromJsonAdapter {
    @FromJson Point pointFromJson1(List<Integer> point) {
      throw new AssertionError();
    }

    @FromJson Point pointFromJson2(String point) {
      throw new AssertionError();
    }
  }

  /**
   * Simple adapter methods are not invoked for null values unless they're annotated {@code
   * @Nullable}. (The specific annotation class doesn't matter; just its simple name.)
   */
  @Test public void toAndFromNullNotNullable() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new NotNullablePointAsListOfIntegersJsonAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class).lenient();
    assertThat(pointAdapter.toJson(null)).isEqualTo("null");
    assertThat(pointAdapter.fromJson("null")).isNull();
  }

  static class NotNullablePointAsListOfIntegersJsonAdapter {
    @ToJson List<Integer> pointToJson(Point point) {
      throw new AssertionError();
    }

    @FromJson Point pointFromJson(List<Integer> o) throws Exception {
      throw new AssertionError();
    }
  }

  @Test public void toAndFromNullNullable() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new NullablePointAsListOfIntegersJsonAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class).lenient();
    assertThat(pointAdapter.toJson(null)).isEqualTo("[0,0]");
    assertThat(pointAdapter.fromJson("null")).isEqualTo(new Point(0, 0));
  }

  static class NullablePointAsListOfIntegersJsonAdapter {
    @ToJson List<Integer> pointToJson(@Nullable Point point) {
      return point != null
          ? Arrays.asList(point.x, point.y)
          : Arrays.asList(0, 0);
    }

    @FromJson Point pointFromJson(@Nullable List<Integer> o) throws Exception {
      if (o == null) return new Point(0, 0);
      if (o.size() == 2) return new Point(o.get(0), o.get(1));
      throw new Exception("Expected null or 2 elements but was " + o);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Nullable {
  }

  static class Point {
    final int x;
    final int y;

    public Point(int x, int y) {
      this.x = x;
      this.y = y;
    }

    @Override public boolean equals(Object o) {
      return o instanceof Point && ((Point) o).x == x && ((Point) o).y == y;
    }

    @Override public int hashCode() {
      return x * 37 + y;
    }
  }
}
