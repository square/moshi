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

import com.squareup.kotlinpoet.ARRAY
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
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.moshi.Types

/**
 * Renders literals like `Types.newParameterizedType(List::class.java, String::class.java)`.
 * Rendering is pluggable so that type variables can either be resolved or emitted as other code
 * blocks.
 */
abstract class TypeRenderer {
  abstract fun renderTypeVariable(typeVariable: TypeVariableName): CodeBlock

  fun render(typeName: TypeName): CodeBlock {
    if (typeName.nullable) {
      if (typeName == BOOLEAN.asNullable()
          || typeName == BYTE.asNullable()
          || typeName == CHAR.asNullable()
          || typeName == DOUBLE.asNullable()
          || typeName == FLOAT.asNullable()
          || typeName == INT.asNullable()
          || typeName == LONG.asNullable()
          || typeName == SHORT.asNullable()) {
        return CodeBlock.of("%T::class.javaObjectType", typeName.asNonNullable())
      }
      return render(typeName.asNonNullable())
    }

    return when (typeName) {
      is ClassName -> CodeBlock.of("%T::class.java", typeName)

      is ParameterizedTypeName -> {
        // If it's an Array type, we shortcut this to return Types.arrayOf()
        if (typeName.rawType == ARRAY) {
          CodeBlock.of("%T.arrayOf(%L)",
              Types::class,
              render(typeName.typeArguments[0].objectType()))
        } else {
          val builder = CodeBlock.builder().apply {
            add("%T.", Types::class)
            val enclosingClassName = typeName.rawType.enclosingClassName()
            if (enclosingClassName != null) {
              add("newParameterizedTypeWithOwner(%L, ", render(enclosingClassName))
            } else {
              add("newParameterizedType(")
            }
            add("%T::class.java", typeName.rawType.objectType())
            for (typeArgument in typeName.typeArguments) {
              add(", %L", render(typeArgument.objectType()))
            }
            add(")")
          }
          builder.build()
        }
      }

      is WildcardTypeName -> {
        val target: TypeName
        val method: String
        when {
          typeName.lowerBounds.size == 1 -> {
            target = typeName.lowerBounds[0]
            method = "supertypeOf"
          }
          typeName.upperBounds.size == 1 -> {
            target = typeName.upperBounds[0]
            method = "subtypeOf"
          }
          else -> throw IllegalArgumentException(
              "Unrepresentable wildcard type. Cannot have more than one bound: $typeName")
        }
        CodeBlock.of("%T.%L(%T::class.java)", Types::class, method, target)
      }

      is TypeVariableName -> renderTypeVariable(typeName)

      else -> throw IllegalArgumentException("Unrepresentable type: $typeName")
    }
  }

  private fun TypeName.objectType(): TypeName {
    return when (this) {
      BOOLEAN -> Boolean::class.javaObjectType.asTypeName()
      BYTE -> Byte::class.javaObjectType.asTypeName()
      SHORT -> Short::class.javaObjectType.asTypeName()
      INT -> Integer::class.javaObjectType.asTypeName()
      LONG -> Long::class.javaObjectType.asTypeName()
      CHAR -> Character::class.javaObjectType.asTypeName()
      FLOAT -> Float::class.javaObjectType.asTypeName()
      DOUBLE -> Double::class.javaObjectType.asTypeName()
      else -> this
    }
  }
}