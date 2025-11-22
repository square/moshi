/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.squareup.moshi.internal

import java.lang.reflect.GenericDeclaration
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance

/*
 * Moshi wants to offer something like `kotlin.reflect.KType.javaType` as a stable API, but that
 * function is `@ExperimentalStdlibApi`.
 *
 * This file contains a copy-paste of that code, implemented on only non-experimental APIs. It's
 * also modified to use Moshi's `Type` implementations.
 *
 * If/when Kotlin offers a non-experimental API to convert a `KType` to a Java `Type`, we should
 * migrate to that and delete this file.
 */
@SinceKotlin("1.4")
internal val KType.javaType: Type
  get() = computeJavaType()

private fun KType.computeJavaType(forceWrapper: Boolean = false): Type {
  when (val classifier = classifier) {
    is KTypeParameter -> return TypeVariableImpl(classifier)
    is KClass<*> -> {
      val jClass = if (forceWrapper) classifier.javaObjectType else classifier.java
      val arguments = arguments
      if (arguments.isEmpty()) return jClass

      if (jClass.isArray) {
        if (jClass.componentType.isPrimitive) return jClass

        val (variance, elementType) = arguments.singleOrNull()
          ?: throw IllegalArgumentException(
            "kotlin.Array must have exactly one type argument: $this",
          )
        return when (variance) {
          // Array<in ...> is always erased to Object[], and Array<*> is Object[].
          null, KVariance.IN -> jClass
          KVariance.INVARIANT, KVariance.OUT -> {
            val javaElementType = elementType!!.computeJavaType()
            if (javaElementType is Class<*>) jClass else GenericArrayTypeImpl(javaElementType)
          }
        }
      }

      return createPossiblyInnerType(jClass, arguments)
    }
    else -> throw UnsupportedOperationException("Unsupported type classifier: $this")
  }
}

private fun createPossiblyInnerType(jClass: Class<*>, arguments: List<KTypeProjection>): Type {
  val ownerClass = jClass.declaringClass
    ?: return ParameterizedTypeImpl(
      ownerType = null,
      rawType = jClass,
      typeArguments = arguments.map(KTypeProjection::javaType).toTypedArray(),
    )

  if (Modifier.isStatic(jClass.modifiers)) {
    return ParameterizedTypeImpl(
      ownerType = ownerClass,
      rawType = jClass,
      typeArguments = arguments.map(KTypeProjection::javaType).toTypedArray(),
    )
  }

  val n = jClass.typeParameters.size
  return ParameterizedTypeImpl(
    ownerType = createPossiblyInnerType(ownerClass, arguments.subList(n, arguments.size)),
    rawType = jClass,
    typeArguments = arguments.subList(0, n).map(KTypeProjection::javaType).toTypedArray(),
  )
}

private val KTypeProjection.javaType: Type
  get() {
    val variance = variance ?: return WildcardTypeImpl(
      upperBound = Any::class.java,
      lowerBound = null,
    )

    val type = type!!
    // TODO: JvmSuppressWildcards
    return when (variance) {
      KVariance.INVARIANT -> {
        // TODO: declaration-site variance
        type.computeJavaType(forceWrapper = true)
      }
      KVariance.IN -> WildcardTypeImpl(
        upperBound = Any::class.java,
        lowerBound = type.computeJavaType(forceWrapper = true),
      )
      KVariance.OUT -> WildcardTypeImpl(
        upperBound = type.computeJavaType(forceWrapper = true),
        lowerBound = null,
      )
    }
  }

// Suppression of the error is needed for `AnnotatedType[] getAnnotatedBounds()` which is impossible to implement on JDK 6
// because `AnnotatedType` has only appeared in JDK 8.
@Suppress("ABSTRACT_MEMBER_NOT_IMPLEMENTED")
private class TypeVariableImpl(private val typeParameter: KTypeParameter) :
  TypeVariable<GenericDeclaration> {
  override fun getName(): String = typeParameter.name

  override fun getGenericDeclaration(): GenericDeclaration = TODO(
    "getGenericDeclaration() is not yet supported for type variables created from KType: $typeParameter",
  )

  override fun getBounds(): Array<Type> = typeParameter.upperBounds.map {
    it.computeJavaType(forceWrapper = true)
  }.toTypedArray()

  override fun equals(other: Any?): Boolean =
    other is TypeVariable<*> && name == other.name && genericDeclaration == other.genericDeclaration

  override fun hashCode(): Int = name.hashCode() xor genericDeclaration.hashCode()

  override fun toString(): String = name
}
