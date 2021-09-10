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
import com.google.devtools.ksp.symbol.Visibility
import com.google.devtools.ksp.symbol.Visibility.INTERNAL
import com.google.devtools.ksp.symbol.Visibility.JAVA_PACKAGE
import com.google.devtools.ksp.symbol.Visibility.LOCAL
import com.google.devtools.ksp.symbol.Visibility.PRIVATE
import com.google.devtools.ksp.symbol.Visibility.PROTECTED
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier

internal inline fun <reified T> Resolver.getClassDeclarationByName(): KSClassDeclaration {
  return getClassDeclarationByName(T::class.qualifiedName!!)
}

internal fun Resolver.getClassDeclarationByName(fqcn: String): KSClassDeclaration {
  return getClassDeclarationByName(getKSNameFromString(fqcn)) ?: error("Class '$fqcn' not found.")
}

internal fun KSClassDeclaration.asType() = asType(emptyList())

internal fun KSClassDeclaration.isKotlinClass(resolver: Resolver): Boolean {
  return origin == KOTLIN ||
    origin == KOTLIN_LIB ||
    hasAnnotation(resolver.getClassDeclarationByName<Metadata>().asType())
}

internal fun KSAnnotated.hasAnnotation(target: KSType): Boolean {
  return findAnnotationWithType(target) != null
}

internal inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType(
  resolver: Resolver,
): KSAnnotation? {
  return findAnnotationWithType(resolver.getClassDeclarationByName<T>().asType())
}

internal fun KSAnnotated.findAnnotationWithType(target: KSType): KSAnnotation? {
  return annotations.find { it.annotationType.resolve() == target }
}

internal inline fun <reified T> KSAnnotation.getMember(name: String): T {
  val matchingArg = arguments.find { it.name?.asString() == name }
    ?: error(
      "No member name found for '$name'. All arguments: ${arguments.map { it.name?.asString() }}"
    )
  return when (val argValue = matchingArg.value) {
    is List<*> -> {
      if (argValue.isEmpty()) {
        argValue as T
      } else {
        val first = argValue[0]
        if (first is KSType) {
          argValue.map { (it as KSType).toClassName() } as T
        } else {
          argValue as T
        }
      }
    }
    is KSType -> argValue.toClassName() as T
    else -> {
      argValue as? T ?: error("Null value found for @${shortName.getShortName()}.$name. All args -> $arguments")
    }
  }
}

internal fun KSType.unwrapTypeAlias(): KSType {
  return if (this.declaration is KSTypeAlias) {
    (this.declaration as KSTypeAlias).type.resolve()
  } else {
    this
  }
}

internal fun Visibility.asKModifier(): KModifier {
  return when (this) {
    PUBLIC -> KModifier.PUBLIC
    PRIVATE -> KModifier.PRIVATE
    PROTECTED -> KModifier.PROTECTED
    INTERNAL -> KModifier.INTERNAL
    JAVA_PACKAGE -> KModifier.PUBLIC
    LOCAL -> error("Local is unsupported")
  }
}

internal fun KSAnnotation.toAnnotationSpec(resolver: Resolver): AnnotationSpec {
  val element = annotationType.resolve().unwrapTypeAlias().declaration as KSClassDeclaration
  val builder = AnnotationSpec.builder(element.toClassName())
  for (argument in arguments) {
    val member = CodeBlock.builder()
    val name = argument.name!!.getShortName()
    member.add("%L = ", name)
    addValueToBlock(argument.value!!, resolver, member)
    builder.addMember(member.build())
  }
  return builder.build()
}

private fun addValueToBlock(value: Any, resolver: Resolver, member: CodeBlock.Builder) {
  when (value) {
    is List<*> -> {
      // Array type
      member.add("[⇥⇥")
      value.forEachIndexed { index, innerValue ->
        if (index > 0) member.add(", ")
        addValueToBlock(innerValue!!, resolver, member)
      }
      member.add("⇤⇤]")
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
    is KSName ->
      member.add(
        "%T.%L", ClassName.bestGuess(value.getQualifier()),
        value.getShortName()
      )
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
