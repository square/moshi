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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.element.ElementKind
import javax.lang.model.util.Elements

internal fun TypeName.rawType(): ClassName {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> rawType
    else -> throw IllegalArgumentException("Cannot get raw type from $this")
  }
}

private fun ClassName.isClass(elements: Elements): Boolean {
  val fqcn = toString()
  if (fqcn.startsWith("kotlin.collections.")) {
    // These are special kotlin interfaces are only visible in kotlin, because they're replaced by
    // the compiler with concrete java classes/
    return false
  } else if (this == ARRAY) {
    // This is a "fake" class and not visible to Elements.
    return true
  }
  return elements.getTypeElement(fqcn).kind == ElementKind.INTERFACE
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

internal fun TypeName.makeType(
  elementUtils: Elements,
  typesArray: ParameterSpec,
  genericTypeNames: List<TypeVariableName>
): CodeBlock {
  if (nullable) {
    return asNonNullable().makeType(elementUtils, typesArray, genericTypeNames)
  }
  return when (this) {
    is ClassName -> CodeBlock.of(
        "%T::class.java", this)
    is ParameterizedTypeName -> {
      // If it's an Array type, we shortcut this to return Types.arrayOf()
      if (rawType == ARRAY) {
        return CodeBlock.of("%T.arrayOf(%L)",
            Types::class,
            typeArguments[0].objectType().makeType(elementUtils, typesArray, genericTypeNames))
      }
      // If it's a Class type, we have to specify the generics.
      val rawTypeParameters = if (rawType.isClass(elementUtils)) {
        CodeBlock.of(
            typeArguments.joinTo(
                buffer = StringBuilder(),
                separator = ", ",
                prefix = "<",
                postfix = ">") { "%T" }
                .toString(),
            *(typeArguments.map { objectType() }.toTypedArray())
        )
      } else {
        CodeBlock.of("")
      }
      CodeBlock.of(
          "%T.newParameterizedType(%T%L::class.java, ${typeArguments
              .joinToString(", ") { "%L" }})",
          Types::class,
          rawType.objectType(),
          rawTypeParameters,
          *(typeArguments.map {
            it.objectType().makeType(elementUtils, typesArray, genericTypeNames)
          }.toTypedArray()))
    }
    is WildcardTypeName -> {
      val target: TypeName
      val method: String
      when {
        lowerBounds.size == 1 -> {
          target = lowerBounds[0]
          method = "supertypeOf"
        }
        upperBounds.size == 1 -> {
          target = upperBounds[0]
          method = "subtypeOf"
        }
        else -> throw IllegalArgumentException(
            "Unrepresentable wildcard type. Cannot have more than one bound: " + this)
      }
      CodeBlock.of("%T.%L(%T::class.java)",
          Types::class, method, target)
    }
    is TypeVariableName -> {
      CodeBlock.of("%N[%L]", typesArray,
          genericTypeNames.indexOfFirst { it == this })
    }
    else -> throw IllegalArgumentException("Unrepresentable type: " + this)
  }
}