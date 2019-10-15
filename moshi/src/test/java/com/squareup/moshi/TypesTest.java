/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

import static com.squareup.moshi.internal.Util.canonicalize;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class TypesTest {
  @Retention(RUNTIME) @JsonQualifier @interface TestQualifier {
  }

  @Retention(RUNTIME) @JsonQualifier @interface AnotherTestQualifier {
  }

  @Retention(RUNTIME) @interface TestAnnotation {
  }

  @TestQualifier private Object hasTestQualifier;

  @Test public void nextAnnotationsRequiresJsonAnnotation() throws Exception {
    try {
      Types.nextAnnotations(Collections.<Annotation>emptySet(), TestAnnotation.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage(
          "interface com.squareup.moshi.TypesTest$TestAnnotation is not a JsonQualifier.");
    }
  }

  @Test public void nextAnnotationsDoesNotContainReturnsNull() throws Exception {
    Set<? extends Annotation> annotations =
        Collections.singleton(Types.createJsonQualifierImplementation(AnotherTestQualifier.class));
    assertThat(Types.nextAnnotations(annotations, TestQualifier.class)).isNull();
    assertThat(
        Types.nextAnnotations(Collections.<Annotation>emptySet(), TestQualifier.class)).isNull();
  }

  @Test public void nextAnnotationsReturnsDelegateAnnotations() throws Exception {
    Set<Annotation> annotations = new LinkedHashSet<>(2);
    annotations.add(Types.createJsonQualifierImplementation(TestQualifier.class));
    annotations.add(Types.createJsonQualifierImplementation(AnotherTestQualifier.class));
    Set<AnotherTestQualifier> expected =
        Collections.singleton(Types.createJsonQualifierImplementation(AnotherTestQualifier.class));
    assertThat(Types.nextAnnotations(Collections.unmodifiableSet(annotations), TestQualifier.class))
        .isEqualTo(expected);
  }

  @Test public void newParameterizedType() throws Exception {
    // List<A>. List is a top-level class.
    Type type = Types.newParameterizedType(List.class, A.class);
    assertThat(getFirstTypeArgument(type)).isEqualTo(A.class);

    // A<B>. A is a static inner class.
    type = Types.newParameterizedTypeWithOwner(TypesTest.class, A.class, B.class);
    assertThat(getFirstTypeArgument(type)).isEqualTo(B.class);
  }

  @Test public void newParameterizedType_missingTypeVars() {
    try {
      Types.newParameterizedType(List.class);
      fail("Should have errored due to missing type variable");
    } catch (Exception e) {
      assertThat(e).hasMessageContaining("Missing type arguments");
    }

    try {
      Types.newParameterizedTypeWithOwner(TypesTest.class, A.class);
      fail("Should have errored due to missing type variable");
    } catch (Exception e) {
      assertThat(e).hasMessageContaining("Missing type arguments");
    }
  }

  @Test public void parameterizedTypeWithRequiredOwnerMissing() throws Exception {
    try {
      Types.newParameterizedType(A.class, B.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("unexpected owner type for " + A.class + ": null");
    }
  }

  @Test public void parameterizedTypeWithUnnecessaryOwnerProvided() throws Exception {
    try {
      Types.newParameterizedTypeWithOwner(A.class, List.class, B.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("unexpected owner type for " + List.class + ": " + A.class);
    }
  }

  @Test public void parameterizedTypeWithIncorrectOwnerProvided() throws Exception {
    try {
      Types.newParameterizedTypeWithOwner(A.class, D.class, B.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("unexpected owner type for " + D.class + ": " + A.class);
    }
  }

  @Test public void arrayOf() {
    assertThat(Types.getRawType(Types.arrayOf(int.class))).isEqualTo(int[].class);
    assertThat(Types.getRawType(Types.arrayOf(List.class))).isEqualTo(List[].class);
    assertThat(Types.getRawType(Types.arrayOf(String[].class))).isEqualTo(String[][].class);
  }

  List<? extends CharSequence> listSubtype;
  List<? super String> listSupertype;

  @Test public void subtypeOf() throws Exception {
    Type listOfWildcardType = TypesTest.class.getDeclaredField("listSubtype").getGenericType();
    Type expected = Types.collectionElementType(listOfWildcardType, List.class);
    assertThat(Types.subtypeOf(CharSequence.class)).isEqualTo(expected);
  }

  @Test public void supertypeOf() throws Exception {
    Type listOfWildcardType = TypesTest.class.getDeclaredField("listSupertype").getGenericType();
    Type expected = Types.collectionElementType(listOfWildcardType, List.class);
    assertThat(Types.supertypeOf(String.class)).isEqualTo(expected);
  }

  @Test public void getFirstTypeArgument() throws Exception {
    assertThat(getFirstTypeArgument(A.class)).isNull();

    Type type = Types.newParameterizedTypeWithOwner(TypesTest.class, A.class, B.class, C.class);
    assertThat(getFirstTypeArgument(type)).isEqualTo(B.class);
  }

  @Test public void newParameterizedTypeObjectMethods() throws Exception {
    Type mapOfStringIntegerType = TypesTest.class.getDeclaredField(
        "mapOfStringInteger").getGenericType();
    ParameterizedType newMapType = Types.newParameterizedType(Map.class, String.class, Integer.class);
    assertThat(newMapType).isEqualTo(mapOfStringIntegerType);
    assertThat(newMapType.hashCode()).isEqualTo(mapOfStringIntegerType.hashCode());
    assertThat(newMapType.toString()).isEqualTo(mapOfStringIntegerType.toString());

    Type arrayListOfMapOfStringIntegerType = TypesTest.class.getDeclaredField(
        "arrayListOfMapOfStringInteger").getGenericType();
    ParameterizedType newListType = Types.newParameterizedType(ArrayList.class, newMapType);
    assertThat(newListType).isEqualTo(arrayListOfMapOfStringIntegerType);
    assertThat(newListType.hashCode()).isEqualTo(arrayListOfMapOfStringIntegerType.hashCode());
    assertThat(newListType.toString()).isEqualTo(arrayListOfMapOfStringIntegerType.toString());
  }

  private static final class A {
  }

  private static final class B {
  }

  private static final class C {
  }

  private static final class D<T> {
  }

  /**
   * Given a parameterized type {@code A<B, C>}, returns B. If the specified type is not a generic
   * type, returns null.
   */
  public static Type getFirstTypeArgument(Type type) throws Exception {
    if (!(type instanceof ParameterizedType)) return null;
    ParameterizedType ptype = (ParameterizedType) type;
    Type[] actualTypeArguments = ptype.getActualTypeArguments();
    if (actualTypeArguments.length == 0) return null;
    return canonicalize(actualTypeArguments[0]);
  }

  Map<String, Integer> mapOfStringInteger;
  Map<String, Integer>[] arrayOfMapOfStringInteger;
  ArrayList<Map<String, Integer>> arrayListOfMapOfStringInteger;
  interface StringIntegerMap extends Map<String, Integer> {
  }

  @Test public void arrayComponentType() throws Exception {
    assertThat(Types.arrayComponentType(String[][].class)).isEqualTo(String[].class);
    assertThat(Types.arrayComponentType(String[].class)).isEqualTo(String.class);

    Type arrayOfMapOfStringIntegerType = TypesTest.class.getDeclaredField(
        "arrayOfMapOfStringInteger").getGenericType();
    Type mapOfStringIntegerType = TypesTest.class.getDeclaredField(
        "mapOfStringInteger").getGenericType();
    assertThat(Types.arrayComponentType(arrayOfMapOfStringIntegerType))
        .isEqualTo(mapOfStringIntegerType);
  }

  @Test public void collectionElementType() throws Exception {
    Type arrayListOfMapOfStringIntegerType = TypesTest.class.getDeclaredField(
        "arrayListOfMapOfStringInteger").getGenericType();
    Type mapOfStringIntegerType = TypesTest.class.getDeclaredField(
        "mapOfStringInteger").getGenericType();
    assertThat(Types.collectionElementType(arrayListOfMapOfStringIntegerType, List.class))
        .isEqualTo(mapOfStringIntegerType);
  }

  @Test public void mapKeyAndValueTypes() throws Exception {
    Type mapOfStringIntegerType = TypesTest.class.getDeclaredField(
        "mapOfStringInteger").getGenericType();
    assertThat(Types.mapKeyAndValueTypes(mapOfStringIntegerType, Map.class))
        .containsExactly(String.class, Integer.class);
  }

  @Test public void propertiesTypes() throws Exception {
    assertThat(Types.mapKeyAndValueTypes(Properties.class, Properties.class))
        .containsExactly(String.class, String.class);
  }

  @Test public void fixedVariablesTypes() throws Exception {
    assertThat(Types.mapKeyAndValueTypes(StringIntegerMap.class, StringIntegerMap.class))
        .containsExactly(String.class, Integer.class);
  }

  @SuppressWarnings("GetClassOnAnnotation") // Explicitly checking for proxy implementation.
  @Test public void createJsonQualifierImplementation() throws Exception {
    TestQualifier actual = Types.createJsonQualifierImplementation(TestQualifier.class);
    TestQualifier expected =
        (TestQualifier) TypesTest.class.getDeclaredField("hasTestQualifier").getAnnotations()[0];
    assertThat(actual.annotationType()).isEqualTo(TestQualifier.class);
    assertThat(actual).isEqualTo(expected);
    assertThat(actual).isNotEqualTo(null);
    assertThat(actual.hashCode()).isEqualTo(expected.hashCode());
    assertThat(actual.getClass()).isNotEqualTo(TestQualifier.class);
  }

  @Test public void arrayEqualsGenericTypeArray() {
    assertThat(Types.equals(int[].class, Types.arrayOf(int.class))).isTrue();
    assertThat(Types.equals(Types.arrayOf(int.class), int[].class)).isTrue();
    assertThat(Types.equals(String[].class, Types.arrayOf(String.class))).isTrue();
    assertThat(Types.equals(Types.arrayOf(String.class), String[].class)).isTrue();
  }

  @Test public void parameterizedAndWildcardTypesCannotHavePrimitiveArguments() throws Exception {
    try {
      Types.newParameterizedType(List.class, int.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Unexpected primitive int. Use the boxed type.");
    }
    try {
      Types.subtypeOf(byte.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Unexpected primitive byte. Use the boxed type.");
    }
    try {
      Types.subtypeOf(boolean.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Unexpected primitive boolean. Use the boxed type.");
    }
  }

  @Test public void getFieldJsonQualifierAnnotations_privateFieldTest() {
    Set<? extends Annotation> annotations = Types.getFieldJsonQualifierAnnotations(ClassWithAnnotatedFields.class,
        "privateField");

    assertThat(annotations).hasSize(1);
    assertThat(annotations.iterator().next()).isInstanceOf(FieldAnnotation.class);
  }

  @Test public void getFieldJsonQualifierAnnotations_publicFieldTest() {
    Set<? extends Annotation> annotations = Types.getFieldJsonQualifierAnnotations(ClassWithAnnotatedFields.class,
        "publicField");

    assertThat(annotations).hasSize(1);
    assertThat(annotations.iterator().next()).isInstanceOf(FieldAnnotation.class);
  }

  @Test public void getFieldJsonQualifierAnnotations_unannotatedTest() {
    Set<? extends Annotation> annotations = Types.getFieldJsonQualifierAnnotations(ClassWithAnnotatedFields.class,
        "unannotatedField");

    assertThat(annotations).hasSize(0);
  }

  @Test public void generatedJsonAdapterName_strings() {
    assertThat(Types.generatedJsonAdapterName("com.foo.Test")).isEqualTo("com.foo.TestJsonAdapter");
    assertThat(Types.generatedJsonAdapterName("com.foo.Test$Bar")).isEqualTo("com.foo.Test_BarJsonAdapter");
  }

  @Test public void generatedJsonAdapterName_class() {
    assertThat(Types.generatedJsonAdapterName(TestJsonClass.class)).isEqualTo("com.squareup.moshi.TypesTest_TestJsonClassJsonAdapter");
  }

  @Test public void generatedJsonAdapterName_class_missingJsonClass() {
    try {
      Types.generatedJsonAdapterName(TestNonJsonClass.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageContaining("Class does not have a JsonClass annotation");
    }
  }

  //
  // Regression tests for https://github.com/square/moshi/issues/338
  //
  // Adapted from https://github.com/google/gson/pull/1128
  //

  private static final class RecursiveTypeVars<T> {
    RecursiveTypeVars<? super T> superType;
  }

  @Test public void recursiveTypeVariablesResolve() {
    JsonAdapter<RecursiveTypeVars<String>> adapter = new Moshi.Builder().build().adapter(Types
        .newParameterizedTypeWithOwner(TypesTest.class, RecursiveTypeVars.class, String.class));
    assertThat(adapter).isNotNull();
  }

  @Test public void recursiveTypeVariablesResolve1() {
    JsonAdapter<TestType> adapter = new Moshi.Builder().build().adapter(TestType.class);
    assertThat(adapter).isNotNull();
  }

  @Test public void recursiveTypeVariablesResolve2() {
    JsonAdapter<TestType2> adapter = new Moshi.Builder().build().adapter(TestType2.class);
    assertThat(adapter).isNotNull();
  }

  private static class TestType<X> {
    TestType<? super X> superType;
  }

  private static class TestType2<X, Y> {
    TestType2<? super Y, ? super X> superReversedType;
  }

  @JsonClass(generateAdapter = false)
  static class TestJsonClass {

  }

  static class TestNonJsonClass {

  }

  @JsonQualifier
  @Target(FIELD)
  @Retention(RUNTIME)
  @interface FieldAnnotation {

  }

  @Target(FIELD)
  @Retention(RUNTIME)
  @interface NoQualifierAnnotation {

  }

  static class ClassWithAnnotatedFields {
    @FieldAnnotation
    @NoQualifierAnnotation
    private final int privateField = 0;

    @FieldAnnotation
    @NoQualifierAnnotation
    public final int publicField = 0;

    private final int unannotatedField = 0;
  }
}
