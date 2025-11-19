/*
 * Copyright (C) 2025 Square, Inc.
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
package com.squareup.moshi.internal

import assertk.Assert
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.squareup.moshi.internal.javaType as moshiJavaType
import com.squareup.moshi.rawType
import java.lang.reflect.GenericArrayType
import java.lang.reflect.GenericDeclaration
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KFunction0
import kotlin.reflect.javaType as kotlinJavaType
import kotlin.reflect.typeOf
import org.junit.Test

class KotlinReflectTypesTest {
  @Test
  fun regularClass() {
    val kotlinType = typeOf<String>()
    val javaType = kotlinType.moshiJavaType as Class<*>
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType).isEqualTo(String::class.java)
  }

  @Test
  fun regularArray() {
    val kotlinType = typeOf<Array<String>>()
    val javaType = kotlinType.moshiJavaType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType).isEqualTo(Array<String>::class.java)
  }

  @Test
  fun varianceInArray() {
    val kotlinType = typeOf<Array<in String>>()
    val javaType = kotlinType.moshiJavaType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType).isEqualTo(Array<String>::class.java)
  }

  @Test
  fun varianceOutArray() {
    val kotlinType = typeOf<Array<out String>>()
    val javaType = kotlinType.moshiJavaType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType).isEqualTo(Array<String>::class.java)
  }

  @Test
  fun genericArray() {
    val kotlinType = typeOf<Array<List<String>>>()
    val javaType = kotlinType.moshiJavaType as GenericArrayType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType.toString()).isEqualTo("java.util.List<java.lang.String>[]")
    val componentType = javaType.genericComponentType as ParameterizedType
    assertThat(componentType.rawType).isEqualTo(List::class.java)
    assertThat(componentType.actualTypeArguments).containsExactly(String::class.java)
    assertThat(componentType.ownerType).isNull()
  }

  @Test
  fun parameterizedType() {
    val kotlinType = typeOf<List<String>>()
    val javaType = kotlinType.moshiJavaType as ParameterizedType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType.toString()).isEqualTo("java.util.List<java.lang.String>")
    assertThat(javaType.rawType).isEqualTo(List::class.java)
    assertThat(javaType.actualTypeArguments).containsExactly(String::class.java)
    assertThat(javaType.ownerType).isNull()
  }

  @Test
  fun outWildcardType() {
    val kotlinType = typeOf<MutableList<out String>>()
    val javaType = kotlinType.moshiJavaType as ParameterizedType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType.toString()).isEqualTo("java.util.List<? extends java.lang.String>")
    assertThat(javaType.rawType).isEqualTo(List::class.java)

    val wildcardType = javaType.actualTypeArguments.single() as WildcardType
    assertThat(wildcardType.rawType).isEqualTo(String::class.java)
    assertThat(wildcardType.upperBounds).containsExactly(String::class.java)
    assertThat(wildcardType.lowerBounds).isEmpty()
  }

  @Test
  fun inWildcardType() {
    val kotlinType = typeOf<MutableList<in String>>()
    val javaType = kotlinType.moshiJavaType as ParameterizedType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType.toString()).isEqualTo("java.util.List<? super java.lang.String>")
    assertThat(javaType.rawType).isEqualTo(List::class.java)

    val wildcardType = javaType.actualTypeArguments.single() as WildcardType
    assertThat(wildcardType.rawType).isEqualTo(Any::class.java)
    assertThat(wildcardType.upperBounds).containsExactly(Any::class.java)
    assertThat(wildcardType.lowerBounds).containsExactly(String::class.java)
  }

  @Test
  fun starWildcardType() {
    val kotlinType = typeOf<MutableList<*>>()
    val javaType = kotlinType.moshiJavaType as ParameterizedType
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType.toString()).isEqualTo("java.util.List<?>")
    assertThat(javaType.rawType).isEqualTo(List::class.java)

    val wildcardType = javaType.actualTypeArguments.single() as WildcardType
    assertThat(wildcardType.rawType).isEqualTo(Any::class.java)
    assertThat(wildcardType.upperBounds).containsExactly(Any::class.java)
    assertThat(wildcardType.lowerBounds).isEmpty()
  }

  @Test
  fun primitiveType() {
    val kotlinType = typeOf<Int>()
    val javaType = kotlinType.moshiJavaType as Class<*>
    assertThat(javaType).isSymmetricEqualTo(kotlinType.kotlinJavaType)
    assertThat(javaType).isEqualTo(Int::class.java)
  }

  @Test
  fun typeVariable() {
    val function: KFunction0<String> = ::hello
    val kotlinType = function.returnType
    val javaType = kotlinType.moshiJavaType as TypeVariable<GenericDeclaration>
    assertThat(javaType.bounds).containsExactly(Any::class.java)
    assertFailure { javaType.genericDeclaration }.isInstanceOf<NotImplementedError>()
    assertThat(javaType.name).isEqualTo("T")
    assertThat(javaType.toString()).isEqualTo("T")
  }

  fun <T> hello(): T {
    error("Unexpected call")
  }

  fun <T : Any> Assert<T>.isSymmetricEqualTo(expected: T) = given { actual ->
    assertThat(actual).isEqualTo(expected)
    assertThat(actual.hashCode(), "hashCode()").isEqualTo(expected.hashCode())
    assertThat(expected, "symmetric equals").isEqualTo(actual)
  }
}
