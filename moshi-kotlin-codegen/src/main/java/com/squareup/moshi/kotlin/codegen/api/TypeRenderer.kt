/*
 * Copyright (C) 2018 Square, Inc.
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

import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.joinToCode
import kotlin.reflect.KVariance

/**
 * Renders literals like `Types.newParameterizedType(List::class.java, String::class.java)`.
 * Rendering is pluggable so that type variables can either be resolved or emitted as other code
 * blocks.
 */
internal abstract class TypeRenderer {
  abstract fun renderTypeVariable(typeVariable: TypeVariableName): CodeBlock

  fun render(typeName: TypeName, forceBox: Boolean = false): CodeBlock {
    if (typeName.annotations.isNotEmpty()) {
      return render(typeName.copy(annotations = emptyList()), forceBox)
    }

    return when (typeName) {
      is ClassName -> {
        renderKType(typeName)
      }

      is ParameterizedTypeName -> {
        // If it's an Array type, we shortcut this to return Types.arrayOf()
        if (typeName.rawType == ARRAY) {
          val arg = typeName.typeArguments[0]
          CodeBlock.of(
            "%L.%M(%L)",
            render(arg),
            MemberName("com.squareup.moshi", "asArrayKType"),
            arg.kVarianceBlock
          )
        } else {
          CodeBlock.of(
            "%T::class.%M(%L)",
            typeName.rawType,
            MemberName("com.squareup.moshi", "parameterizedBy"),
            typeName.typeArguments
              .map {
                CodeBlock.of(
                  "%L.%M(%L)",
                  render(it),
                  MemberName("com.squareup.moshi", "asKTypeProjection"),
                  it.kVarianceBlock
                )
              }
              .joinToCode(", ")
          )
        }
      }
      is WildcardTypeName -> {
        val target = when {
          typeName.inTypes.size == 1 -> {
            typeName.inTypes[0]
          }
          typeName.outTypes.size == 1 -> {
            typeName.outTypes[0]
          }
          else -> throw IllegalArgumentException(
            "Unrepresentable wildcard type. Cannot have more than one bound: $typeName"
          )
        }
        render(target)
      }

      is TypeVariableName -> renderTypeVariable(typeName)

      else -> throw IllegalArgumentException("Unrepresentable type: $typeName")
    }
  }

  private fun renderKType(className: ClassName): CodeBlock {
    return CodeBlock.of(
      "%T::class.%M(isMarkedNullable = %L)",
      className.copy(nullable = false),
      MemberName("com.squareup.moshi", "asKType"),
      className.isNullable
    )
  }

  private val TypeName.kVarianceBlock: CodeBlock get() {
    val variance = if (this is WildcardTypeName) {
      if (outTypes.isNotEmpty()) {
        KVariance.IN
      } else {
        KVariance.OUT
      }
    } else {
      KVariance.INVARIANT
    }
    return CodeBlock.of(
      "%T.%L",
      KVariance::class,
      variance.name
    )
  }
}
