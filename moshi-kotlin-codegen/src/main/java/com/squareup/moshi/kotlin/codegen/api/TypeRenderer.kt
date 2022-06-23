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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName

/**
 * Renders literals like `List::class.parameterizedBy(String::class.asKTypeProjection(...))`.
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
      is TypeVariableName -> renderTypeVariable(typeName)
      is ClassName, is LambdaTypeName, is ParameterizedTypeName, is WildcardTypeName -> {
        CodeBlock.of(
          "%M<%T>()",
          MemberName("kotlin.reflect", "typeOf"),
          typeName
        )
      }
      else -> throw IllegalArgumentException("Unrepresentable type: $typeName")
    }
  }
}
