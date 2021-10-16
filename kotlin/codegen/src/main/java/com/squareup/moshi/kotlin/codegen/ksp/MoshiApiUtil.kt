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
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.asClassName
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.kotlin.codegen.api.DelegateKey
import com.squareup.moshi.kotlin.codegen.api.PropertyGenerator
import com.squareup.moshi.kotlin.codegen.api.TargetProperty
import com.squareup.moshi.kotlin.codegen.api.rawType

private val VISIBILITY_MODIFIERS = setOf(
  KModifier.INTERNAL,
  KModifier.PRIVATE,
  KModifier.PROTECTED,
  KModifier.PUBLIC
)

internal fun Collection<KModifier>.visibility(): KModifier {
  return find { it in VISIBILITY_MODIFIERS } ?: KModifier.PUBLIC
}

private val TargetProperty.isTransient get() = propertySpec.annotations.any { it.typeName == Transient::class.asClassName() }
private val TargetProperty.isSettable get() = propertySpec.mutable || parameter != null
private val TargetProperty.isVisible: Boolean
  get() {
    return visibility == KModifier.INTERNAL ||
      visibility == KModifier.PROTECTED ||
      visibility == KModifier.PUBLIC
  }

/**
 * Returns a generator for this property, or null if either there is an error and this property
 * cannot be used with code gen, or if no codegen is necessary for this property.
 */
internal fun TargetProperty.generator(
  logger: KSPLogger,
  resolver: Resolver,
  originalType: KSDeclaration
): PropertyGenerator? {
  if (isTransient) {
    if (!hasDefault) {
      logger.error(
        "No default value for transient property $name",
        originalType
      )
      return null
    }
    return PropertyGenerator(this, DelegateKey(type, emptyList()), true)
  }

  if (!isVisible) {
    logger.error(
      "property $name is not visible",
      originalType
    )
    return null
  }

  if (!isSettable) {
    return null // This property is not settable. Ignore it.
  }

  // Merge parameter and property annotations
  val qualifiers = parameter?.qualifiers.orEmpty() + propertySpec.annotations
  for (jsonQualifier in qualifiers) {
    val qualifierRawType = jsonQualifier.typeName.rawType()
    // Check Java types since that covers both Java and Kotlin annotations.
    resolver.getClassDeclarationByName(qualifierRawType.canonicalName)?.let { annotationElement ->
      annotationElement.findAnnotationWithType<Retention>()?.let {
        if (it.value != AnnotationRetention.RUNTIME) {
          logger.error(
            "JsonQualifier @${qualifierRawType.simpleName} must have RUNTIME retention"
          )
        }
      }
      annotationElement.findAnnotationWithType<Target>()?.let {
        if (AnnotationTarget.FIELD !in it.allowedTargets) {
          logger.error(
            "JsonQualifier @${qualifierRawType.simpleName} must support FIELD target"
          )
        }
      }
    }
  }

  val jsonQualifierSpecs = qualifiers.map {
    it.toBuilder()
      .useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD)
      .build()
  }

  return PropertyGenerator(
    this,
    DelegateKey(type, jsonQualifierSpecs)
  )
}

internal val KSClassDeclaration.isJsonQualifier: Boolean
  get() = isAnnotationPresent(JsonQualifier::class)
