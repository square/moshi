/*
 * Copyright (C) 2018 Square, Inc.
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
package com.squareup.moshi.kotlin.codegen.api

import com.squareup.kotlinpoet.*
import com.squareup.moshi.NonNullValues

internal fun TypeName.rawType(): ClassName {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> rawType
    else -> throw IllegalArgumentException("Cannot get raw type from $this")
  }
}

internal fun ParameterizedTypeName.isCollection(): Boolean {
    return when(rawType) {
        ITERABLE, MUTABLE_ITERABLE, COLLECTION, MUTABLE_COLLECTION, LIST, MUTABLE_LIST, SET, MUTABLE_SET -> true
        else -> runCatching { Class
                .forName(rawType.reflectionName())
                .isAssignableFrom(Iterable::class.java)
        }.getOrDefault(false)
    }
}

internal fun TypeName.typeAnnotations(): Set<AnnotationSpec> {
    return if (this is ParameterizedTypeName && isCollection() && !typeArguments[0].isNullable) {
        val annotation = AnnotationSpec.builder(NonNullValues::class).build()
        setOf(annotation)
    } else {
        emptySet()
    }
}

internal fun TypeName.defaultPrimitiveValue(): CodeBlock =
    when (this) {
      BOOLEAN -> CodeBlock.of("false")
      CHAR -> CodeBlock.of("0.toChar()")
      BYTE -> CodeBlock.of("0.toByte()")
      SHORT -> CodeBlock.of("0.toShort()")
      INT -> CodeBlock.of("0")
      FLOAT -> CodeBlock.of("0f")
      LONG -> CodeBlock.of("0L")
      DOUBLE -> CodeBlock.of("0.0")
      UNIT, Void::class.asTypeName() -> throw IllegalStateException("Parameter with void or Unit type is illegal")
      else -> CodeBlock.of("null")
    }

internal fun KModifier.checkIsVisibility() {
  require(ordinal <= ordinal) {
    "Visibility must be one of ${(0..ordinal).joinToString { KModifier.values()[it].name }}. Is $name"
  }
}
