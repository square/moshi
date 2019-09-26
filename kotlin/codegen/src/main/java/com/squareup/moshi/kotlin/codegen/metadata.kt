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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.classinspector.elements.ElementsClassInspector
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.squareup.kotlinpoet.metadata.isAbstract
import com.squareup.kotlinpoet.metadata.isClass
import com.squareup.kotlinpoet.metadata.isEnum
import com.squareup.kotlinpoet.metadata.isInner
import com.squareup.kotlinpoet.metadata.isLocal
import com.squareup.kotlinpoet.metadata.isSealed
import com.squareup.kotlinpoet.metadata.specs.ClassInspector
import com.squareup.kotlinpoet.metadata.specs.toTypeSpec
import com.squareup.kotlinpoet.metadata.toImmutableKmClass
import com.squareup.moshi.Json
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.kotlin.codegen.api.DelegateKey
import com.squareup.moshi.kotlin.codegen.api.PropertyGenerator
import com.squareup.moshi.kotlin.codegen.api.TargetConstructor
import com.squareup.moshi.kotlin.codegen.api.TargetParameter
import com.squareup.moshi.kotlin.codegen.api.TargetProperty
import com.squareup.moshi.kotlin.codegen.api.TargetType
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import javax.annotation.processing.Messager
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

private val JSON_QUALIFIER = JsonQualifier::class.java
private val JSON = Json::class.asClassName()
private val OBJECT_CLASS = ClassName("java.lang", "Object")
private val VISIBILITY_MODIFIERS = setOf(
    KModifier.INTERNAL,
    KModifier.PRIVATE,
    KModifier.PROTECTED,
    KModifier.PUBLIC
)

private fun Collection<KModifier>.visibility(): KModifier {
  return find { it in VISIBILITY_MODIFIERS } ?: KModifier.PUBLIC
}

@KotlinPoetMetadataPreview
internal fun primaryConstructor(kotlinApi: TypeSpec, elements: Elements): TargetConstructor? {
  val primaryConstructor = kotlinApi.primaryConstructor ?: return null

  val parameters = mutableMapOf<String, TargetParameter>()
  for ((index, parameter) in primaryConstructor.parameters.withIndex()) {
    val name = parameter.name
    parameters[name] = TargetParameter(
        name = name,
        index = index,
        hasDefault = parameter.defaultValue != null,
        qualifiers = parameter.annotations.qualifiers(elements),
        jsonName = parameter.annotations.jsonName() ?: name.escapeDollarSigns()
    )
  }

  return TargetConstructor(parameters, primaryConstructor.modifiers.visibility())
}

/** Returns a target type for `element`, or null if it cannot be used with code gen. */
@KotlinPoetMetadataPreview
internal fun targetType(messager: Messager,
    elements: Elements,
    types: Types,
    element: TypeElement): TargetType? {
  val typeMetadata = element.getAnnotation(Metadata::class.java)
  if (typeMetadata == null) {
    messager.printMessage(
        Diagnostic.Kind.ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class",
        element)
    return null
  }

  val kmClass = try {
    typeMetadata.toImmutableKmClass()
  } catch (e: UnsupportedOperationException) {
    messager.printMessage(
        Diagnostic.Kind.ERROR, "@JsonClass can't be applied to $element: must be a Class type",
        element)
    return null
  }

  when {
    kmClass.isEnum -> {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "@JsonClass with 'generateAdapter = \"true\"' can't be applied to $element: code gen for enums is not supported or necessary",
          element)
      return null
    }
    !kmClass.isClass -> {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class",
          element)
      return null
    }
    kmClass.isInner -> {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "@JsonClass can't be applied to $element: must not be an inner class", element)
      return null
    }
    kmClass.isSealed -> {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "@JsonClass can't be applied to $element: must not be sealed", element)
      return null
    }
    kmClass.isAbstract -> {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "@JsonClass can't be applied to $element: must not be abstract",
          element)
      return null
    }
    kmClass.isLocal -> {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "@JsonClass can't be applied to $element: must not be local",
          element)
      return null
    }
  }

  val elementHandler = ElementsClassInspector.create(elements, types)
  val kotlinApi = kmClass.toTypeSpec(elementHandler)
  val typeVariables = kotlinApi.typeVariables
  val appliedType = AppliedType.get(element)

  val constructor = primaryConstructor(kotlinApi, elements)
  if (constructor == null) {
    messager.printMessage(Diagnostic.Kind.ERROR, "No primary constructor found on $element",
        element)
    return null
  }
  if (constructor.visibility != KModifier.INTERNAL && constructor.visibility != KModifier.PUBLIC) {
    messager.printMessage(Diagnostic.Kind.ERROR, "@JsonClass can't be applied to $element: " +
        "primary constructor is not internal or public", element)
    return null
  }

  val properties = mutableMapOf<String, TargetProperty>()
  for (supertype in appliedType.supertypes(types)) {
    if (supertype.element.asClassName() == OBJECT_CLASS) {
      continue // Don't load properties for java.lang.Object.
    }
    if (supertype.element.kind != ElementKind.CLASS) {
      continue // Don't load properties for interface types.
    }
    if (supertype.element.getAnnotation(Metadata::class.java) == null) {
      messager.printMessage(Diagnostic.Kind.ERROR,
          "@JsonClass can't be applied to $element: supertype $supertype is not a Kotlin type",
          element)
      return null
    }
    val supertypeProperties = if (supertype.element == element) {
      // We've already parsed this api above, reuse it
      declaredProperties(supertype.element, constructor, elementHandler, kotlinApi)
    } else {
      declaredProperties(
          supertype.element, constructor, elementHandler)
    }
    for ((name, property) in supertypeProperties) {
      properties.putIfAbsent(name, property)
    }
  }
  return TargetType(
      typeName = element.asType().asTypeName(),
      constructor = constructor,
      properties = properties,
      typeVariables = typeVariables,
      isDataClass = KModifier.DATA in kotlinApi.modifiers,
      visibility = kotlinApi.modifiers.visibility())
}

/** Returns the properties declared by `typeElement`. */
@KotlinPoetMetadataPreview
private fun declaredProperties(
    typeElement: TypeElement,
    constructor: TargetConstructor,
    elementHandler: ClassInspector,
    kotlinApi: TypeSpec = typeElement.toTypeSpec(elementHandler)
): Map<String, TargetProperty> {

  val result = mutableMapOf<String, TargetProperty>()
  for (property in kotlinApi.propertySpecs) {
    val name = property.name
    val parameter = constructor.parameters[name]
    result[name] = TargetProperty(
        propertySpec = property,
        parameter = parameter,
        visibility = property.modifiers.visibility(),
        jsonName = parameter?.jsonName ?: property.annotations.jsonName()
        ?: name.escapeDollarSigns()
    )
  }

  return result
}

private val TargetProperty.isTransient get() = propertySpec.annotations.any { it.className == Transient::class.asClassName() }
private val TargetProperty.isSettable get() = propertySpec.mutable || parameter != null
private val TargetProperty.isVisible: Boolean
  get() {
    return visibility == KModifier.INTERNAL
        || visibility == KModifier.PROTECTED
        || visibility == KModifier.PUBLIC
  }

/**
 * Returns a generator for this property, or null if either there is an error and this property
 * cannot be used with code gen, or if no codegen is necessary for this property.
 */
internal fun TargetProperty.generator(
    messager: Messager,
    sourceElement: TypeElement,
    elements: Elements
): PropertyGenerator? {
  if (isTransient) {
    if (!hasDefault) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "No default value for transient property $name",
          sourceElement)
      return null
    }
    return PropertyGenerator(this, DelegateKey(type, emptyList()), true)
  }

  if (!isVisible) {
    messager.printMessage(Diagnostic.Kind.ERROR, "property $name is not visible",
        sourceElement)
    return null
  }

  if (!isSettable) {
    return null // This property is not settable. Ignore it.
  }

  // Merge parameter and property annotations
  val qualifiers = parameter?.qualifiers.orEmpty() + propertySpec.annotations.qualifiers(elements)
  for (jsonQualifier in qualifiers) {
    // Check Java types since that covers both Java and Kotlin annotations.
    val annotationElement = elements.getTypeElement(jsonQualifier.className.canonicalName)
        ?: continue
    annotationElement.getAnnotation(Retention::class.java)?.let {
      if (it.value != RetentionPolicy.RUNTIME) {
        messager.printMessage(Diagnostic.Kind.ERROR,
            "JsonQualifier @${jsonQualifier.className.simpleName} must have RUNTIME retention")
      }
    }
    annotationElement.getAnnotation(Target::class.java)?.let {
      if (ElementType.FIELD !in it.value) {
        messager.printMessage(Diagnostic.Kind.ERROR,
            "JsonQualifier @${jsonQualifier.className.simpleName} must support FIELD target")
      }
    }
  }

  val jsonQualifierSpecs = qualifiers.map {
    it.toBuilder()
        .useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD)
        .build()
  }

  return PropertyGenerator(this,
      DelegateKey(type, jsonQualifierSpecs))
}

private fun List<AnnotationSpec>?.qualifiers(elements: Elements): Set<AnnotationSpec> {
  if (this == null) return setOf()
  return filterTo(mutableSetOf()) {
    elements.getTypeElement(it.className.toString()).getAnnotation(JSON_QUALIFIER) != null
  }
}

/** Gross, but we can't extract values from AnnotationSpecs by member names alone. */
private fun List<AnnotationSpec>?.jsonName(): String? {
  if (this == null) return null
  return find { it.className == JSON }?.let {
    it.members[0].toString().removePrefix("name = \"").removeSuffix("\"")
  }
}

private fun String.escapeDollarSigns(): String {
  return replace("\$", "\${\'\$\'}")
}
