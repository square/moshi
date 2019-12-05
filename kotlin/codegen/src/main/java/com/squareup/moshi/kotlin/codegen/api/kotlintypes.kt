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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
import kotlin.reflect.KClass

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
      UNIT, Void::class.asTypeName(), NOTHING -> throw IllegalStateException("Parameter with void, Unit, or Nothing type is illegal")
      else -> CodeBlock.of("null")
    }

internal fun TypeName.asTypeBlock(): CodeBlock {
  if (annotations.isNotEmpty()) {
    return copy(annotations = emptyList()).asTypeBlock()
  }
  when (this) {
    is ParameterizedTypeName -> {
      return if (rawType == ARRAY) {
        CodeBlock.of("%T::class.java", copy(nullable = false))
      } else {
        rawType.asTypeBlock()
      }
    }
    is TypeVariableName -> {
      val bound = bounds.firstOrNull() ?: ANY
      return bound.asTypeBlock()
    }
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

internal inline fun <reified T: TypeName> TypeName.mapTypes(noinline transform: T.() -> TypeName?): TypeName {
  return mapTypes(T::class, transform)
}

@Suppress("UNCHECKED_CAST")
internal fun <T: TypeName> TypeName.mapTypes(target: KClass<T>, transform: T.() -> TypeName?): TypeName {
  if (target.java == javaClass) {
    return (this as T).transform() ?: return this
  }
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> {
      (rawType.mapTypes(target, transform) as ClassName).parameterizedBy(typeArguments.map { it.mapTypes(target, transform) })
          .copy(nullable = isNullable, annotations = annotations)
    }
    is TypeVariableName -> {
      copy(bounds = bounds.map { it.mapTypes(target, transform) })
    }
    is WildcardTypeName -> {
      // TODO Would be nice if KotlinPoet modeled these easier.
      // Producer type - empty inTypes, single element outTypes
      // Consumer type - single element inTypes, single ANY element outType.
      when {
        this == STAR -> this
        outTypes.isNotEmpty() && inTypes.isEmpty() -> {
          WildcardTypeName.producerOf(outTypes[0].mapTypes(target, transform))
              .copy(nullable = isNullable, annotations = annotations)
        }
        inTypes.isNotEmpty() -> {
          WildcardTypeName.consumerOf(inTypes[0].mapTypes(target, transform))
              .copy(nullable = isNullable, annotations = annotations)
        }
        else -> throw UnsupportedOperationException("Not possible.")
      }
    }
    else -> throw UnsupportedOperationException("Type '${javaClass.simpleName}' is illegal. Only classes, parameterized types, wildcard types, or type variables are allowed.")
  }
}

internal fun TypeName.stripTypeVarVariance(): TypeName {
  return mapTypes<TypeVariableName> {
    TypeVariableName(name = name, bounds = bounds.map { it.mapTypes(TypeVariableName::stripTypeVarVariance) }, variance = null)
  }
}
