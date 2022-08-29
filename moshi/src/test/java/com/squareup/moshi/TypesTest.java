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

import static com.squareup.moshi.internal.Util.canonicalize;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

public final class TypesTest {
  @Retention(RUNTIME)
  @JsonQualifier
  @interface TestQualifier {}

  @Retention(RUNTIME)
  @JsonQualifier
  @interface AnotherTestQualifier {}

  @Retention(RUNTIME)
  @interface TestAnnotation {}

  @TestQualifier private Object hasTestQualifier;

  @Test
  public void nextAnnotationsRequiresJsonAnnotation() {
    try {
      Types.nextAnnotations(Collections.emptySet(), TestAnnotation.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains(
                  "interface com.squareup.moshi.TypesTest$TestAnnotation is not a JsonQualifier."));
    }
  }

  @Test
  public void nextAnnotationsDoesNotContainReturnsNull() {
    Set<? extends Annotation> annotations =
        Collections.singleton(Types.createJsonQualifierImplementation(AnotherTestQualifier.class));
    assertNull(Types.nextAnnotations(annotations, TestQualifier.class));
    assertNull(Types.nextAnnotations(Collections.emptySet(), TestQualifier.class));
  }

  @Test
  public void nextAnnotationsReturnsDelegateAnnotations() {
    Set<Annotation> annotations = new LinkedHashSet<>(2);
    annotations.add(Types.createJsonQualifierImplementation(TestQualifier.class));
    annotations.add(Types.createJsonQualifierImplementation(AnotherTestQualifier.class));
    Set<AnotherTestQualifier> expected =
        Collections.singleton(Types.createJsonQualifierImplementation(AnotherTestQualifier.class));
    assertEquals(
        Types.nextAnnotations(Collections.unmodifiableSet(annotations), TestQualifier.class),
        expected);
  }

  @Test
  public void newParameterizedType() throws Exception {
    // List<A>. List is a top-level class.
    Type type = Types.newParameterizedType(List.class, A.class);
    assertEquals(getFirstTypeArgument(type), A.class);

    // A<B>. A is a static inner class.
    type = Types.newParameterizedTypeWithOwner(TypesTest.class, A.class, B.class);
    assertEquals(getFirstTypeArgument(type), B.class);
  }

  @Test
  public void newParameterizedType_missingTypeVars() {
    try {
      Types.newParameterizedType(List.class);
      fail("Should have errored due to missing type variable");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Missing type arguments"));
    }

    try {
      Types.newParameterizedTypeWithOwner(TypesTest.class, A.class);
      fail("Should have errored due to missing type variable");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Missing type arguments"));
    }
  }

  @Test
  public void parameterizedTypeWithRequiredOwnerMissing() {
    try {
      Types.newParameterizedType(A.class, B.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("unexpected owner type for " + A.class + ": null"));
    }
  }

  @Test
  public void parameterizedTypeWithUnnecessaryOwnerProvided() {
    try {
      Types.newParameterizedTypeWithOwner(A.class, List.class, B.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected
              .getMessage()
              .contains("unexpected owner type for " + List.class + ": " + A.class));
    }
  }

  @Test
  public void parameterizedTypeWithIncorrectOwnerProvided() {
    try {
      Types.newParameterizedTypeWithOwner(A.class, D.class, B.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected.getMessage().contains("unexpected owner type for " + D.class + ": " + A.class));
    }
  }

  @Test
  public void arrayOf() {
    assertEquals(Types.getRawType(Types.arrayOf(int.class)), int[].class);
    assertEquals(Types.getRawType(Types.arrayOf(List.class)), List[].class);
    assertEquals(Types.getRawType(Types.arrayOf(String[].class)), String[][].class);
  }

  List<? extends CharSequence> listSubtype;
  List<? super String> listSupertype;

  @Test
  public void subtypeOf() throws Exception {
    Type listOfWildcardType = TypesTest.class.getDeclaredField("listSubtype").getGenericType();
    Type expected = Types.collectionElementType(listOfWildcardType, List.class);
    assertEquals(Types.subtypeOf(CharSequence.class), expected);
  }

  @Test
  public void supertypeOf() throws Exception {
    Type listOfWildcardType = TypesTest.class.getDeclaredField("listSupertype").getGenericType();
    Type expected = Types.collectionElementType(listOfWildcardType, List.class);
    assertEquals(Types.supertypeOf(String.class), expected);
  }

  @Test
  public void getFirstTypeArgument() throws Exception {
    assertNull(getFirstTypeArgument(A.class));

    Type type = Types.newParameterizedTypeWithOwner(TypesTest.class, A.class, B.class, C.class);
    assertEquals(getFirstTypeArgument(type), B.class);
  }

  @Test
  public void newParameterizedTypeObjectMethods() throws Exception {
    Type mapOfStringIntegerType =
        TypesTest.class.getDeclaredField("mapOfStringInteger").getGenericType();
    ParameterizedType newMapType =
        Types.newParameterizedType(Map.class, String.class, Integer.class);
    assertEquals(newMapType, mapOfStringIntegerType);
    assertEquals(newMapType.hashCode(), mapOfStringIntegerType.hashCode());
    assertEquals(newMapType.toString(), mapOfStringIntegerType.toString());

    Type arrayListOfMapOfStringIntegerType =
        TypesTest.class.getDeclaredField("arrayListOfMapOfStringInteger").getGenericType();
    ParameterizedType newListType = Types.newParameterizedType(ArrayList.class, newMapType);
    assertEquals(newListType, arrayListOfMapOfStringIntegerType);
    assertEquals(newListType.hashCode(), arrayListOfMapOfStringIntegerType.hashCode());
    assertEquals(newListType.toString(), arrayListOfMapOfStringIntegerType.toString());
  }

  private static final class A {}

  private static final class B {}

  private static final class C {}

  private static final class D<T> {}

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

  interface StringIntegerMap extends Map<String, Integer> {}

  @Test
  public void arrayComponentType() throws Exception {
    assertEquals(Types.arrayComponentType(String[][].class), String[].class);
    assertEquals(Types.arrayComponentType(String[].class), String.class);

    Type arrayOfMapOfStringIntegerType =
        TypesTest.class.getDeclaredField("arrayOfMapOfStringInteger").getGenericType();
    Type mapOfStringIntegerType =
        TypesTest.class.getDeclaredField("mapOfStringInteger").getGenericType();
    assertEquals(Types.arrayComponentType(arrayOfMapOfStringIntegerType), mapOfStringIntegerType);
  }

  @Test
  public void collectionElementType() throws Exception {
    Type arrayListOfMapOfStringIntegerType =
        TypesTest.class.getDeclaredField("arrayListOfMapOfStringInteger").getGenericType();
    Type mapOfStringIntegerType =
        TypesTest.class.getDeclaredField("mapOfStringInteger").getGenericType();
    assertEquals(
        Types.collectionElementType(arrayListOfMapOfStringIntegerType, List.class),
        mapOfStringIntegerType);
  }

  @Test
  public void mapKeyAndValueTypes() throws Exception {
    Type mapOfStringIntegerType =
        TypesTest.class.getDeclaredField("mapOfStringInteger").getGenericType();
    Type[] types = Types.mapKeyAndValueTypes(mapOfStringIntegerType, Map.class);

    assertEquals(types[0], String.class);
    assertEquals(types[1], Integer.class);
  }

  @Test
  public void propertiesTypes() {
    Type[] types = Types.mapKeyAndValueTypes(Properties.class, Properties.class);

    assertEquals(types[0], String.class);
    assertEquals(types[1], String.class);
  }

  @Test
  public void fixedVariablesTypes() {
    Type[] types = Types.mapKeyAndValueTypes(StringIntegerMap.class, StringIntegerMap.class);

    assertEquals(types[0], String.class);
    assertEquals(types[1], Integer.class);
  }

  @SuppressWarnings("GetClassOnAnnotation") // Explicitly checking for proxy implementation.
  @Test
  public void createJsonQualifierImplementation() throws Exception {
    TestQualifier actual = Types.createJsonQualifierImplementation(TestQualifier.class);
    TestQualifier expected =
        (TestQualifier) TypesTest.class.getDeclaredField("hasTestQualifier").getAnnotations()[0];
    assertEquals(actual.annotationType(), TestQualifier.class);
    assertEquals(actual, expected);
    assertNotNull(actual);
    assertEquals(actual.hashCode(), expected.hashCode());
    assertNotEquals(actual.getClass(), TestQualifier.class);
  }

  @Test
  public void arrayEqualsGenericTypeArray() {
    assertTrue(Types.equals(int[].class, Types.arrayOf(int.class)));
    assertTrue(Types.equals(Types.arrayOf(int.class), int[].class));
    assertTrue(Types.equals(String[].class, Types.arrayOf(String.class)));
    assertTrue(Types.equals(Types.arrayOf(String.class), String[].class));
  }

  @Test
  public void parameterizedAndWildcardTypesCannotHavePrimitiveArguments() {
    try {
      Types.newParameterizedType(List.class, int.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Unexpected primitive int. Use the boxed type."));
    }
    try {
      Types.subtypeOf(byte.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Unexpected primitive byte. Use the boxed type."));
    }
    try {
      Types.subtypeOf(boolean.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertTrue(
          expected.getMessage().contains("Unexpected primitive boolean. Use the boxed type."));
    }
  }

  @Test
  public void getFieldJsonQualifierAnnotations_privateFieldTest() {
    Set<? extends Annotation> annotations =
        Types.getFieldJsonQualifierAnnotations(ClassWithAnnotatedFields.class, "privateField");

    assertEquals(1, annotations.size());
    assertTrue(annotations.iterator().next() instanceof FieldAnnotation);
  }

  @Test
  public void getFieldJsonQualifierAnnotations_publicFieldTest() {
    Set<? extends Annotation> annotations =
        Types.getFieldJsonQualifierAnnotations(ClassWithAnnotatedFields.class, "publicField");

    assertEquals(1, annotations.size());
    assertTrue(annotations.iterator().next() instanceof FieldAnnotation);
  }

  @Test
  public void getFieldJsonQualifierAnnotations_unannotatedTest() {
    Set<? extends Annotation> annotations =
        Types.getFieldJsonQualifierAnnotations(ClassWithAnnotatedFields.class, "unannotatedField");

    assertEquals(0, annotations.size());
  }

  @Test
  public void generatedJsonAdapterName_strings() {
    assertEquals(Types.generatedJsonAdapterName("com.foo.Test"), "com.foo.TestJsonAdapter");
    assertEquals(Types.generatedJsonAdapterName("com.foo.Test$Bar"), "com.foo.Test_BarJsonAdapter");
  }

  @Test
  public void generatedJsonAdapterName_class() {
    assertEquals(
        Types.generatedJsonAdapterName(TestJsonClass.class),
        "com.squareup.moshi.TypesTest_TestJsonClassJsonAdapter");
  }

  @Test
  public void generatedJsonAdapterName_class_missingJsonClass() {
    try {
      Types.generatedJsonAdapterName(TestNonJsonClass.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Class does not have a JsonClass annotation"));
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

  @Test
  public void recursiveTypeVariablesResolve() {
    JsonAdapter<RecursiveTypeVars<String>> adapter =
        new Moshi.Builder()
            .build()
            .adapter(
                Types.newParameterizedTypeWithOwner(
                    TypesTest.class, RecursiveTypeVars.class, String.class));
    assertNotNull(adapter);
  }

  @Test
  public void recursiveTypeVariablesResolve1() {
    JsonAdapter<TestType> adapter = new Moshi.Builder().build().adapter(TestType.class);
    assertNotNull(adapter);
  }

  @Test
  public void recursiveTypeVariablesResolve2() {
    JsonAdapter<TestType2> adapter = new Moshi.Builder().build().adapter(TestType2.class);
    assertNotNull(adapter);
  }

  private static class TestType<X> {
    TestType<? super X> superType;
  }

  private static class TestType2<X, Y> {
    TestType2<? super Y, ? super X> superReversedType;
  }

  @JsonClass(generateAdapter = false)
  static class TestJsonClass {}

  static class TestNonJsonClass {}

  @JsonQualifier
  @Target(FIELD)
  @Retention(RUNTIME)
  @interface FieldAnnotation {}

  @Target(FIELD)
  @Retention(RUNTIME)
  @interface NoQualifierAnnotation {}

  static class ClassWithAnnotatedFields {
    @FieldAnnotation @NoQualifierAnnotation private final int privateField = 0;

    @FieldAnnotation @NoQualifierAnnotation public final int publicField = 0;

    private final int unannotatedField = 0;
  }
}
