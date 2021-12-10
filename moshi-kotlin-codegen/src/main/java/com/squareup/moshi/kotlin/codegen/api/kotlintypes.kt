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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import java.lang.reflect.Array

internal fun TypeName.rawType(): ClassName {
  return findRawType() ?: throw IllegalArgumentException("Cannot get raw type from $this")
}

internal fun TypeName.findRawType(): ClassName? {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> rawType
    is LambdaTypeName -> {
      var count = parameters.size
      if (receiver != null) {
        count++
      }
      val functionSimpleName = if (count >= 23) {
        "FunctionN"
      } else {
        "Function$count"
      }
      ClassName("kotlin.jvm.functions", functionSimpleName)
    }
    else -> null
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
    UNIT, Void::class.asTypeName(), NOTHING -> throw IllegalStateException("Parameter with void, Unit, or Nothing type is illegal")
    else -> CodeBlock.of("null")
  }

@OptIn(DelicateKotlinPoetApi::class)
internal fun TypeName.asTypeBlock(): CodeBlock {
  if (annotations.isNotEmpty()) {
    return copy(annotations = emptyList()).asTypeBlock()
  }
  when (this) {
    is ParameterizedTypeName -> {
      return if (rawType == ARRAY) {
        val componentType = typeArguments[0]
        if (componentType is ParameterizedTypeName) {
          // "generic" array just uses the component's raw type
          // java.lang.reflect.Array.newInstance(<raw-type>, 0).javaClass
          CodeBlock.of(
            "%T.newInstance(%L, 0).javaClass",
            Array::class.java.asClassName(),
            componentType.rawType.asTypeBlock()
          )
        } else {
          CodeBlock.of("%T::class.java", copy(nullable = false))
        }
      } else {
        rawType.asTypeBlock()
      }
    }
    is TypeVariableName -> {
      val bound = bounds.firstOrNull() ?: ANY
      return bound.asTypeBlock()
    }
    is LambdaTypeName -> return rawType().asTypeBlock()
    is ClassName -> {
      // Check against the non-nullable version for equality, but we'll keep the nullability in
      // consideration when creating the CodeBlock if needed.
      return when (copy(nullable = false)) {
        BOOLEAN, CHAR, BYTE, SHORT, INT, FLOAT, LONG, DOUBLE -> {
          if (isNullable) {
            // Remove nullable but keep the java object type
            CodeBlock.of("%T::class.javaObjectType", copy(nullable = false))
          } else {
            CodeBlock.of("%T::class.javaPrimitiveType", this)
          }
        }
        UNIT, Void::class.asTypeName(), NOTHING -> throw IllegalStateException("Parameter with void, Unit, or Nothing type is illegal")
        else -> CodeBlock.of("%T::class.java", copy(nullable = false))
      }
    }
    else -> throw UnsupportedOperationException("Parameter with type '${javaClass.simpleName}' is illegal. Only classes, parameterized types, or type variables are allowed.")
  }
}

internal fun KModifier.checkIsVisibility() {
  require(ordinal <= ordinal) {
    "Visibility must be one of ${(0..ordinal).joinToString { KModifier.values()[it].name }}. Is $name"
  }
}

internal fun TypeName.stripTypeVarVariance(resolver: TypeVariableResolver): TypeName {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> {
      deepCopy { it.stripTypeVarVariance(resolver) }
    }
    is TypeVariableName -> resolver[name]
    is WildcardTypeName -> deepCopy { it.stripTypeVarVariance(resolver) }
    else -> throw UnsupportedOperationException("Type '${javaClass.simpleName}' is illegal. Only classes, parameterized types, wildcard types, or type variables are allowed.")
  }
}

internal fun ParameterizedTypeName.deepCopy(
  transform: (TypeName) -> TypeName
): ParameterizedTypeName {
  return rawType.parameterizedBy(typeArguments.map { transform(it) })
    .copy(nullable = isNullable, annotations = annotations, tags = tags)
}

internal fun TypeVariableName.deepCopy(
  variance: KModifier? = this.variance,
  transform: (TypeName) -> TypeName
): TypeVariableName {
  return TypeVariableName(name = name, bounds = bounds.map { transform(it) }, variance = variance)
    .copy(nullable = isNullable, annotations = annotations, tags = tags)
}

internal fun WildcardTypeName.deepCopy(transform: (TypeName) -> TypeName): TypeName {
  // TODO Would be nice if KotlinPoet modeled these easier.
  // Producer type - empty inTypes, single element outTypes
  // Consumer type - single element inTypes, single ANY element outType.
  return when {
    this == STAR -> this
    outTypes.isNotEmpty() && inTypes.isEmpty() -> {
      WildcardTypeName.producerOf(transform(outTypes[0]))
        .copy(nullable = isNullable, annotations = annotations)
    }
    inTypes.isNotEmpty() -> {
      WildcardTypeName.consumerOf(transform(inTypes[0]))
        .copy(nullable = isNullable, annotations = annotations)
    }
    else -> throw UnsupportedOperationException("Not possible.")
  }
}

internal fun LambdaTypeName.deepCopy(transform: (TypeName) -> TypeName): TypeName {
  return LambdaTypeName.get(
    receiver?.let(transform),
    parameters.map { it.toBuilder(type = transform(it.type)).build() },
    transform(returnType)
  ).copy(nullable = isNullable, annotations = annotations, suspending = isSuspending)
}

internal interface TypeVariableResolver {
  val parametersMap: Map<String, TypeVariableName>
  operator fun get(index: String): TypeVariableName
}

internal fun List<TypeName>.toTypeVariableResolver(
  fallback: TypeVariableResolver? = null,
  sourceType: String? = null,
): TypeVariableResolver {
  val parametersMap = LinkedHashMap<String, TypeVariableName>()
  val typeParamResolver = { id: String ->
    parametersMap[id]
      ?: fallback?.get(id)
      ?: throw IllegalStateException("No type argument found for $id! Anaylzing $sourceType")
  }

  val resolver = object : TypeVariableResolver {
    override val parametersMap: Map<String, TypeVariableName> = parametersMap

    override operator fun get(index: String): TypeVariableName = typeParamResolver(index)
  }

  // Fill the parametersMap. Need to do sequentially and allow for referencing previously defined params
  for (typeVar in this) {
    check(typeVar is TypeVariableName)
    // Put the simple typevar in first, then it can be referenced in the full toTypeVariable()
    // replacement later that may add bounds referencing this.
    val id = typeVar.name
    parametersMap[id] = TypeVariableName(id)
  }

  for (typeVar in this) {
    check(typeVar is TypeVariableName)
    // Now replace it with the full version.
    parametersMap[typeVar.name] = typeVar.deepCopy(null) { it.stripTypeVarVariance(resolver) }
  }

  return resolver
}
