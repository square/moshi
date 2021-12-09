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

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.ClassKind.CLASS
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.kotlin.codegen.api.TargetConstructor
import com.squareup.moshi.kotlin.codegen.api.TargetParameter
import com.squareup.moshi.kotlin.codegen.api.TargetProperty
import com.squareup.moshi.kotlin.codegen.api.TargetType
import com.squareup.moshi.kotlin.codegen.api.unwrapTypeAlias

/** Returns a target type for [type] or null if it cannot be used with code gen. */
internal fun targetType(
  type: KSDeclaration,
  resolver: Resolver,
  logger: KSPLogger,
): TargetType? {
  if (type !is KSClassDeclaration) {
    logger.error("@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must be a Kotlin class", type)
    return null
  }
  logger.check(type.classKind != ClassKind.ENUM_CLASS, type) {
    "@JsonClass with 'generateAdapter = \"true\"' can't be applied to ${type.qualifiedName?.asString()}: code gen for enums is not supported or necessary"
  }
  logger.check(type.classKind == CLASS && type.origin == Origin.KOTLIN, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must be a Kotlin class"
  }
  logger.check(Modifier.INNER !in type.modifiers, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be an inner class"
  }
  logger.check(Modifier.SEALED !in type.modifiers, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be sealed"
  }
  logger.check(Modifier.ABSTRACT !in type.modifiers, type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be abstract"
  }
  logger.check(!type.isLocal(), type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must not be local"
  }
  logger.check(type.isPublic() || type.isInternal(), type) {
    "@JsonClass can't be applied to ${type.qualifiedName?.asString()}: must be internal or public"
  }

  val classTypeParamsResolver = type.typeParameters.toTypeParameterResolver(
    sourceTypeHint = type.qualifiedName!!.asString()
  )
  val typeVariables = type.typeParameters.map { it.toTypeVariableName(classTypeParamsResolver) }
  val appliedType = AppliedType(type)

  val constructor = primaryConstructor(resolver, type, classTypeParamsResolver, logger)
    ?: run {
      logger.error("No primary constructor found on $type", type)
      return null
    }
  if (constructor.visibility != KModifier.INTERNAL && constructor.visibility != KModifier.PUBLIC) {
    logger.error(
      "@JsonClass can't be applied to $type: " +
        "primary constructor is not internal or public",
      type
    )
    return null
  }

  val properties = mutableMapOf<String, TargetProperty>()

  val originalType = appliedType.type
  for (superclass in appliedType.superclasses(resolver)) {
    val classDecl = superclass.type
    if (!classDecl.isKotlinClass()) {
      logger.error(
        """
        @JsonClass can't be applied to $type: supertype $superclass is not a Kotlin type.
        Origin=${classDecl.origin}
        Annotations=${classDecl.annotations.joinToString(prefix = "[", postfix = "]") { it.shortName.getShortName() }}
        """.trimIndent(),
        type
      )
      return null
    }
    val supertypeProperties = declaredProperties(
      constructor = constructor,
      originalType = originalType,
      classDecl = classDecl,
      resolver = resolver,
      typeParameterResolver = classDecl.typeParameters
        .toTypeParameterResolver(classTypeParamsResolver)
    )
    for ((name, property) in supertypeProperties) {
      properties.putIfAbsent(name, property)
    }
  }
  val visibility = type.getVisibility().toKModifier() ?: KModifier.PUBLIC
  // If any class in the enclosing class hierarchy is internal, they must all have internal
  // generated adapters.
  val resolvedVisibility = if (visibility == KModifier.INTERNAL) {
    // Our nested type is already internal, no need to search
    visibility
  } else {
    // Implicitly public, so now look up the hierarchy
    val forceInternal = generateSequence<KSDeclaration>(type) { it.parentDeclaration }
      .filterIsInstance<KSClassDeclaration>()
      .any { it.isInternal() }
    if (forceInternal) KModifier.INTERNAL else visibility
  }
  return TargetType(
    typeName = type.toClassName().withTypeArguments(typeVariables),
    constructor = constructor,
    properties = properties,
    typeVariables = typeVariables,
    isDataClass = Modifier.DATA in type.modifiers,
    visibility = resolvedVisibility,
  )
}

private fun ClassName.withTypeArguments(arguments: List<TypeName>): TypeName {
  return if (arguments.isEmpty()) {
    this
  } else {
    this.parameterizedBy(arguments)
  }
}

@OptIn(KspExperimental::class)
internal fun primaryConstructor(
  resolver: Resolver,
  targetType: KSClassDeclaration,
  typeParameterResolver: TypeParameterResolver,
  logger: KSPLogger
): TargetConstructor? {
  val primaryConstructor = targetType.primaryConstructor ?: return null

  val parameters = LinkedHashMap<String, TargetParameter>()
  for ((index, parameter) in primaryConstructor.parameters.withIndex()) {
    val name = parameter.name!!.getShortName()
    parameters[name] = TargetParameter(
      name = name,
      index = index,
      type = parameter.type.toTypeName(typeParameterResolver),
      hasDefault = parameter.hasDefault,
      qualifiers = parameter.qualifiers(resolver),
      jsonName = parameter.jsonName()
    )
  }

  val kmConstructorSignature: String = resolver.mapToJvmSignature(primaryConstructor)
    ?: run {
      logger.error("No primary constructor found.", primaryConstructor)
      return null
    }
  return TargetConstructor(
    parameters,
    primaryConstructor.getVisibility().toKModifier() ?: KModifier.PUBLIC,
    kmConstructorSignature
  )
}

private fun KSAnnotated?.qualifiers(resolver: Resolver): Set<AnnotationSpec> {
  if (this == null) return setOf()
  return annotations
    .filter {
      it.annotationType.resolve().declaration.isAnnotationPresent(JsonQualifier::class)
    }
    .mapTo(mutableSetOf()) {
      it.toAnnotationSpec(resolver)
    }
}

private fun KSAnnotated?.jsonName(): String? {
  return this?.findAnnotationWithType<Json>()?.name?.takeUnless { it == Json.UNSET_NAME }
}

private fun KSAnnotated?.jsonIgnore(): Boolean {
  return this?.findAnnotationWithType<Json>()?.ignore ?: false
}

@OptIn(KspExperimental::class)
private fun declaredProperties(
  constructor: TargetConstructor,
  originalType: KSClassDeclaration,
  classDecl: KSClassDeclaration,
  resolver: Resolver,
  typeParameterResolver: TypeParameterResolver,
): Map<String, TargetProperty> {
  val result = mutableMapOf<String, TargetProperty>()
  for (property in classDecl.getDeclaredProperties()) {
    val initialType = property.type.resolve()
    val resolvedType = if (initialType.declaration is KSTypeParameter) {
      property.asMemberOf(originalType.asType())
    } else {
      initialType
    }
    val propertySpec = property.toPropertySpec(resolver, resolvedType, typeParameterResolver)
    val name = propertySpec.name
    val parameter = constructor.parameters[name]
    val isTransient = Modifier.JAVA_TRANSIENT in property.modifiers ||
      property.isAnnotationPresent(Transient::class) ||
      Modifier.JAVA_TRANSIENT in resolver.effectiveJavaModifiers(property)
    result[name] = TargetProperty(
      propertySpec = propertySpec,
      parameter = parameter,
      visibility = property.getVisibility().toKModifier() ?: KModifier.PUBLIC,
      jsonName = parameter?.jsonName ?: property.jsonName() ?: name,
      jsonIgnore = isTransient || parameter?.jsonIgnore == true || property.jsonIgnore()
    )
  }

  return result
}

private fun KSPropertyDeclaration.toPropertySpec(
  resolver: Resolver,
  resolvedType: KSType,
  typeParameterResolver: TypeParameterResolver
): PropertySpec {
  return PropertySpec.builder(
    name = simpleName.getShortName(),
    type = resolvedType.toTypeName(typeParameterResolver).unwrapTypeAlias()
  )
    .mutable(isMutable)
    .addModifiers(modifiers.map { KModifier.valueOf(it.name) })
    .apply {
      addAnnotations(
        this@toPropertySpec.annotations
          .mapNotNull {
            if ((it.annotationType.resolve().unwrapTypeAlias().declaration as KSClassDeclaration).isJsonQualifier
            ) {
              it.toAnnotationSpec(resolver)
            } else {
              null
            }
          }
          .asIterable()
      )
    }
    .build()
}
