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
package com.squareup.moshi.kotlin.codegen.api

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.tag
import com.squareup.kotlinpoet.tags.TypeAliasTag
import java.util.TreeSet

internal fun TypeName.unwrapTypeAliasReal(): TypeName {
  return tag<TypeAliasTag>()?.abbreviatedType?.let { unwrappedType ->
    // If any type is nullable, then the whole thing is nullable
    var isAnyNullable = isNullable
    // Keep track of all annotations across type levels. Sort them too for consistency.
    val runningAnnotations = TreeSet<AnnotationSpec>(compareBy { it.toString() }).apply {
      addAll(annotations)
    }
    val nestedUnwrappedType = unwrappedType.unwrapTypeAlias()
    runningAnnotations.addAll(nestedUnwrappedType.annotations)
    isAnyNullable = isAnyNullable || nestedUnwrappedType.isNullable
    nestedUnwrappedType.copy(nullable = isAnyNullable, annotations = runningAnnotations.toList())
  } ?: this
}

internal fun TypeName.unwrapTypeAlias(): TypeName {
  return when (this) {
    is ClassName -> unwrapTypeAliasReal()
    is ParameterizedTypeName -> {
      if (TypeAliasTag::class in tags) {
        unwrapTypeAliasReal()
      } else {
        deepCopy(TypeName::unwrapTypeAlias)
      }
    }
    is TypeVariableName -> {
      if (TypeAliasTag::class in tags) {
        unwrapTypeAliasReal()
      } else {
        deepCopy(transform = TypeName::unwrapTypeAlias)
      }
    }
    is WildcardTypeName -> {
      if (TypeAliasTag::class in tags) {
        unwrapTypeAliasReal()
      } else {
        deepCopy(TypeName::unwrapTypeAlias)
      }
    }
    is LambdaTypeName -> {
      if (TypeAliasTag::class in tags) {
        unwrapTypeAliasReal()
      } else {
        deepCopy(TypeName::unwrapTypeAlias)
      }
    }
    else -> throw UnsupportedOperationException("Type '${javaClass.simpleName}' is illegal. Only classes, parameterized types, wildcard types, or type variables are allowed.")
  }
}
