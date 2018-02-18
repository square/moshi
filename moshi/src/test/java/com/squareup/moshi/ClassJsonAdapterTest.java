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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SimpleTimeZone;
import okio.Buffer;
import org.junit.Test;

import static com.squareup.moshi.TestUtil.newReader;
import static com.squareup.moshi.internal.Util.NO_ANNOTATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class ClassJsonAdapterTest {
  private final Moshi moshi = new Moshi.Builder().build();

  static class BasicPizza {
    int diameter;
    boolean extraCheese;
  }

  @Test public void basicClassAdapter() throws Exception {
    BasicPizza value = new BasicPizza();
    value.diameter = 13;
    value.extraCheese = true;
    String toJson = toJson(BasicPizza.class, value);
    assertThat(toJson).isEqualTo("{\"diameter\":13,\"extraCheese\":true}");

    BasicPizza fromJson = fromJson(BasicPizza.class, "{\"diameter\":13,\"extraCheese\":true}");
    assertThat(fromJson.diameter).isEqualTo(13);
    assertThat(fromJson.extraCheese).isTrue();
  }

  static class PrivateFieldsPizza {
    private String secretIngredient;
  }

  @Test public void privateFields() throws Exception {
    PrivateFieldsPizza value = new PrivateFieldsPizza();
    value.secretIngredient = "vodka";
    String toJson = toJson(PrivateFieldsPizza.class, value);
    assertThat(toJson).isEqualTo("{\"secretIngredient\":\"vodka\"}");

    PrivateFieldsPizza fromJson = fromJson(
        PrivateFieldsPizza.class, "{\"secretIngredient\":\"vodka\"}");
    assertThat(fromJson.secretIngredient).isEqualTo("vodka");
  }

  static class BasePizza {
    int diameter;
  }

  static class DessertPizza extends BasePizza {
    boolean chocolate;
  }

  @Test public void typeHierarchy() throws Exception {
    DessertPizza value = new DessertPizza();
    value.diameter = 13;
    value.chocolate = true;
    String toJson = toJson(DessertPizza.class, value);
    assertThat(toJson).isEqualTo("{\"chocolate\":true,\"diameter\":13}");

    DessertPizza fromJson = fromJson(DessertPizza.class, "{\"diameter\":13,\"chocolate\":true}");
    assertThat(fromJson.diameter).isEqualTo(13);
    assertThat(fromJson.chocolate).isTrue();
  }

  static class BaseAbcde {
    int d;
    int a;
    int c;
  }

  static class ExtendsBaseAbcde extends BaseAbcde {
    int b;
    int e;
  }

  @Test public void fieldsAreAlphabeticalAcrossFlattenedHierarchy() throws Exception {
    ExtendsBaseAbcde value = new ExtendsBaseAbcde();
    value.a = 4;
    value.b = 5;
    value.c = 6;
    value.d = 7;
    value.e = 8;
    String toJson = toJson(ExtendsBaseAbcde.class, value);
    assertThat(toJson).isEqualTo("{\"a\":4,\"b\":5,\"c\":6,\"d\":7,\"e\":8}");

    ExtendsBaseAbcde fromJson = fromJson(
        ExtendsBaseAbcde.class, "{\"a\":4,\"b\":5,\"c\":6,\"d\":7,\"e\":8}");
    assertThat(fromJson.a).isEqualTo(4);
    assertThat(fromJson.b).isEqualTo(5);
    assertThat(fromJson.c).isEqualTo(6);
    assertThat(fromJson.d).isEqualTo(7);
    assertThat(fromJson.e).isEqualTo(8);
  }

  static class StaticFields {
    static int a = 11;
    int b;
  }

  @Test public void staticFieldsOmitted() throws Exception {
    StaticFields value = new StaticFields();
    value.b = 12;
    String toJson = toJson(StaticFields.class, value);
    assertThat(toJson).isEqualTo("{\"b\":12}");

    StaticFields fromJson = fromJson(StaticFields.class, "{\"a\":13,\"b\":12}");
    assertThat(StaticFields.a).isEqualTo(11); // Unchanged.
    assertThat(fromJson.b).isEqualTo(12);
  }

  static class TransientFields {
    transient int a;
    int b;
  }

  @Test public void transientFieldsOmitted() throws Exception {
    TransientFields value = new TransientFields();
    value.a = 11;
    value.b = 12;
    String toJson = toJson(TransientFields.class, value);
    assertThat(toJson).isEqualTo("{\"b\":12}");

    TransientFields fromJson = fromJson(TransientFields.class, "{\"a\":13,\"b\":12}");
    assertThat(fromJson.a).isEqualTo(0); // Not assigned.
    assertThat(fromJson.b).isEqualTo(12);
  }

  static class BaseA {
    int a;
  }

  static class ExtendsBaseA extends BaseA {
    int a;
  }

  @Test public void fieldNameCollision() throws Exception {
    try {
      ClassJsonAdapter.FACTORY.create(ExtendsBaseA.class, NO_ANNOTATIONS, moshi);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Conflicting fields:\n"
          + "    int com.squareup.moshi.ClassJsonAdapterTest$ExtendsBaseA.a\n"
          + "    int com.squareup.moshi.ClassJsonAdapterTest$BaseA.a");
    }
  }

  static class NameCollision {
    String foo;
    @Json(name = "foo") String bar;
  }

  @Test public void jsonAnnotationNameCollision() throws Exception {
    try {
      ClassJsonAdapter.FACTORY.create(NameCollision.class, NO_ANNOTATIONS, moshi);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Conflicting fields:\n"
          + "    java.lang.String com.squareup.moshi.ClassJsonAdapterTest$NameCollision.foo\n"
          + "    java.lang.String com.squareup.moshi.ClassJsonAdapterTest$NameCollision.bar");
    }
  }

  static class TransientBaseA {
    transient int a;
  }

  static class ExtendsTransientBaseA extends TransientBaseA {
    int a;
  }

  @Test public void fieldNameCollisionWithTransientFieldIsOkay() throws Exception {
    ExtendsTransientBaseA value = new ExtendsTransientBaseA();
    value.a = 11;
    ((TransientBaseA) value).a = 12;
    String toJson = toJson(ExtendsTransientBaseA.class, value);
    assertThat(toJson).isEqualTo("{\"a\":11}");

    ExtendsTransientBaseA fromJson = fromJson(ExtendsTransientBaseA.class, "{\"a\":11}");
    assertThat(fromJson.a).isEqualTo(11);
    assertThat(((TransientBaseA) fromJson).a).isEqualTo(0); // Not assigned.
  }

  static class NoArgConstructor {
    int a;
    int b;

    NoArgConstructor() {
      a = 5;
    }
  }

  @Test public void noArgConstructor() throws Exception {
    NoArgConstructor fromJson = fromJson(NoArgConstructor.class, "{\"b\":8}");
    assertThat(fromJson.a).isEqualTo(5);
    assertThat(fromJson.b).isEqualTo(8);
  }

  static class NoArgConstructorThrowsCheckedException {
    NoArgConstructorThrowsCheckedException() throws Exception {
      throw new Exception("foo");
    }
  }

  @Test public void noArgConstructorThrowsCheckedException() throws Exception {
    try {
      fromJson(NoArgConstructorThrowsCheckedException.class, "{}");
      fail();
    } catch (RuntimeException expected) {
      assertThat(expected.getCause()).hasMessage("foo");
    }
  }

  static class NoArgConstructorThrowsUncheckedException {
    NoArgConstructorThrowsUncheckedException() throws Exception {
      throw new UnsupportedOperationException("foo");
    }
  }

  @Test public void noArgConstructorThrowsUncheckedException() throws Exception {
    try {
      fromJson(NoArgConstructorThrowsUncheckedException.class, "{}");
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected).hasMessage("foo");
    }
  }

  static class NoArgConstructorWithDefaultField {
    int a = 5;
    int b;
  }

  @Test public void noArgConstructorFieldDefaultsHonored() throws Exception {
    NoArgConstructorWithDefaultField fromJson = fromJson(
        NoArgConstructorWithDefaultField.class, "{\"b\":8}");
    assertThat(fromJson.a).isEqualTo(5);
    assertThat(fromJson.b).isEqualTo(8);
  }

  static class MagicConstructor {
    int a;

    public MagicConstructor(Void argument) {
      throw new AssertionError();
    }
  }

  @Test public void magicConstructor() throws Exception {
    MagicConstructor fromJson = fromJson(MagicConstructor.class, "{\"a\":8}");
    assertThat(fromJson.a).isEqualTo(8);
  }

  static class MagicConstructorWithDefaultField {
    int a = 5;
    int b;

    public MagicConstructorWithDefaultField(Void argument) {
      throw new AssertionError();
    }
  }

  @Test public void magicConstructorFieldDefaultsNotHonored() throws Exception {
    MagicConstructorWithDefaultField fromJson = fromJson(
        MagicConstructorWithDefaultField.class, "{\"b\":3}");
    assertThat(fromJson.a).isEqualTo(0); // Surprising! No value is assigned.
    assertThat(fromJson.b).isEqualTo(3);
  }

  static class NullRootObject {
    int a;
  }

  @Test public void nullRootObject() throws Exception {
    String toJson = toJson(PrivateFieldsPizza.class, null);
    assertThat(toJson).isEqualTo("null");

    NullRootObject fromJson = fromJson(NullRootObject.class, "null");
    assertThat(fromJson).isNull();
  }

  static class NullFieldValue {
    String a = "not null";
  }

  @Test public void nullFieldValues() throws Exception {
    NullFieldValue value = new NullFieldValue();
    value.a = null;
    String toJson = toJson(NullFieldValue.class, value);
    assertThat(toJson).isEqualTo("{\"a\":null}");

    NullFieldValue fromJson = fromJson(NullFieldValue.class, "{\"a\":null}");
    assertThat(fromJson.a).isNull();
  }

  class NonStatic {
  }

  @Test public void nonStaticNestedClassNotSupported() throws Exception {
    try {
      ClassJsonAdapter.FACTORY.create(NonStatic.class, NO_ANNOTATIONS, moshi);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Cannot serialize non-static nested class "
          + "com.squareup.moshi.ClassJsonAdapterTest$NonStatic");
    }
  }

  @Test public void anonymousClassNotSupported() throws Exception {
    Comparator<Object> c = new Comparator<Object>() {
      @Override public int compare(Object a, Object b) {
        return 0;
      }
    };
    try {
      ClassJsonAdapter.FACTORY.create(c.getClass(), NO_ANNOTATIONS, moshi);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Cannot serialize anonymous class " + c.getClass().getName());
    }
  }

  @Test public void localClassNotSupported() throws Exception {
    class Local {
    }
    try {
      ClassJsonAdapter.FACTORY.create(Local.class, NO_ANNOTATIONS, moshi);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Cannot serialize local class "
          + "com.squareup.moshi.ClassJsonAdapterTest$1Local");
    }
  }

  interface Interface {
  }

  @Test public void interfaceNotSupported() throws Exception {
    assertThat(ClassJsonAdapter.FACTORY.create(Interface.class, NO_ANNOTATIONS, moshi)).isNull();
  }

  static abstract class Abstract {
  }

  @Test public void abstractClassNotSupported() throws Exception {
    try {
      ClassJsonAdapter.FACTORY.create(Abstract.class, NO_ANNOTATIONS, moshi);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Cannot serialize abstract class "
          + "com.squareup.moshi.ClassJsonAdapterTest$Abstract");
    }
  }

  static class ExtendsPlatformClassWithPrivateField extends SimpleTimeZone {
    int a;

    public ExtendsPlatformClassWithPrivateField() {
      super(0, "FOO");
    }
  }

  @Test public void platformSuperclassPrivateFieldIsExcluded() throws Exception {
    ExtendsPlatformClassWithPrivateField value = new ExtendsPlatformClassWithPrivateField();
    value.a = 4;
    String toJson = toJson(ExtendsPlatformClassWithPrivateField.class, value);
    assertThat(toJson).isEqualTo("{\"a\":4}");

    ExtendsPlatformClassWithPrivateField fromJson = fromJson(
        ExtendsPlatformClassWithPrivateField.class, "{\"a\":4,\"ID\":\"BAR\"}");
    assertThat(fromJson.a).isEqualTo(4);
    assertThat(fromJson.getID()).isEqualTo("FOO");
  }

  static class ExtendsPlatformClassWithProtectedField extends ByteArrayOutputStream {
    int a;

    public ExtendsPlatformClassWithProtectedField() {
      super(2);
    }
  }

  @Test public void platformSuperclassProtectedFieldIsIncluded() throws Exception {
    ExtendsPlatformClassWithProtectedField value = new ExtendsPlatformClassWithProtectedField();
    value.a = 4;
    value.write(5);
    value.write(6);
    String toJson = toJson(ExtendsPlatformClassWithProtectedField.class, value);
    assertThat(toJson).isEqualTo("{\"a\":4,\"buf\":[5,6],\"count\":2}");

    ExtendsPlatformClassWithProtectedField fromJson = fromJson(
        ExtendsPlatformClassWithProtectedField.class, "{\"a\":4,\"buf\":[5,6],\"count\":2}");
    assertThat(fromJson.a).isEqualTo(4);
    assertThat(fromJson.toByteArray()).contains((byte) 5, (byte) 6);
  }

  static class NamedFields {
    @Json(name = "#") List<String> phoneNumbers;
    @Json(name = "@") String emailAddress;
    @Json(name = "zip code") String zipCode;
  }

  @Test public void jsonAnnotationHonored() throws Exception {
    NamedFields value = new NamedFields();
    value.phoneNumbers = Arrays.asList("8005553333", "8005554444");
    value.emailAddress = "cash@square.com";
    value.zipCode = "94043";

    String toJson = toJson(NamedFields.class, value);
    assertThat(toJson).isEqualTo("{"
        + "\"#\":[\"8005553333\",\"8005554444\"],"
        + "\"@\":\"cash@square.com\","
        + "\"zip code\":\"94043\""
        + "}");

    NamedFields fromJson = fromJson(NamedFields.class, "{"
        + "\"#\":[\"8005553333\",\"8005554444\"],"
        + "\"@\":\"cash@square.com\","
        + "\"zip code\":\"94043\""
        + "}");
    assertThat(fromJson.phoneNumbers).isEqualTo(Arrays.asList("8005553333", "8005554444"));
    assertThat(fromJson.emailAddress).isEqualTo("cash@square.com");
    assertThat(fromJson.zipCode).isEqualTo("94043");
  }

  static final class Box<T> {
    final T data;

    Box(T data) {
      this.data = data;
    }
  }

  @Test public void parameterizedType() throws Exception {
    @SuppressWarnings("unchecked")
    JsonAdapter<Box<Integer>> adapter = (JsonAdapter<Box<Integer>>) ClassJsonAdapter.FACTORY.create(
        Types.newParameterizedTypeWithOwner(ClassJsonAdapterTest.class, Box.class, Integer.class),
        NO_ANNOTATIONS, moshi);
    assertThat(adapter.fromJson("{\"data\":5}").data).isEqualTo(5);
    assertThat(adapter.toJson(new Box<>(5))).isEqualTo("{\"data\":5}");
  }

  private <T> String toJson(Class<T> type, T value) throws IOException {
    @SuppressWarnings("unchecked") // Factory.create returns an adapter that matches its argument.
        JsonAdapter<T> jsonAdapter = (JsonAdapter<T>) ClassJsonAdapter.FACTORY.create(
        type, NO_ANNOTATIONS, moshi);

    // Wrap in an array to avoid top-level object warnings without going completely lenient.
    Buffer buffer = new Buffer();
    JsonWriter jsonWriter = JsonWriter.of(buffer);
    jsonWriter.setSerializeNulls(true);
    jsonWriter.beginArray();
    jsonAdapter.toJson(jsonWriter, value);
    jsonWriter.endArray();
    assertThat(buffer.readByte()).isEqualTo((byte) '[');
    String json = buffer.readUtf8(buffer.size() - 1);
    assertThat(buffer.readByte()).isEqualTo((byte) ']');
    return json;
  }

  private <T> T fromJson(Class<T> type, String json) throws IOException {
    @SuppressWarnings("unchecked") // Factory.create returns an adapter that matches its argument.
        JsonAdapter<T> jsonAdapter = (JsonAdapter<T>) ClassJsonAdapter.FACTORY.create(
        type, NO_ANNOTATIONS, moshi);
    // Wrap in an array to avoid top-level object warnings without going completely lenient.
    JsonReader jsonReader = newReader("[" + json + "]");
    jsonReader.beginArray();
    T result = jsonAdapter.fromJson(jsonReader);
    jsonReader.endArray();
    return result;
  }
}
