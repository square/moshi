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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.moshi.JsonAdapter
import java.util.Locale

/** A JsonAdapter that can be used to encode and decode a particular field. */
@InternalMoshiCodegenApi
public data class DelegateKey(
  private val type: TypeName,
  private val jsonQualifiers: List<AnnotationSpec>,
) {
  public val nullable: Boolean get() = type.isNullable

  /** Returns an adapter to use when encoding and decoding this property. */
  internal fun generateProperty(
    nameAllocator: NameAllocator,
    typeRenderer: TypeRenderer,
    moshiParameter: ParameterSpec,
    propertyName: String
  ): PropertySpec {
    val qualifierNames = jsonQualifiers.joinToString("") {
      "At${it.typeName.rawType().simpleName}"
    }
    val adapterName = nameAllocator.newName(
      "${type.toVariableName().replaceFirstChar { it.lowercase(Locale.US) }}${qualifierNames}Adapter",
      this
    )

    val adapterTypeName = JsonAdapter::class.asClassName().parameterizedBy(type)
    val standardArgs = arrayOf(
      moshiParameter,
      typeRenderer.render(type)
    )

    val (initializerString, args) = when {
      jsonQualifiers.isEmpty() -> ", %M()" to arrayOf(MemberName("kotlin.collections", "emptySet"))
      else -> {
        ", setOf(%L)" to arrayOf(jsonQualifiers.map { it.asInstantiationExpression() }.joinToCode())
      }
    }
    val finalArgs = arrayOf(*standardArgs, *args, propertyName)

    return PropertySpec.builder(adapterName, adapterTypeName, KModifier.PRIVATE)
      .initializer("%N.adapter(%L$initializerString, %S)", *finalArgs)
      .build()
  }
}

private fun AnnotationSpec.asInstantiationExpression(): CodeBlock {
  // <Type>(args)
  return CodeBlock.of(
    "%T(%L)",
    typeName,
    members.joinToCode()
  )
}

/**
 * Returns a suggested variable name derived from a list of type names. This just concatenates,
 * yielding types like MapOfStringLong.
 */
private fun List<TypeName>.toVariableNames() = joinToString("") { it.toVariableName() }

/** Returns a suggested variable name derived from a type name, like nullableListOfString. */
private fun TypeName.toVariableName(): String {
  val base = when (this) {
    is ClassName -> simpleName
    is ParameterizedTypeName -> rawType.simpleName + "Of" + typeArguments.toVariableNames()
    is WildcardTypeName -> (inTypes + outTypes).toVariableNames()
    is TypeVariableName -> name + bounds.toVariableNames()
    else -> throw IllegalArgumentException("Unrecognized type! $this")
  }

  return if (isNullable) {
    "Nullable$base"
  } else {
    base
  }
}
