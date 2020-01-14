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
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.metadata.ImmutableKmConstructor
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.squareup.kotlinpoet.metadata.isAbstract
import com.squareup.kotlinpoet.metadata.isClass
import com.squareup.kotlinpoet.metadata.isEnum
import com.squareup.kotlinpoet.metadata.isInner
import com.squareup.kotlinpoet.metadata.isInternal
import com.squareup.kotlinpoet.metadata.isLocal
import com.squareup.kotlinpoet.metadata.isPublic
import com.squareup.kotlinpoet.metadata.isSealed
import com.squareup.kotlinpoet.metadata.specs.TypeNameAliasTag
import com.squareup.kotlinpoet.tag
import com.squareup.moshi.Json
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.kotlin.codegen.api.DelegateKey
import com.squareup.moshi.kotlin.codegen.api.PropertyGenerator
import com.squareup.moshi.kotlin.codegen.api.TargetConstructor
import com.squareup.moshi.kotlin.codegen.api.TargetParameter
import com.squareup.moshi.kotlin.codegen.api.TargetProperty
import com.squareup.moshi.kotlin.codegen.api.TargetType
import com.squareup.moshi.kotlin.codegen.api.mapTypes
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.TreeSet
import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic.Kind.ERROR

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
internal fun primaryConstructor(
    targetElement: TypeElement,
    kotlinApi: TypeSpec,
    elements: Elements,
    messager: Messager
): TargetConstructor? {
  val primaryConstructor = kotlinApi.primaryConstructor ?: return null

  val parameters = LinkedHashMap<String, TargetParameter>()
  for ((index, parameter) in primaryConstructor.parameters.withIndex()) {
    val name = parameter.name
    parameters[name] = TargetParameter(
        name = name,
        index = index,
        type = parameter.type,
        hasDefault = parameter.defaultValue != null,
        qualifiers = parameter.annotations.qualifiers(elements),
        jsonName = parameter.annotations.jsonName()
    )
  }

  val kmConstructorSignature = primaryConstructor.tag<ImmutableKmConstructor>()?.signature?.toString()
      ?: run {
        messager.printMessage(ERROR, "No KmConstructor found for primary constructor.",
            targetElement)
        null
      }
  return TargetConstructor(parameters, primaryConstructor.modifiers.visibility(),
      kmConstructorSignature)
}

/** Returns a target type for `element`, or null if it cannot be used with code gen. */
@KotlinPoetMetadataPreview
internal fun targetType(messager: Messager,
    elements: Elements,
    types: Types,
    element: TypeElement,
    cachedClassInspector: MoshiCachedClassInspector
): TargetType? {
  val typeMetadata = element.getAnnotation(Metadata::class.java)
  if (typeMetadata == null) {
    messager.printMessage(
        ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class",
        element)
    return null
  }

  val kmClass = try {
    cachedClassInspector.toImmutableKmClass(typeMetadata)
  } catch (e: UnsupportedOperationException) {
    messager.printMessage(
        ERROR, "@JsonClass can't be applied to $element: must be a Class type",
        element)
    return null
  }

  when {
    kmClass.isEnum -> {
      messager.printMessage(
          ERROR,
          "@JsonClass with 'generateAdapter = \"true\"' can't be applied to $element: code gen for enums is not supported or necessary",
          element)
      return null
    }
    !kmClass.isClass -> {
      messager.printMessage(
          ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class",
          element)
      return null
    }
    kmClass.isInner -> {
      messager.printMessage(
          ERROR,
          "@JsonClass can't be applied to $element: must not be an inner class", element)
      return null
    }
    kmClass.isSealed -> {
      messager.printMessage(
          ERROR, "@JsonClass can't be applied to $element: must not be sealed",
          element)
      return null
    }
    kmClass.isAbstract -> {
      messager.printMessage(
          ERROR, "@JsonClass can't be applied to $element: must not be abstract",
          element)
      return null
    }
    kmClass.isLocal -> {
      messager.printMessage(
          ERROR, "@JsonClass can't be applied to $element: must not be local",
          element)
      return null
    }
    !kmClass.isPublic && !kmClass.isInternal -> {
      messager.printMessage(
          ERROR,
          "@JsonClass can't be applied to $element: must be internal or public",
          element)
      return null
    }
  }

  val kotlinApi = cachedClassInspector.toTypeSpec(kmClass)
  val typeVariables = kotlinApi.typeVariables
  val appliedType = AppliedType.get(element)

  val constructor = primaryConstructor(element, kotlinApi, elements, messager)
  if (constructor == null) {
    messager.printMessage(ERROR, "No primary constructor found on $element",
        element)
    return null
  }
  if (constructor.visibility != KModifier.INTERNAL && constructor.visibility != KModifier.PUBLIC) {
    messager.printMessage(ERROR, "@JsonClass can't be applied to $element: " +
        "primary constructor is not internal or public", element)
    return null
  }

  val properties = mutableMapOf<String, TargetProperty>()
  val superTypes = appliedType.supertypes(types)
      .filterNot { supertype ->
        supertype.element.asClassName() == OBJECT_CLASS || // Don't load properties for java.lang.Object.
            supertype.element.kind != ElementKind.CLASS  // Don't load properties for interface types.
      }
      .onEach { supertype ->
        if (supertype.element.getAnnotation(Metadata::class.java) == null) {
          messager.printMessage(ERROR,
              "@JsonClass can't be applied to $element: supertype $supertype is not a Kotlin type",
              element)
          return null
        }
      }
      .associateWithTo(LinkedHashMap()) { supertype ->
        // Load the kotlin API cache into memory eagerly so we can reuse the parsed APIs
        if (supertype.element == element) {
          // We've already parsed this api above, reuse it
          kotlinApi
        } else {
          cachedClassInspector.toTypeSpec(supertype.element)
        }
      }
  for (supertypeApi in superTypes.values) {
    val supertypeProperties = declaredProperties(constructor, supertypeApi)
    for ((name, property) in supertypeProperties) {
      properties.putIfAbsent(name, property)
    }
  }
  val visibility = kotlinApi.modifiers.visibility()
  // If any class in the enclosing class hierarchy is internal, they must all have internal
  // generated adapters.
  val resolvedVisibility = if (visibility == KModifier.INTERNAL) {
    // Our nested type is already internal, no need to search
    visibility
  } else {
    // Implicitly public, so now look up the hierarchy
    val forceInternal = generateSequence<Element>(element) { it.enclosingElement }
        .filterIsInstance<TypeElement>()
        .map { cachedClassInspector.toImmutableKmClass(it.metadata) }
        .any { it.isInternal }
    if (forceInternal) KModifier.INTERNAL else visibility
  }
  return TargetType(
      typeName = element.asType().asTypeName(),
      constructor = constructor,
      properties = properties,
      typeVariables = typeVariables,
      isDataClass = KModifier.DATA in kotlinApi.modifiers,
      visibility = resolvedVisibility)
}

/** Returns the properties declared by `typeElement`. */
@KotlinPoetMetadataPreview
private fun declaredProperties(
    constructor: TargetConstructor,
    kotlinApi: TypeSpec
): Map<String, TargetProperty> {

  val result = mutableMapOf<String, TargetProperty>()
  for (initialProperty in kotlinApi.propertySpecs) {
    val property = initialProperty.toBuilder(type = initialProperty.type.unwrapTypeAlias()).build()
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
          ERROR, "No default value for transient property $name",
          sourceElement)
      return null
    }
    return PropertyGenerator(this, DelegateKey(type, emptyList()), true)
  }

  if (!isVisible) {
    messager.printMessage(ERROR, "property $name is not visible",
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
        messager.printMessage(ERROR,
            "JsonQualifier @${jsonQualifier.className.simpleName} must have RUNTIME retention")
      }
    }
    annotationElement.getAnnotation(Target::class.java)?.let {
      if (ElementType.FIELD !in it.value) {
        messager.printMessage(ERROR,
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

private fun List<AnnotationSpec>?.jsonName(): String? {
  if (this == null) return null
  return find { it.className == JSON }?.let { annotation ->
    val mirror = requireNotNull(annotation.tag<AnnotationMirror>()) {
      "Could not get the annotation mirror from the annotation spec"
    }
    mirror.elementValues.entries.single {
      it.key.simpleName.contentEquals("name")
    }.value.value as String
  }
}

private fun String.escapeDollarSigns(): String {
  return replace("\$", "\${\'\$\'}")
}

internal fun TypeName.unwrapTypeAlias(): TypeName {
  return mapTypes<ClassName> {
    tag<TypeNameAliasTag>()?.type?.let { unwrappedType ->
      // If any type is nullable, then the whole thing is nullable
      var isAnyNullable = isNullable
      // Keep track of all annotations across type levels. Sort them too for consistency.
      val runningAnnotations = TreeSet<AnnotationSpec>(compareBy { it.toString() }).apply {
        addAll(annotations)
      }
      val nestedUnwrappedType = unwrappedType.unwrapTypeAlias()
      runningAnnotations.addAll(nestedUnwrappedType.annotations)
      isAnyNullable = isAnyNullable || nestedUnwrappedType.isNullable
      nestedUnwrappedType.copy(nullable = isAnyNullable, annotations = runningAnnotations.toList())
    }
  }
}

internal val TypeElement.metadata: Metadata
  get() {
    return getAnnotation(Metadata::class.java)
        ?: throw IllegalStateException("Not a kotlin type! $this")
  }
