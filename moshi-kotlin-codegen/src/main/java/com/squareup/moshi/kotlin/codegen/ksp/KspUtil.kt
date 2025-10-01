/*
 * Copyright (C) 2021 Square, Inc.
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
package com.squareup.moshi.kotlin.codegen.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Origin.KOTLIN
import com.google.devtools.ksp.symbol.Origin.KOTLIN_LIB
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toClassName

internal fun KSClassDeclaration.asType() = asType(emptyList())

internal fun KSClassDeclaration.isKotlinClass(): Boolean {
  return origin == KOTLIN ||
    origin == KOTLIN_LIB ||
    isAnnotationPresent(Metadata::class)
}

internal inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType(): T? {
  return getAnnotationsByType(T::class).firstOrNull()
}

internal fun KSType.unwrapTypeAlias(): KSType {
  return if (this.declaration is KSTypeAlias) {
    (this.declaration as KSTypeAlias).type.resolve()
  } else {
    this
  }
}

internal fun KSAnnotation.toAnnotationSpec(resolver: Resolver): AnnotationSpec {
  val element = annotationType.resolve().unwrapTypeAlias().declaration as KSClassDeclaration
  val builder = AnnotationSpec.builder(element.toClassName())
  for (argument in arguments) {
    val member = CodeBlock.builder()
    val name = argument.name!!.getShortName()
    member.add("%L = ", name)
    addValueToBlock(argument.value!!, resolver, member, element)
    builder.addMember(member.build())
  }
  return builder.build()
}

private fun addValueToBlock(value: Any, resolver: Resolver, member: CodeBlock.Builder, annotationContext: KSClassDeclaration? = null) {
  when (value) {
    is List<*> -> {
      // Array type
      member.add("arrayOf(⇥⇥")
      value.forEachIndexed { index, innerValue ->
        if (index > 0) member.add(", ")
        addValueToBlock(innerValue!!, resolver, member, annotationContext)
      }
      member.add("⇤⇤)")
    }

    is KSType -> {
      val unwrapped = value.unwrapTypeAlias()
      val isEnum = (unwrapped.declaration as KSClassDeclaration).classKind == ClassKind.ENUM_ENTRY
      if (isEnum) {
        val parent = unwrapped.declaration.parentDeclaration as KSClassDeclaration
        val entry = unwrapped.declaration.simpleName.getShortName()
        member.add("%T.%L", parent.toClassName(), entry)
      } else {
        member.add("%T::class", unwrapped.toClassName())
      }
    }

    is KSName -> {
      // Try to resolve enum names using annotation context first
      val qualifier = value.getQualifier()
      val shortName = value.getShortName()

      if (qualifier != null) {
        // Try direct resolution first
        val resolvedClass = resolver.getClassDeclarationByName(resolver.getKSNameFromString(qualifier))
        when {
          resolvedClass?.classKind == ClassKind.ENUM_CLASS -> {
            member.add("%T.%L", resolvedClass.toClassName(), shortName)
          }
          annotationContext != null -> {
            // Look for nested enum in annotation context
            val nestedEnum = annotationContext.declarations
              .filterIsInstance<KSClassDeclaration>()
              .find { it.simpleName.getShortName() == qualifier && it.classKind == ClassKind.ENUM_CLASS }

            if (nestedEnum != null) {
              member.add("%T.%L", nestedEnum.toClassName(), shortName)
            } else {
              // Fallback to best guess
              member.add("%T.%L", ClassName.bestGuess(qualifier), shortName)
            }
          }
          else -> {
            // Fallback to best guess
            member.add("%T.%L", ClassName.bestGuess(qualifier), shortName)
          }
        }
      } else {
        member.add("%L", shortName)
      }
    }

    is KSClassDeclaration -> {
      // Handle enum entries that come directly as KSClassDeclaration
      if (value.classKind == ClassKind.ENUM_ENTRY) {
        val enumEntry = value.simpleName.getShortName()
        val parentClass = value.parentDeclaration as? KSClassDeclaration

        if (parentClass != null && parentClass.classKind == ClassKind.ENUM_CLASS) {
          member.add("%T.%L", parentClass.toClassName(), enumEntry)
        } else {
          member.add("%L", enumEntry)
        }
      } else {
        member.add("%T::class", value.toClassName())
      }
    }

    is KSAnnotation -> member.add("%L", value.toAnnotationSpec(resolver))

    else -> member.add(memberForValue(value))
  }
}

/**
 * Creates a [CodeBlock] with parameter `format` depending on the given `value` object.
 * Handles a number of special cases, such as appending "f" to `Float` values, and uses
 * `%L` for other types.
 */
internal fun memberForValue(value: Any) = when (value) {
  is Class<*> -> CodeBlock.of("%T::class", value)

  is Enum<*> -> CodeBlock.of("%T.%L", value.javaClass, value.name)

  is String -> CodeBlock.of("%S", value)

  is Float -> CodeBlock.of("%Lf", value)

  is Double -> CodeBlock.of("%L", value)

  is Char -> CodeBlock.of("$value.toChar()")

  is Byte -> CodeBlock.of("$value.toByte()")

  is Short -> CodeBlock.of("$value.toShort()")

  // Int or Boolean
  else -> CodeBlock.of("%L", value)
}

internal inline fun KSPLogger.check(condition: Boolean, message: () -> String) {
  check(condition, null, message)
}

internal inline fun KSPLogger.check(condition: Boolean, element: KSNode?, message: () -> String) {
  if (!condition) {
    error(message(), element)
  }
}
