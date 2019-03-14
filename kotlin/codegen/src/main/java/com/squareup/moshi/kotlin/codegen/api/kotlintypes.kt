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

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.google.auto.common.MoreTypes
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.VARARG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asTypeVariableName
import com.squareup.kotlinpoet.tag
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.ABSTRACT
import javax.lang.model.element.Modifier.DEFAULT
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.NATIVE
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PROTECTED
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import javax.lang.model.element.Modifier.SYNCHRONIZED
import javax.lang.model.element.Modifier.TRANSIENT
import javax.lang.model.element.Modifier.VOLATILE
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeVariable
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
      UNIT, Void::class.asTypeName() -> throw IllegalStateException("Parameter with void or Unit type is illegal")
      else -> CodeBlock.of("null")
    }

internal fun KModifier.checkIsVisibility() {
  require(ordinal <= ordinal) {
    "Visibility must be one of ${(0..ordinal).joinToString { KModifier.values()[it].name }}. Is $name"
  }
}

/**
 * Returns a new [FunSpec] representation of [this].
 *
 * This will copy its visibility modifiers, type parameters, return type, name, parameters, and
 * throws declarations.
 *
 * The original `method` ([this]) is stored in [FunSpec.tag].
 *
 * Nearly identical to [FunSpec.overriding], but no override modifier is added nor are checks around
 * overridability done
 */
internal fun ExecutableElement.asFunSpec(): FunSpec {
  var modifiers: Set<Modifier> = modifiers
  val methodName = simpleName.toString()
  val funBuilder = FunSpec.builder(methodName)

  modifiers = modifiers.toMutableSet()
  funBuilder.jvmModifiers(modifiers)

  typeParameters
      .map { it.asType() as TypeVariable }
      .map { it.asTypeVariableName() }
      .forEach { funBuilder.addTypeVariable(it) }

  funBuilder.returns(returnType.asTypeName())
  funBuilder.addParameters(ParameterSpec.parametersOf(this))
  if (isVarArgs) {
    funBuilder.parameters[funBuilder.parameters.lastIndex] = funBuilder.parameters.last()
        .toBuilder()
        .addModifiers(VARARG)
        .build()
  }

  if (thrownTypes.isNotEmpty()) {
    val throwsValueString = thrownTypes.joinToString { "%T::class" }
    funBuilder.addAnnotation(AnnotationSpec.builder(Throws::class)
        .addMember(throwsValueString, *thrownTypes.toTypedArray())
        .build())
  }

  funBuilder.tag(this)
  return funBuilder.build()
}

/**
 * Returns a new [PropertySpec] representation of [this].
 *
 * This will copy its name, type, visibility modifiers, constant value, and annotations. Note that
 * Java modifiers that correspond to annotations in kotlin will be added as well (`volatile`,
 * `transient`, etc`.
 *
 * The original `field` ([this]) is stored in [PropertySpec.tag].
 */
internal fun VariableElement.asPropertySpec(asJvmField: Boolean = false): PropertySpec {
  require(kind == ElementKind.FIELD) {
    "Must be a field!"
  }
  val modifiers: Set<Modifier> = modifiers
  val fieldName = simpleName.toString()
  val propertyBuilder = PropertySpec.builder(fieldName, asType().asTypeName())
  propertyBuilder.addModifiers(*modifiers.mapNotNull { it.asKModifier() }.toTypedArray())
  constantValue?.let {
    if (it is String) {
      propertyBuilder.initializer(CodeBlock.of("%S", it))
    } else {
      propertyBuilder.initializer(CodeBlock.of("%L", it))
    }
  }
  propertyBuilder.addAnnotations(annotationMirrors.map(AnnotationMirror::asAnnotationSpec))
  propertyBuilder.addAnnotations(modifiers.mapNotNull { it.asAnnotation() })
  propertyBuilder.tag(this)
  if (asJvmField && KModifier.PRIVATE !in propertyBuilder.modifiers) {
    propertyBuilder.addAnnotation(JvmField::class)
  }
  return propertyBuilder.build()
}

/**
 * Returns a new [AnnotationSpec] representation of [this].
 *
 * Identical and delegates to [AnnotationSpec.get], but the original `mirror` is also stored
 * in [AnnotationSpec.tag].
 */
internal fun AnnotationMirror.asAnnotationSpec(): AnnotationSpec {
  return AnnotationSpec.get(this)
      .toBuilder()
      .tag(MoreTypes.asTypeElement(annotationType))
      .build()
}

private fun Modifier.asKModifier(): KModifier? {
  return when (this) {
    PUBLIC -> KModifier.PUBLIC
    PROTECTED -> KModifier.PROTECTED
    PRIVATE -> KModifier.PRIVATE
    ABSTRACT -> KModifier.ABSTRACT
    FINAL -> KModifier.FINAL
    else -> null
  }
}

private fun Modifier.asAnnotation(): AnnotationSpec? {
  return when (this) {
    DEFAULT -> JvmDefault::class.asAnnotationSpec()
    STATIC -> JvmStatic::class.asAnnotationSpec()
    TRANSIENT -> Transient::class.asAnnotationSpec()
    VOLATILE -> Volatile::class.asAnnotationSpec()
    SYNCHRONIZED -> Synchronized::class.asAnnotationSpec()
    NATIVE -> JvmDefault::class.asAnnotationSpec()
    else -> null
  }
}

private fun <T : Annotation> KClass<T>.asAnnotationSpec(): AnnotationSpec {
  return AnnotationSpec.builder(this).build()
}
