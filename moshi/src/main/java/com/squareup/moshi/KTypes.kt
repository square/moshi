@file:JvmName("KTypes")
package com.squareup.moshi

import com.squareup.moshi.internal.KTypeImpl
import com.squareup.moshi.internal.KTypeParameterImpl
import com.squareup.moshi.internal.markNotNull
import com.squareup.moshi.internal.stripWildcards
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.KVariance.INVARIANT
import kotlin.reflect.javaType
import java.lang.reflect.Array as JavaArray

/**
 * Creates a new [KType] representation of this [Type] for use with Moshi serialization. Note that
 * [wildcards][WildcardType] are stripped away in [type projections][KTypeProjection] as they are
 * not relevant for serialization and are also not standalone [KType] subtypes in Kotlin.
 */
public fun Type.toKType(
  isMarkedNullable: Boolean = true,
  annotations: List<Annotation> = emptyList()
): KType {
  return when (this) {
    is Class<*> -> KTypeImpl(kotlin, emptyList(), isMarkedNullable, annotations, isPlatformType = true)
    is ParameterizedType -> KTypeImpl(
      classifier = (rawType as Class<*>).kotlin,
      arguments = actualTypeArguments.map { it.toKTypeProjection() },
      isMarkedNullable = isMarkedNullable,
      annotations = annotations,
      isPlatformType = true
    )
    is GenericArrayType -> {
      KTypeImpl(
        classifier = rawType.kotlin,
        arguments = listOf(genericComponentType.toKTypeProjection()),
        isMarkedNullable = isMarkedNullable,
        annotations = annotations,
        isPlatformType = true
      )
    }
    is WildcardType -> stripWildcards().toKType(isMarkedNullable, annotations)
    is TypeVariable<*> -> KTypeImpl(
      classifier = KTypeParameterImpl(false, name, bounds.map { it.toKType() }, INVARIANT),
      arguments = emptyList(),
      isMarkedNullable = isMarkedNullable,
      annotations = annotations,
      isPlatformType = true
    )
    else -> throw IllegalArgumentException("Unsupported type: $this")
  }
}

/**
 * Creates a new [KTypeProjection] representation of this [Type] for use in [KType.arguments].
 */
public fun Type.toKTypeProjection(): KTypeProjection {
  return when (this) {
    is Class<*>, is ParameterizedType, is TypeVariable<*> -> KTypeProjection.invariant(toKType())
    is WildcardType -> {
      val lowerBounds = lowerBounds
      val upperBounds = upperBounds
      if (lowerBounds.isEmpty() && upperBounds.isEmpty()) {
        return KTypeProjection.STAR
      }
      return if (lowerBounds.isNotEmpty()) {
        KTypeProjection.contravariant(lowerBounds[0].toKType())
      } else {
        KTypeProjection.invariant(upperBounds[0].toKType())
      }
    }
    else -> {
      throw NotImplementedError("Unsupported type: $this")
    }
  }
}

/** Returns a [KType] representation of this [KClass]. */
public fun KClass<*>.asKType(isMarkedNullable: Boolean, annotations: List<Annotation> = emptyList()): KType =
  KTypeImpl(this, emptyList(), isMarkedNullable, annotations, isPlatformType = false)

/** Returns a [KType] representation of this [KClass]. */
public fun KType.copy(
  isMarkedNullable: Boolean = this.isMarkedNullable,
  annotations: List<Annotation> = this.annotations
): KType = KTypeImpl(this.classifier, this.arguments, isMarkedNullable, annotations, isPlatformType = false)

/** Returns a [KType] of this [KClass] with the given [arguments]. */
public fun KClass<*>.parameterizedBy(
  vararg arguments: KTypeProjection,
): KType = KTypeImpl(this, arguments.toList(), false, emptyList(), isPlatformType = false)

/** Returns a [KTypeProjection] representation of this [KClass] with the given [variance]. */
public fun KClass<*>.asKTypeProjection(variance: KVariance = INVARIANT): KTypeProjection =
  asKType(false).asKTypeProjection(variance)

/** Returns a [KTypeProjection] representation of this [KType] with the given [variance]. */
public fun KType.asKTypeProjection(variance: KVariance = INVARIANT): KTypeProjection =
  KTypeProjection(variance, this)

/** Returns an [Array] [KType] with [this] as its single [argument][KType.arguments]. */
@OptIn(ExperimentalStdlibApi::class)
public fun KType.asArrayKType(variance: KVariance): KType {
  // Unfortunate but necessary for Java
  val componentType = javaType
  val classifier = JavaArray.newInstance(Types.getRawType(componentType), 0).javaClass.kotlin
  val argument = KTypeProjection(variance = variance, type = this)
  return KTypeImpl(
    classifier = classifier,
    arguments = listOf(argument),
    isMarkedNullable = isMarkedNullable,
    annotations = annotations,
    isPlatformType = false
  )
}

/** Returns true if [this] and [other] are equal. */
public fun KType?.isFunctionallyEqualTo(other: KType?): Boolean {
  if (this === other) {
    return true // Also handles (a == null && b == null).
  }

  markNotNull(this)
  markNotNull(other)

  if (isMarkedNullable != other.isMarkedNullable) return false
  if (!arguments.contentEquals(other.arguments) { a, b -> a.type.isFunctionallyEqualTo(b.type) }) return false

  // This isn't a supported type.
  when (val classifier = classifier) {
    is KClass<*> -> {
      return classifier == other.classifier
    }
    is KTypeParameter -> {
      val otherClassifier = other.classifier
      if (otherClassifier !is KTypeParameter) return false
      // TODO Use a plain KTypeParameter.equals again once https://youtrack.jetbrains.com/issue/KT-39661 is fixed
      return (classifier.upperBounds.contentEquals(otherClassifier.upperBounds, KType::isFunctionallyEqualTo) && (classifier.name == otherClassifier.name))
    }
    else -> return false // This isn't a supported type.
  }
}

private fun <T> List<T>.contentEquals(other: List<T>, comparator: (a: T, b: T) -> Boolean): Boolean {
  if (size != other.size) return false
  for (i in indices) {
    val arg = get(i)
    val otherArg = other[i]
    // TODO do we care about variance?
    if (!comparator(arg, otherArg)) return false
  }
  return true
}

public val KType.rawType: KClass<*> get() {
  return when (val classifier = classifier) {
    is KClass<*> -> classifier
    is KTypeParameter -> {
      // We could use the variable's bounds, but that won't work if there are multiple. having a raw
      // type that's more general than necessary is okay.
      Any::class
    }
    else -> error("Unrecognized KType: $this")
  }
}
