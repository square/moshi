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
package com.squareup.moshi.kotlin.codegen

import com.google.auto.common.AnnotationMirrors
import com.squareup.kotlinpoet.TypeName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonQualifier
import me.eugeniomarletti.kotlin.metadata.declaresDefaultValue
import me.eugeniomarletti.kotlin.metadata.hasSetter
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Property
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Visibility.INTERNAL
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Visibility.PROTECTED
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Visibility.PUBLIC
import me.eugeniomarletti.kotlin.metadata.visibility
import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic

/** A property in user code that maps to JSON. */
internal data class TargetProperty(
  val name: String,
  val type: TypeName,
  private val proto: Property,
  private val parameter: TargetParameter?,
  private val annotationHolder: ExecutableElement?,
  private val field: VariableElement?,
  private val setter: ExecutableElement?,
  private val getter: ExecutableElement?
) {
  val parameterIndex get() = parameter?.index ?: -1

  val hasDefault get() = parameter?.proto?.declaresDefaultValue ?: true

  private val isTransient get() = field != null && Modifier.TRANSIENT in field.modifiers

  private val element get() = field ?: setter ?: getter!!

  private val isSettable get() = proto.hasSetter || parameter != null

  private val isVisible: Boolean
    get() {
      return proto.visibility == INTERNAL
          || proto.visibility == PROTECTED
          || proto.visibility == PUBLIC
    }

  /**
   * Returns a generator for this property, or null if either there is an error and this property
   * cannot be used with code gen, or if no codegen is necessary for this property.
   */
  fun generator(messager: Messager): PropertyGenerator? {
    if (isTransient) {
      if (!hasDefault) {
        messager.printMessage(
            Diagnostic.Kind.ERROR, "No default value for transient property ${this}", element)
        return null
      }
      return null // This property is transient and has a default value. Ignore it.
    }

    if (!isVisible) {
      messager.printMessage(Diagnostic.Kind.ERROR, "property ${this} is not visible", element)
      return null
    }

    if (!isSettable) {
      return null // This property is not settable. Ignore it.
    }

    return PropertyGenerator(this)
  }

  fun delegateKey() = DelegateKey(type, jsonQualifiers())

  /** Returns the JsonQualifiers on the field and parameter of this property. */
  private fun jsonQualifiers(): Set<AnnotationMirror> {
    val elementQualifiers = element.qualifiers
    val annotationHolderQualifiers = annotationHolder.qualifiers
    val parameterQualifiers = parameter?.element.qualifiers

    // TODO(jwilson): union the qualifiers somehow?
    return when {
      elementQualifiers.isNotEmpty() -> elementQualifiers
      annotationHolderQualifiers.isNotEmpty() -> annotationHolderQualifiers
      parameterQualifiers.isNotEmpty() -> parameterQualifiers
      else -> setOf()
    }
  }

  private val Element?.qualifiers: Set<AnnotationMirror>
    get() {
      if (this == null) return setOf()
      return AnnotationMirrors.getAnnotatedAnnotations(this, JsonQualifier::class.java)
    }

  /** Returns the @Json name of this property, or this property's name if none is provided. */
  fun jsonName(): String {
    val fieldJsonName = element.jsonName
    val annotationHolderJsonName = annotationHolder.jsonName
    val parameterJsonName = parameter?.element.jsonName

    return when {
      fieldJsonName != null -> fieldJsonName
      annotationHolderJsonName != null -> annotationHolderJsonName
      parameterJsonName != null -> parameterJsonName
      else -> name
    }
  }

  private val Element?.jsonName: String?
    get() {
      if (this == null) return null
      return getAnnotation(Json::class.java)?.name?.replace("$", "\\$")
    }

  override fun toString() = name
}
