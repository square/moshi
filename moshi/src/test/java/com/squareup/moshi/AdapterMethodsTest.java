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

import com.squareup.moshi.MoshiTest.Uppercase;
import com.squareup.moshi.MoshiTest.UppercaseAdapterFactory;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okio.ByteString;
import org.junit.Test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
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

  private static final class PointJsonAdapterWithDelegate {
    @FromJson Point fromJson(JsonReader reader, JsonAdapter<Point> delegate) throws IOException {
      reader.beginArray();
      Point value = delegate.fromJson(reader);
      reader.endArray();
      return value;
    }

    @ToJson void toJson(JsonWriter writer, Point value, JsonAdapter<Point> delegate)
        throws IOException {
      writer.beginArray();
      delegate.toJson(writer, value);
      writer.endArray();
    }
  }

  private static final class PointJsonAdapterWithDelegateWithQualifier {
    @FromJson @WithParens Point fromJson(JsonReader reader, @WithParens JsonAdapter<Point> delegate)
        throws IOException {
      reader.beginArray();
      Point value = delegate.fromJson(reader);
      reader.endArray();
      return value;
    }

    @ToJson void toJson(JsonWriter writer, @WithParens Point value,
        @WithParens JsonAdapter<Point> delegate)
        throws IOException {
      writer.beginArray();
      delegate.toJson(writer, value);
      writer.endArray();
    }
  }

  @Test public void toAndFromWithDelegate() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointJsonAdapterWithDelegate())
        .build();
    JsonAdapter<Point> adapter = moshi.adapter(Point.class);
    Point point = new Point(5, 8);
    assertThat(adapter.toJson(point)).isEqualTo("[{\"x\":5,\"y\":8}]");
    assertThat(adapter.fromJson("[{\"x\":5,\"y\":8}]")).isEqualTo(point);
  }

  @Test public void toAndFromWithDelegateWithQualifier() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointJsonAdapterWithDelegateWithQualifier())
        .add(new PointWithParensJsonAdapter())
        .build();
    JsonAdapter<Point> adapter = moshi.adapter(Point.class, WithParens.class);
    Point point = new Point(5, 8);
    assertThat(adapter.toJson(point)).isEqualTo("[\"(5 8)\"]");
    assertThat(adapter.fromJson("[\"(5 8)\"]")).isEqualTo(point);
  }

  @Test public void toAndFromWithIntermediate() throws Exception {
    Moshi moshi = new Moshi.Builder().add(new Object() {
      @FromJson String fromJson(String string) {
        return string.substring(1, string.length() - 1);
      }

      @ToJson String toJson(String value) {
        return "|" + value + "|";
      }
    }).build();
    JsonAdapter<String> adapter = moshi.adapter(String.class);
    assertThat(adapter.toJson("pizza")).isEqualTo("\"|pizza|\"");
    assertThat(adapter.fromJson("\"|pizza|\"")).isEqualTo("pizza");
  }

  @Test public void toAndFromWithIntermediateWithQualifier() throws Exception {
    Moshi moshi = new Moshi.Builder().add(new Object() {
      @FromJson @Uppercase String fromJson(@Uppercase String string) {
        return string.substring(1, string.length() - 1);
      }

      @ToJson @Uppercase String toJson(@Uppercase String value) {
        return "|" + value + "|";
      }
    }).add(new UppercaseAdapterFactory()).build();
    JsonAdapter<String> adapter = moshi.adapter(String.class, Uppercase.class);
    assertThat(adapter.toJson("pizza")).isEqualTo("\"|PIZZA|\"");
    assertThat(adapter.fromJson("\"|pizza|\"")).isEqualTo("PIZZA");
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

  @Test public void emptyAdapters() throws Exception {
    Moshi.Builder builder = new Moshi.Builder();
    try {
      builder.add(new EmptyJsonAdapter()).build();
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage(
          "Expected at least one @ToJson or @FromJson method on "
          + "com.squareup.moshi.AdapterMethodsTest$EmptyJsonAdapter");
    }
  }

  static class EmptyJsonAdapter {
  }

  @Test public void unexpectedSignatureToAdapters() throws Exception {
    Moshi.Builder builder = new Moshi.Builder();
    try {
      builder.add(new UnexpectedSignatureToJsonAdapter()).build();
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Unexpected signature for void "
          + "com.squareup.moshi.AdapterMethodsTest$UnexpectedSignatureToJsonAdapter.pointToJson"
          + "(com.squareup.moshi.AdapterMethodsTest$Point).\n"
          + "@ToJson method signatures may have one of the following structures:\n"
          + "    <any access modifier> void toJson(JsonWriter writer, T value) throws <any>;\n"
          + "    <any access modifier> void toJson(JsonWriter writer, T value,"
          + " JsonAdapter<any> delegate, <any more delegates>) throws <any>;\n"
          + "    <any access modifier> R toJson(T value) throws <any>;\n");
    }
  }

  static class UnexpectedSignatureToJsonAdapter {
    @ToJson void pointToJson(Point point) {
    }
  }

  @Test public void unexpectedSignatureFromAdapters() throws Exception {
    Moshi.Builder builder = new Moshi.Builder();
    try {
      builder.add(new UnexpectedSignatureFromJsonAdapter()).build();
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Unexpected signature for void "
          + "com.squareup.moshi.AdapterMethodsTest$UnexpectedSignatureFromJsonAdapter.pointFromJson"
          + "(java.lang.String).\n"
          + "@FromJson method signatures may have one of the following structures:\n"
          + "    <any access modifier> R fromJson(JsonReader jsonReader) throws <any>;\n"
          + "    <any access modifier> R fromJson(JsonReader jsonReader,"
          + " JsonAdapter<any> delegate, <any more delegates>) throws <any>;\n"
          + "    <any access modifier> R fromJson(T value) throws <any>;\n");
    }
  }

  static class UnexpectedSignatureFromJsonAdapter {
    @FromJson void pointFromJson(String point) {
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

  @Test public void toAndFromNullJsonWithWriterAndReader() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new NullableIntToJsonAdapter())
        .build();
    JsonAdapter<Point> pointAdapter = moshi.adapter(Point.class);
    assertThat(pointAdapter.fromJson("{\"x\":null,\"y\":3}")).isEqualTo(new Point(-1, 3));
    assertThat(pointAdapter.toJson(new Point(-1, 3))).isEqualTo("{\"y\":3}");
  }

  static class NullableIntToJsonAdapter {
    @FromJson int jsonToInt(JsonReader reader) throws IOException {
      if (reader.peek() == JsonReader.Token.NULL) {
        reader.nextNull();
        return -1;
      }
      return reader.nextInt();
    }

    @ToJson void intToJson(JsonWriter writer, int value) throws IOException {
      if (value == -1) {
        writer.nullValue();
      } else {
        writer.value(value);
      }
    }
  }

  @Test public void adapterThrows() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new ExceptionThrowingPointJsonAdapter())
        .build();
    JsonAdapter<Point[]> arrayOfPointAdapter = moshi.adapter(Point[].class).lenient();
    try {
      arrayOfPointAdapter.toJson(new Point[] { null, null, new Point(0, 0) });
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected.getMessage())
          .isEqualTo("java.lang.Exception: pointToJson fail! at $[2]");
    }
    try {
      arrayOfPointAdapter.fromJson("[null,null,[0,0]]");
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected.getMessage())
          .isEqualTo("java.lang.Exception: pointFromJson fail! at $[2]");
    }
  }

  static class ExceptionThrowingPointJsonAdapter {
    @ToJson void pointToJson(JsonWriter writer, Point point) throws Exception {
      if (point != null) throw new Exception("pointToJson fail!");
      writer.nullValue();
    }

    @FromJson Point pointFromJson(JsonReader reader) throws Exception {
      if (reader.peek() == JsonReader.Token.NULL) return reader.nextNull();
      throw new Exception("pointFromJson fail!");
    }
  }

  @Test public void adapterDoesToJsonOnly() throws Exception {
    Object shapeToJsonAdapter = new Object() {
      @ToJson String shapeToJson(Shape shape) {
        throw new AssertionError();
      }
    };

    Moshi toJsonMoshi = new Moshi.Builder()
        .add(shapeToJsonAdapter)
        .build();
    try {
      toJsonMoshi.adapter(Shape.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("No @FromJson adapter for interface "
          + "com.squareup.moshi.AdapterMethodsTest$Shape (with no annotations)");
      assertThat(e).hasCauseExactlyInstanceOf(IllegalArgumentException.class);
      assertThat(e.getCause()).hasMessage("No next JsonAdapter for interface "
          + "com.squareup.moshi.AdapterMethodsTest$Shape (with no annotations)");
    }
  }

  @Test public void adapterDoesFromJsonOnly() throws Exception {
    Object shapeFromJsonAdapter = new Object() {
      @FromJson Shape shapeFromJson(String shape) {
        throw new AssertionError();
      }
    };

    Moshi fromJsonMoshi = new Moshi.Builder()
        .add(shapeFromJsonAdapter)
        .build();
    try {
      fromJsonMoshi.adapter(Shape.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("No @ToJson adapter for interface "
          + "com.squareup.moshi.AdapterMethodsTest$Shape (with no annotations)");
      assertThat(e).hasCauseExactlyInstanceOf(IllegalArgumentException.class);
      assertThat(e.getCause()).hasMessage("No next JsonAdapter for interface "
          + "com.squareup.moshi.AdapterMethodsTest$Shape (with no annotations)");
    }
  }

  /**
   * Unfortunately in some versions of Android the implementations of {@link ParameterizedType}
   * doesn't implement equals and hashCode. Confirm that we work around that.
   */
  @Test public void parameterizedTypeEqualsNotUsed() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new ListOfStringJsonAdapter())
        .build();

    // This class doesn't implement equals() and hashCode() as it should.
    ParameterizedType listOfStringType = brokenParameterizedType(0, List.class, String.class);

    JsonAdapter<List<String>> jsonAdapter = moshi.adapter(listOfStringType);
    assertThat(jsonAdapter.toJson(Arrays.asList("a", "b", "c"))).isEqualTo("\"a|b|c\"");
    assertThat(jsonAdapter.fromJson("\"a|b|c\"")).isEqualTo(Arrays.asList("a", "b", "c"));
  }

  static class ListOfStringJsonAdapter {
    @ToJson String listOfStringToJson(List<String> list) {
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) result.append('|');
        result.append(list.get(i));
      }
      return result.toString();
    }

    @FromJson List<String> listOfStringFromJson(String string) {
      return Arrays.asList(string.split("\\|"));
    }
  }

  /**
   * Even when the types we use to look up JSON adapters are not equal, if they're equivalent they
   * should return the same JsonAdapter instance.
   */
  @Test public void parameterizedTypeCacheKey() throws Exception {
    Moshi moshi = new Moshi.Builder().build();

    Type a = brokenParameterizedType(0, List.class, String.class);
    Type b = brokenParameterizedType(1, List.class, String.class);
    Type c = brokenParameterizedType(2, List.class, String.class);

    assertThat(moshi.adapter(b)).isSameAs(moshi.adapter(a));
    assertThat(moshi.adapter(c)).isSameAs(moshi.adapter(a));
  }

  @Test public void writerAndReaderTakingJsonAdapterParameter() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointWriterAndReaderJsonAdapter())
        .add(new JsonAdapterWithWriterAndReaderTakingJsonAdapterParameter())
        .build();
    JsonAdapter<Line> lineAdapter = moshi.adapter(Line.class);
    Line line = new Line(new Point(5, 8), new Point(3, 2));
    assertThat(lineAdapter.toJson(line)).isEqualTo("[[5,8],[3,2]]");
    assertThat(lineAdapter.fromJson("[[5,8],[3,2]]")).isEqualTo(line);
  }

  static class JsonAdapterWithWriterAndReaderTakingJsonAdapterParameter {
    @ToJson void lineToJson(
        JsonWriter writer, Line line, JsonAdapter<Point> pointAdapter) throws IOException {
      writer.beginArray();
      pointAdapter.toJson(writer, line.a);
      pointAdapter.toJson(writer, line.b);
      writer.endArray();
    }

    @FromJson Line lineFromJson(
        JsonReader reader, JsonAdapter<Point> pointAdapter) throws Exception {
      reader.beginArray();
      Point a = pointAdapter.fromJson(reader);
      Point b = pointAdapter.fromJson(reader);
      reader.endArray();
      return new Line(a, b);
    }
  }

  @Test public void writerAndReaderTakingAnnotatedJsonAdapterParameter() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointWithParensJsonAdapter())
        .add(new JsonAdapterWithWriterAndReaderTakingAnnotatedJsonAdapterParameter())
        .build();
    JsonAdapter<Line> lineAdapter = moshi.adapter(Line.class);
    Line line = new Line(new Point(5, 8), new Point(3, 2));
    assertThat(lineAdapter.toJson(line)).isEqualTo("[\"(5 8)\",\"(3 2)\"]");
    assertThat(lineAdapter.fromJson("[\"(5 8)\",\"(3 2)\"]")).isEqualTo(line);
  }

  static class PointWithParensJsonAdapter{
    @ToJson String pointToJson(@WithParens Point point) throws IOException {
      return String.format("(%s %s)", point.x, point.y);
    }

    @FromJson @WithParens Point pointFromJson(String string) throws Exception {
      Matcher matcher = Pattern.compile("\\((\\d+) (\\d+)\\)").matcher(string);
      if (!matcher.matches()) throw new JsonDataException();
      return new Point(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }
  }

  static class JsonAdapterWithWriterAndReaderTakingAnnotatedJsonAdapterParameter {
    @ToJson void lineToJson(JsonWriter writer, Line line,
        @WithParens JsonAdapter<Point> pointAdapter) throws IOException {
      writer.beginArray();
      pointAdapter.toJson(writer, line.a);
      pointAdapter.toJson(writer, line.b);
      writer.endArray();
    }

    @FromJson Line lineFromJson(
        JsonReader reader, @WithParens JsonAdapter<Point> pointAdapter) throws Exception {
      reader.beginArray();
      Point a = pointAdapter.fromJson(reader);
      Point b = pointAdapter.fromJson(reader);
      reader.endArray();
      return new Line(a, b);
    }
  }

  @Test public void writerAndReaderTakingMultipleJsonAdapterParameters() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PointWriterAndReaderJsonAdapter())
        .add(new PointWithParensJsonAdapter())
        .add(new JsonAdapterWithWriterAndReaderTakingMultipleJsonAdapterParameters())
        .build();
    JsonAdapter<Line> lineAdapter = moshi.adapter(Line.class);
    Line line = new Line(new Point(5, 8), new Point(3, 2));
    assertThat(lineAdapter.toJson(line)).isEqualTo("[[5,8],\"(3 2)\"]");
    assertThat(lineAdapter.fromJson("[[5,8],\"(3 2)\"]")).isEqualTo(line);
  }

  static class JsonAdapterWithWriterAndReaderTakingMultipleJsonAdapterParameters {
    @ToJson void lineToJson(JsonWriter writer, Line line,
        JsonAdapter<Point> aAdapter, @WithParens JsonAdapter<Point> bAdapter) throws IOException {
      writer.beginArray();
      aAdapter.toJson(writer, line.a);
      bAdapter.toJson(writer, line.b);
      writer.endArray();
    }

    @FromJson Line lineFromJson(JsonReader reader,
        JsonAdapter<Point> aAdapter, @WithParens JsonAdapter<Point> bAdapter) throws Exception {
      reader.beginArray();
      Point a = aAdapter.fromJson(reader);
      Point b = bAdapter.fromJson(reader);
      reader.endArray();
      return new Line(a, b);
    }
  }

  @Retention(RUNTIME)
  @JsonQualifier
  public @interface WithParens {
  }

  @Test public void noToJsonAdapterTakingJsonAdapterParameter() throws Exception {
    try {
      new Moshi.Builder().add(new ToJsonAdapterTakingJsonAdapterParameter());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageStartingWith("Unexpected signature");
    }
  }

  static class ToJsonAdapterTakingJsonAdapterParameter {
    @ToJson String lineToJson(Line line, JsonAdapter<Point> pointAdapter) throws IOException {
      throw new AssertionError();
    }
  }

  @Test public void noFromJsonAdapterTakingJsonAdapterParameter() throws Exception {
    try {
      new Moshi.Builder().add(new FromJsonAdapterTakingJsonAdapterParameter());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageStartingWith("Unexpected signature");
    }
  }

  static class FromJsonAdapterTakingJsonAdapterParameter {
    @FromJson Line lineFromJson(String value, JsonAdapter<Point> pointAdapter) throws Exception {
      throw new AssertionError();
    }
  }

  @Test public void adaptedTypeIsEnclosedParameterizedType() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new EnclosedParameterizedTypeJsonAdapter())
        .build();
    JsonAdapter<Box<Point>> boxAdapter = moshi.adapter(Types.newParameterizedTypeWithOwner(
        AdapterMethodsTest.class, Box.class, Point.class));
    Box<Point> box = new Box<>(new Point(5, 8));
    String json = "[{\"x\":5,\"y\":8}]";
    assertThat(boxAdapter.toJson(box)).isEqualTo(json);
    assertThat(boxAdapter.fromJson(json)).isEqualTo(box);
  }

  static class EnclosedParameterizedTypeJsonAdapter {
    @FromJson Box<Point> boxFromJson(List<Point> points) {
      return new Box<>(points.get(0));
    }

    @ToJson List<Point> boxToJson(Box<Point> box) throws Exception {
      return Collections.singletonList(box.data);
    }
  }

  static class Box<T> {
    final T data;

    public Box(T data) {
      this.data = data;
    }

    @Override public boolean equals(Object o) {
      return o instanceof Box && ((Box) o).data.equals(data);
    }

    @Override public int hashCode() {
      return data.hashCode();
    }
  }

  @Test public void genericArrayTypes() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new ByteArrayJsonAdapter())
        .build();
    JsonAdapter<MapOfByteArrays> jsonAdapter = moshi.adapter(MapOfByteArrays.class);

    MapOfByteArrays mapOfByteArrays = new MapOfByteArrays(
        Collections.singletonMap("a", new byte[] { 0, -1}));
    String json = "{\"map\":{\"a\":\"00ff\"}}";

    assertThat(jsonAdapter.toJson(mapOfByteArrays)).isEqualTo(json);
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(mapOfByteArrays);
  }

  static class ByteArrayJsonAdapter {
    @ToJson String byteArrayToJson(byte[] b) {
      return ByteString.of(b).hex();
    }

    @FromJson byte[] byteArrayFromJson(String s) throws Exception {
      return ByteString.decodeHex(s).toByteArray();
    }
  }

  static class MapOfByteArrays {
    final Map<String, byte[]> map;

    public MapOfByteArrays(Map<String, byte[]> map) {
      this.map = map;
    }

    @Override public boolean equals(Object o) {
      return o instanceof MapOfByteArrays && o.toString().equals(toString());
    }

    @Override public int hashCode() {
      return toString().hashCode();
    }

    @Override public String toString() {
      StringBuilder result = new StringBuilder();
      for (Map.Entry<String, byte[]> entry : map.entrySet()) {
        if (result.length() > 0) result.append(", ");
        result.append(entry.getKey())
            .append(":")
            .append(Arrays.toString(entry.getValue()));
      }
      return result.toString();
    }
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

  static class Line {
    final Point a;
    final Point b;

    public Line(Point a, Point b) {
      this.a = a;
      this.b = b;
    }

    @Override public boolean equals(Object o) {
      return o instanceof Line && ((Line) o).a.equals(a) && ((Line) o).b.equals(b);
    }

    @Override public int hashCode() {
      return a.hashCode() * 37 + b.hashCode();
    }
  }

  interface Shape {
    String draw();
  }

  /**
   * Returns a new parameterized type that doesn't implement {@link Object#equals} or {@link
   * Object#hashCode} by value. These implementation defects are consistent with the parameterized
   * type that shipped in some older versions of Android.
   */
  ParameterizedType brokenParameterizedType(
      final int hashCode, final Class<?> rawType, final Type... typeArguments) {
    return new ParameterizedType() {
      @Override public Type[] getActualTypeArguments() {
        return typeArguments;
      }

      @Override public Type getRawType() {
        return rawType;
      }

      @Override public Type getOwnerType() {
        return null;
      }

      @Override public boolean equals(Object other) {
        return other == this;
      }

      @Override public int hashCode() {
        return hashCode;
      }
    };
  }
}
