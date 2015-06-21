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
import java.util.Date;
import org.junit.Test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class JsonQualifiersTest {
  @Test public void builtInTypes() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new BuiltInTypesJsonAdapter())
        .build();
    JsonAdapter<StringAndFooString> adapter = moshi.adapter(StringAndFooString.class);

    StringAndFooString v1 = new StringAndFooString();
    v1.a = "aa";
    v1.b = "bar";
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":\"aa\",\"b\":\"foobar\"}");

    StringAndFooString v2 = adapter.fromJson("{\"a\":\"aa\",\"b\":\"foobar\"}");
    assertThat(v2.a).isEqualTo("aa");
    assertThat(v2.b).isEqualTo("bar");
  }

  static class BuiltInTypesJsonAdapter {
    @ToJson String fooPrefixStringToString(@FooPrefix String s) {
      return "foo" + s;
    }

    @FromJson @FooPrefix String fooPrefixStringFromString(String s) throws Exception {
      if (!s.startsWith("foo")) throw new JsonDataException();
      return s.substring(3);
    }
  }

  @Test public void readerWriterJsonAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new ReaderWriterJsonAdapter())
        .build();
    JsonAdapter<StringAndFooString> adapter = moshi.adapter(StringAndFooString.class);

    StringAndFooString v1 = new StringAndFooString();
    v1.a = "aa";
    v1.b = "bar";
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":\"aa\",\"b\":\"foobar\"}");

    StringAndFooString v2 = adapter.fromJson("{\"a\":\"aa\",\"b\":\"foobar\"}");
    assertThat(v2.a).isEqualTo("aa");
    assertThat(v2.b).isEqualTo("bar");
  }

  static class ReaderWriterJsonAdapter {
    @ToJson void fooPrefixStringToString(JsonWriter jsonWriter, @FooPrefix String s)
        throws IOException {
      jsonWriter.value("foo" + s);
    }

    @FromJson @FooPrefix String fooPrefixStringFromString(JsonReader reader) throws Exception {
      String s = reader.nextString();
      if (!s.startsWith("foo")) throw new JsonDataException();
      return s.substring(3);
    }
  }

  /** Fields with this annotation get "foo" as a prefix in the JSON. */
  @Retention(RUNTIME)
  @JsonQualifier
  public @interface FooPrefix {
  }

  /** Fields with this annotation get "baz" as a suffix in the JSON. */
  @Retention(RUNTIME)
  @JsonQualifier
  public @interface BazSuffix {
  }

  static class StringAndFooString {
    String a;
    @FooPrefix String b;
  }

  static class StringAndFooBazString {
    String a;
    @FooPrefix @BazSuffix String b;
  }

  @Test public void builtInTypesWithMultipleAnnotations() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new BuiltInTypesWithMultipleAnnotationsJsonAdapter())
        .build();
    JsonAdapter<StringAndFooBazString> adapter = moshi.adapter(StringAndFooBazString.class);

    StringAndFooBazString v1 = new StringAndFooBazString();
    v1.a = "aa";
    v1.b = "bar";
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");

    StringAndFooBazString v2 = adapter.fromJson("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");
    assertThat(v2.a).isEqualTo("aa");
    assertThat(v2.b).isEqualTo("bar");
  }

  static class BuiltInTypesWithMultipleAnnotationsJsonAdapter {
    @ToJson String fooPrefixAndBazSuffixStringToString(@FooPrefix @BazSuffix String s) {
      return "foo" + s + "baz";
    }

    @FromJson @FooPrefix @BazSuffix String fooPrefixAndBazSuffixStringFromString(
        String s) throws Exception {
      if (!s.startsWith("foo")) throw new JsonDataException();
      if (!s.endsWith("baz")) throw new JsonDataException();
      return s.substring(3, s.length() - 3);
    }
  }

  @Test public void readerWriterWithMultipleAnnotations() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new ReaderWriterWithMultipleAnnotationsJsonAdapter())
        .build();
    JsonAdapter<StringAndFooBazString> adapter = moshi.adapter(StringAndFooBazString.class);

    StringAndFooBazString v1 = new StringAndFooBazString();
    v1.a = "aa";
    v1.b = "bar";
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");

    StringAndFooBazString v2 = adapter.fromJson("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");
    assertThat(v2.a).isEqualTo("aa");
    assertThat(v2.b).isEqualTo("bar");
  }

  static class ReaderWriterWithMultipleAnnotationsJsonAdapter {
    @ToJson void fooPrefixAndBazSuffixStringToString(
        JsonWriter jsonWriter, @FooPrefix @BazSuffix String s) throws IOException {
      jsonWriter.value("foo" + s + "baz");
    }

    @FromJson @FooPrefix @BazSuffix String fooPrefixAndBazSuffixStringFromString(
        JsonReader reader) throws Exception {
      String s = reader.nextString();
      if (!s.startsWith("foo")) throw new JsonDataException();
      if (!s.endsWith("baz")) throw new JsonDataException();
      return s.substring(3, s.length() - 3);
    }
  }

  @Test public void basicTypesAnnotationDelegating() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new BuiltInTypesDelegatingJsonAdapter())
        .add(new BuiltInTypesJsonAdapter())
        .build();
    JsonAdapter<StringAndFooBazString> adapter = moshi.adapter(StringAndFooBazString.class);

    StringAndFooBazString v1 = new StringAndFooBazString();
    v1.a = "aa";
    v1.b = "bar";
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");

    StringAndFooBazString v2 = adapter.fromJson("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");
    assertThat(v2.a).isEqualTo("aa");
    assertThat(v2.b).isEqualTo("bar");
  }

  static class BuiltInTypesDelegatingJsonAdapter {
    @ToJson @FooPrefix String fooPrefixAndBazSuffixStringToString(@FooPrefix @BazSuffix String s) {
      return s + "baz";
    }

    @FromJson @FooPrefix @BazSuffix String fooPrefixAndBazSuffixStringFromString(
        @FooPrefix String s) throws Exception {
      if (!s.endsWith("baz")) throw new JsonDataException();
      return s.substring(0, s.length() - 3);
    }
  }

  @Test public void readerWriterAnnotationDelegating() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new BuiltInTypesDelegatingJsonAdapter())
        .add(new ReaderWriterJsonAdapter())
        .build();
    JsonAdapter<StringAndFooBazString> adapter = moshi.adapter(StringAndFooBazString.class);

    StringAndFooBazString v1 = new StringAndFooBazString();
    v1.a = "aa";
    v1.b = "bar";
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");

    StringAndFooBazString v2 = adapter.fromJson("{\"a\":\"aa\",\"b\":\"foobarbaz\"}");
    assertThat(v2.a).isEqualTo("aa");
    assertThat(v2.b).isEqualTo("bar");
  }

  @Test public void manualJsonAdapter() throws Exception {
    JsonAdapter<String> fooPrefixAdapter = new JsonAdapter<String>() {
      @Override public String fromJson(JsonReader reader) throws IOException {
        String s = reader.nextString();
        if (!s.startsWith("foo")) throw new JsonDataException();
        return s.substring(3);
      }

      @Override public void toJson(JsonWriter writer, String value) throws IOException {
        writer.value("foo" + value);
      }
    };

    Moshi moshi = new Moshi.Builder()
        .add(String.class, FooPrefix.class, fooPrefixAdapter)
        .build();
    JsonAdapter<StringAndFooString> adapter = moshi.adapter(StringAndFooString.class);

    StringAndFooString v1 = new StringAndFooString();
    v1.a = "aa";
    v1.b = "bar";
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":\"aa\",\"b\":\"foobar\"}");

    StringAndFooString v2 = adapter.fromJson("{\"a\":\"aa\",\"b\":\"foobar\"}");
    assertThat(v2.a).isEqualTo("aa");
    assertThat(v2.b).isEqualTo("bar");
  }

  @Test public void noJsonAdapterForAnnotatedType() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    try {
      moshi.adapter(StringAndFooString.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void annotationWithoutJsonQualifierIsIgnoredByAdapterMethods() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new MissingJsonQualifierJsonAdapter())
        .build();
    JsonAdapter<DateAndMillisDate> adapter = moshi.adapter(DateAndMillisDate.class);

    DateAndMillisDate v1 = new DateAndMillisDate();
    v1.a = new Date(5);
    v1.b = new Date(7);
    assertThat(adapter.toJson(v1)).isEqualTo("{\"a\":5,\"b\":7}");

    DateAndMillisDate v2 = adapter.fromJson("{\"a\":5,\"b\":7}");
    assertThat(v2.a).isEqualTo(new Date(5));
    assertThat(v2.b).isEqualTo(new Date(7));
  }

  /** Despite the fact that these methods are annotated, they match all dates. */
  static class MissingJsonQualifierJsonAdapter {
    @ToJson long dateToJson(@Millis Date d) {
      return d.getTime();
    }

    @FromJson @Millis Date jsonToDate(long value) throws Exception {
      return new Date(value);
    }
  }

  /** This annotation does nothing. */
  @Retention(RUNTIME)
  public @interface Millis {
  }

  static class DateAndMillisDate {
    Date a;
    @Millis Date b;
  }

  @Test public void annotationWithoutJsonQualifierIsRejectedOnRegistration() throws Exception {
    JsonAdapter<Date> jsonAdapter = new JsonAdapter<Date>() {
      @Override public Date fromJson(JsonReader reader) throws IOException {
        throw new AssertionError();
      }

      @Override public void toJson(JsonWriter writer, Date value) throws IOException {
        throw new AssertionError();
      }
    };

    try {
      new Moshi.Builder().add(Date.class, Millis.class, jsonAdapter);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("interface com.squareup.moshi.JsonQualifiersTest$Millis "
          + "does not have @JsonQualifier");
    }
  }

  @Test public void annotationsConflict() throws Exception {
    try {
      new Moshi.Builder().add(new AnnotationsConflictJsonAdapter());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageContaining("Conflicting @ToJson methods");
    }
  }

  static class AnnotationsConflictJsonAdapter {
    @ToJson String fooPrefixStringToString(@FooPrefix String s) {
      return "foo" + s;
    }

    @ToJson String fooPrefixStringToString2(@FooPrefix String s) {
      return "foo" + s;
    }
  }

  @Test public void toButNoFromJson() throws Exception {
    // Building it is okay.
    Moshi moshi = new Moshi.Builder()
        .add(new ToButNoFromJsonAdapter())
        .build();

    try {
      moshi.adapter(StringAndFooString.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("No @FromJson adapter for class java.lang.String "
          + "annotated [@com.squareup.moshi.JsonQualifiersTest$FooPrefix()]");
    }
  }

  static class ToButNoFromJsonAdapter {
    @ToJson String fooPrefixStringToString(@FooPrefix String s) {
      return "foo" + s;
    }
  }

  @Test public void fromButNoToJson() throws Exception {
    // Building it is okay.
    Moshi moshi = new Moshi.Builder()
        .add(new FromButNoToJsonAdapter())
        .build();

    try {
      moshi.adapter(StringAndFooString.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("No @ToJson adapter for class java.lang.String "
          + "annotated [@com.squareup.moshi.JsonQualifiersTest$FooPrefix()]");
    }
  }

  static class FromButNoToJsonAdapter {
    @FromJson @FooPrefix String fooPrefixStringFromString(String s) throws Exception {
      if (!s.startsWith("foo")) throw new JsonDataException();
      return s.substring(3);
    }
  }
}
