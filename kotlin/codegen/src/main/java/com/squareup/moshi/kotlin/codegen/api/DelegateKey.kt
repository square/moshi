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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
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
import com.squareup.kotlinpoet.asTypeName
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types

/** A JsonAdapter that can be used to encode and decode a particular field. */
internal data class DelegateKey(
  private val type: TypeName,
  private val jsonQualifiers: List<AnnotationSpec>
) {
  val nullable get() = type.isNullable

  /** Returns an adapter to use when encoding and decoding this property. */
  fun generateProperty(
    nameAllocator: NameAllocator,
    typeRenderer: TypeRenderer,
    moshiParameter: ParameterSpec,
    propertyName: String
  ): PropertySpec {
    val qualifierNames = jsonQualifiers.joinToString("") {
      "At${it.className.simpleName}"
    }
    val adapterName = nameAllocator.newName(
        "${type.toVariableName().decapitalize()}${qualifierNames}Adapter", this)

    val adapterTypeName = JsonAdapter::class.asClassName().parameterizedBy(type)
    val standardArgs = arrayOf(moshiParameter,
        typeRenderer.render(type))
    val (initializerString, args) = when {
      jsonQualifiers.isEmpty() -> ", %M()" to arrayOf(MemberName("kotlin.collections", "emptySet"))
      else -> {
        ", %T.getFieldJsonQualifierAnnotations(javaClass, " +
            "%S)" to arrayOf(Types::class.asTypeName(), adapterName)
      }
    }
    val finalArgs = arrayOf(*standardArgs, *args, propertyName)

    return PropertySpec.builder(adapterName, adapterTypeName, KModifier.PRIVATE)
        .addAnnotations(jsonQualifiers)
        .initializer("%N.adapter(%L$initializerString, %S)", *finalArgs)
        .build()
  }
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
