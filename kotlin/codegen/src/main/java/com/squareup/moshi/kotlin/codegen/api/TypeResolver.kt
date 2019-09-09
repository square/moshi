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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName

/**
 * Resolves type parameters against a type declaration. Use this to fill in type variables with
 * their actual type parameters.
 */
open class TypeResolver {
  open fun resolveTypeVariable(typeVariable: TypeVariableName): TypeName = typeVariable

  fun resolve(typeName: TypeName): TypeName {
    return when (typeName) {
      is ClassName -> typeName

      is ParameterizedTypeName -> {
            typeName.rawType.parameterizedBy(*(typeName.typeArguments.map { resolve(it) }.toTypedArray()))
            .copy(nullable = typeName.isNullable)
      }

      is WildcardTypeName -> {
        when {
          typeName.inTypes.size == 1 -> {
            WildcardTypeName.consumerOf(resolve(typeName.inTypes[0]))
                .copy(nullable = typeName.isNullable)
          }
          typeName.outTypes.size == 1 -> {
            WildcardTypeName.producerOf(resolve(typeName.outTypes[0]))
                .copy(nullable = typeName.isNullable)
          }
          else -> {
            throw IllegalArgumentException(
                "Unrepresentable wildcard type. Cannot have more than one bound: $typeName")
          }
        }
      }

      is TypeVariableName -> resolveTypeVariable(typeName)

      else -> throw IllegalArgumentException("Unrepresentable type: $typeName")
    }
  }
}
