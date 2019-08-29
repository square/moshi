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
package com.squareup.moshi.kotlin.codegen

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName

internal fun TypeName.rawType(): ClassName {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> rawType
    else -> throw IllegalArgumentException("Cannot get raw type from $this")
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
