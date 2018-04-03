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
package com.squareup.moshi

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.element.AnnotationMirror

/** Generates functions to encode and decode a property as JSON. */
internal class PropertyGenerator(
  val name: String,
  val serializedName: String,
  val hasConstructorParameter: Boolean,
  val hasDefault: Boolean,
  val nullable: Boolean,
  val typeName: TypeName,
  val unaliasedName: TypeName,
  val jsonQualifiers: Set<AnnotationMirror>
) {
  lateinit var delegateName: String
  lateinit var localName: String
  lateinit var localIsPresentName: String

  val isRequired
    get() = !nullable && !hasDefault

  /** We prefer to use 'null' to mean absent, but for some properties those are distinct. */
  val differentiateAbsentFromNull
    get() = hasDefault && nullable

  fun reserveDelegateNames(nameAllocator: NameAllocator) {
    val qualifierNames = jsonQualifiers.joinToString("") {
      "at${it.annotationType.asElement().simpleName.toString().capitalize()}"
    }
    nameAllocator.newName("${unaliasedName.toVariableName()}${qualifierNames}Adapter",
        delegateKey())
  }

  fun allocateNames(nameAllocator: NameAllocator) {
    localName = nameAllocator.newName(name)
    localIsPresentName = nameAllocator.newName("${name}Set")
    delegateName = nameAllocator.get(delegateKey())
  }

  /** Returns a key that matches keys of properties that can share an adapter. */
  fun delegateKey() = unaliasedName to jsonQualifiers

  /** Returns an adapter to use when encoding and decoding this property. */
  fun generateDelegateProperty(enclosing: AdapterGenerator): PropertySpec {
    val adapterTypeName = ParameterizedTypeName.get(
        JsonAdapter::class.asTypeName(), unaliasedName)
    val qualifiers = jsonQualifiers.toList()
    val standardArgs = arrayOf(enclosing.moshiParam,
        if (unaliasedName is ClassName && qualifiers.isEmpty()) {
          ""
        } else {
          CodeBlock.of("<%T>", unaliasedName)
        },
        unaliasedName.makeType(
            enclosing.elements, enclosing.typesParam, enclosing.genericTypeNames ?: emptyList()))
    val standardArgsSize = standardArgs.size + 1
    val (initializerString, args) = when {
      qualifiers.isEmpty() -> "" to emptyArray()
      qualifiers.size == 1 -> {
        ", %${standardArgsSize}T::class.java" to arrayOf(
            qualifiers.first().annotationType.asTypeName())
      }
      else -> {
        val initString = qualifiers
            .mapIndexed { index, _ ->
              val annoClassIndex = standardArgsSize + index
              return@mapIndexed "%${annoClassIndex}T::class.java"
            }
            .joinToString()
        val initArgs = qualifiers
            .map { it.annotationType.asTypeName() }
            .toTypedArray()
        ", $initString" to initArgs
      }
    }
    val finalArgs = arrayOf(*standardArgs, *args)

    return PropertySpec.builder(delegateName, adapterTypeName,
        KModifier.PRIVATE)
        .initializer("%1N.adapter%2L(%3L$initializerString)${if (nullable) ".nullSafe()" else ""}",
            *finalArgs)
        .build()
  }

  fun generateLocalProperty(): PropertySpec {
    return PropertySpec.builder(localName, typeName.asNullable())
        .mutable(true)
        .initializer("null")
        .build()
  }

  fun generateLocalIsPresentProperty(): PropertySpec {
    return PropertySpec.builder(localIsPresentName, BOOLEAN)
        .mutable(true)
        .initializer("false")
        .build()
  }
}

/**
 * Returns a suggested variable name derived from a list of type names.
 */
private fun List<TypeName>.toVariableNames(): String {
  return joinToString("_") { it.toVariableName() }
}

/**
 * Returns a suggested variable name derived from a type name.
 */
private fun TypeName.toVariableName(): String {
  return when (this) {
    is ClassName -> simpleName().decapitalize()
    is ParameterizedTypeName -> {
      rawType.simpleName().decapitalize() + if (typeArguments.isEmpty()) "" else "__" + typeArguments.toVariableNames()
    }
    is WildcardTypeName -> "wildcard__" + (lowerBounds + upperBounds).toVariableNames()
    is TypeVariableName -> name.decapitalize() + if (bounds.isEmpty()) "" else "__" + bounds.toVariableNames()
    else -> throw IllegalArgumentException("Unrecognized type! $this")
  }.let { if (nullable) "${it}_nullable" else it }
}
