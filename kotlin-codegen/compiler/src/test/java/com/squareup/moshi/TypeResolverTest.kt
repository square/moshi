package com.squareup.moshi

import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import org.junit.Test

class TypeResolverTest {

  private val resolver = TypeResolver()

  @Test
  fun ensureClassNameNullabilityIsPreserved() {
    assertThat(resolver.resolve(Int::class.asClassName().asNullable()).nullable).isTrue()
  }

  @Test
  fun ensureParameterizedNullabilityIsPreserved() {
    val nullableTypeName = ParameterizedTypeName.get(
        List::class.asClassName(),
        String::class.asClassName())
        .asNullable()

    assertThat(resolver.resolve(nullableTypeName).nullable).isTrue()
  }

  @Test
  fun ensureWildcardNullabilityIsPreserved() {
    val nullableTypeName = WildcardTypeName.subtypeOf(List::class.asClassName())
        .asNullable()

    assertThat(resolver.resolve(nullableTypeName).nullable).isTrue()
  }

}
