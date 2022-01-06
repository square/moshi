/*
 * Copyright (C) 2014 Square, Inc.
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
@file:JvmName("Util")
@file:Suppress("unused", "MemberVisibilityCanBePrivate")
package com.squareup.moshi.internal

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.asArrayType
import com.squareup.moshi.rawType
import java.lang.ClassNotFoundException
import java.lang.Error
import java.lang.IllegalAccessException
import java.lang.IllegalStateException
import java.lang.InstantiationException
import java.lang.NoSuchMethodException
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.lang.Void
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Constructor
import java.lang.reflect.GenericArrayType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.Collections
import java.util.LinkedHashSet
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@JvmField public val NO_ANNOTATIONS: Set<Annotation> = emptySet()
@JvmField public val EMPTY_TYPE_ARRAY: Array<Type> = arrayOf()

@Suppress("UNCHECKED_CAST")
private val METADATA: Class<out Annotation>? = try {
  Class.forName(kotlinMetadataClassName) as Class<out Annotation>
} catch (ignored: ClassNotFoundException) {
  null
}

// We look up the constructor marker separately because Metadata might be (justifiably)
// stripped by R8/Proguard but the DefaultConstructorMarker is still present.
public val DEFAULT_CONSTRUCTOR_MARKER: Class<*>? = try {
  Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
} catch (ignored: ClassNotFoundException) {
  null
}

/** A map from primitive types to their corresponding wrapper types. */
private val PRIMITIVE_TO_WRAPPER_TYPE: Map<Class<*>, Class<*>> = buildMap(16) {
  put(Boolean::class.javaPrimitiveType!!, Boolean::class.java)
  put(Byte::class.javaPrimitiveType!!, Byte::class.java)
  put(Char::class.javaPrimitiveType!!, Char::class.java)
  put(Double::class.javaPrimitiveType!!, Double::class.java)
  put(Float::class.javaPrimitiveType!!, Float::class.java)
  put(Int::class.javaPrimitiveType!!, Int::class.java)
  put(Long::class.javaPrimitiveType!!, Long::class.java)
  put(Short::class.javaPrimitiveType!!, Short::class.java)
  put(Void.TYPE, Void::class.java)
}

// Extracted as a method with a keep rule to prevent R8 from keeping Kotlin Metadata
private val kotlinMetadataClassName: String
  get() = "kotlin.Metadata"

public fun AnnotatedElement.jsonName(declaredName: String): String {
  return getAnnotation(Json::class.java).jsonName(declaredName)
}

public fun Json?.jsonName(declaredName: String): String {
  if (this == null) return declaredName
  val annotationName: String = name
  return if (Json.UNSET_NAME == annotationName) declaredName else annotationName
}

public fun typesMatch(pattern: Type, candidate: Type): Boolean {
  // TODO: permit raw types (like Set.class) to match non-raw candidates (like Set<Long>).
  return Types.equals(pattern, candidate)
}

public val AnnotatedElement.jsonAnnotations: Set<Annotation>
  get() = annotations.jsonAnnotations

public val Array<Annotation>.jsonAnnotations: Set<Annotation>
  get() {
    var result: MutableSet<Annotation>? = null
    for (annotation in this) {
      @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
      if ((annotation as java.lang.annotation.Annotation).annotationType()
        .isAnnotationPresent(JsonQualifier::class.java)
      ) {
        if (result == null) result = LinkedHashSet()
        result.add(annotation)
      }
    }
    return if (result != null) Collections.unmodifiableSet(result) else NO_ANNOTATIONS
  }

public fun Set<Annotation>.isAnnotationPresent(
  annotationClass: Class<out Annotation>
): Boolean {
  if (isEmpty()) return false // Save an iterator in the common case.
  for (annotation in this) {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    if ((annotation as java.lang.annotation.Annotation).annotationType() == annotationClass) return true
  }
  return false
}

/** Returns true if `annotations` has any annotation whose simple name is Nullable. */
public val Array<Annotation>.hasNullable: Boolean
  get() {
    for (annotation in this) {
      @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
      if ((annotation as java.lang.annotation.Annotation).annotationType().simpleName == "Nullable") {
        return true
      }
    }
    return false
  }

/**
 * Returns true if `rawType` is built in. We don't reflect on private fields of platform
 * types because they're unspecified and likely to be different on Java vs. Android.
 */
public val Class<*>.isPlatformType: Boolean
  get() {
    val name = name
    return (
      name.startsWith("android.") ||
        name.startsWith("androidx.") ||
        name.startsWith("java.") ||
        name.startsWith("javax.") ||
        name.startsWith("kotlin.") ||
        name.startsWith("kotlinx.") ||
        name.startsWith("scala.")
      )
  }

/** Throws the cause of `e`, wrapping it if it is checked. */
public fun InvocationTargetException.rethrowCause(): RuntimeException {
  val cause = targetException
  if (cause is RuntimeException) throw cause
  if (cause is Error) throw cause
  throw RuntimeException(cause)
}

/**
 * Returns a type that is functionally equal but not necessarily equal according to [[Object.equals()]][Object.equals].
 */
public fun Type.canonicalize(): Type {
  return when (this) {
    is Class<*> -> {
      if (isArray) GenericArrayTypeImpl(this@canonicalize.componentType.canonicalize()) else this
    }
    is ParameterizedType -> {
      if (this is ParameterizedTypeImpl) return this
      ParameterizedTypeImpl(ownerType, rawType, *actualTypeArguments)
    }
    is GenericArrayType -> {
      if (this is GenericArrayTypeImpl) return this
      GenericArrayTypeImpl(genericComponentType)
    }
    is WildcardType -> {
      if (this is WildcardTypeImpl) return this
      WildcardTypeImpl(upperBounds, lowerBounds)
    }
    else -> this // This type is unsupported!
  }
}

/** If type is a "? extends X" wildcard, returns X; otherwise returns type unchanged. */
public fun Type.removeSubtypeWildcard(): Type {
  if (this !is WildcardType) return this
  val lowerBounds = lowerBounds
  if (lowerBounds.isNotEmpty()) return this
  val upperBounds = upperBounds
  require(upperBounds.size == 1)
  return upperBounds[0]
}

public fun Type.resolve(context: Type, contextRawType: Class<*>): Type {
  return this.resolve(context, contextRawType, LinkedHashSet())
}

private fun Type.resolve(
  context: Type,
  contextRawType: Class<*>,
  visitedTypeVariables: MutableCollection<TypeVariable<*>>
): Type {
  // This implementation is made a little more complicated in an attempt to avoid object-creation.
  var toResolve = this
  while (true) {
    when {
      toResolve is TypeVariable<*> -> {
        val typeVariable = toResolve
        if (typeVariable in visitedTypeVariables) {
          // cannot reduce due to infinite recursion
          return toResolve
        } else {
          visitedTypeVariables += typeVariable
        }
        toResolve = resolveTypeVariable(context, contextRawType, typeVariable)
        if (toResolve === typeVariable) return toResolve
      }
      toResolve is Class<*> && toResolve.isArray -> {
        val original = toResolve
        val componentType: Type = original.componentType
        val newComponentType = componentType.resolve(context, contextRawType, visitedTypeVariables)
        return if (componentType === newComponentType) original else newComponentType.asArrayType()
      }
      toResolve is GenericArrayType -> {
        val original = toResolve
        val componentType = original.genericComponentType
        val newComponentType = componentType.resolve(context, contextRawType, visitedTypeVariables)
        return if (componentType === newComponentType) original else newComponentType.asArrayType()
      }
      toResolve is ParameterizedType -> {
        val original = toResolve
        val ownerType: Type? = original.ownerType
        val newOwnerType = ownerType?.let {
          ownerType.resolve(context, contextRawType, visitedTypeVariables)
        }
        var changed = newOwnerType !== ownerType
        var args = original.actualTypeArguments
        for (t in args.indices) {
          val resolvedTypeArgument = args[t].resolve(context, contextRawType, visitedTypeVariables)
          if (resolvedTypeArgument !== args[t]) {
            if (!changed) {
              args = args.clone()
              changed = true
            }
            args[t] = resolvedTypeArgument
          }
        }
        return if (changed) ParameterizedTypeImpl(newOwnerType, original.rawType, *args) else original
      }
      toResolve is WildcardType -> {
        val original = toResolve
        val originalLowerBound = original.lowerBounds
        val originalUpperBound = original.upperBounds
        if (originalLowerBound.size == 1) {
          val lowerBound = originalLowerBound[0].resolve(context, contextRawType, visitedTypeVariables)
          if (lowerBound !== originalLowerBound[0]) {
            return Types.supertypeOf(lowerBound)
          }
        } else if (originalUpperBound.size == 1) {
          val upperBound = originalUpperBound[0].resolve(context, contextRawType, visitedTypeVariables)
          if (upperBound !== originalUpperBound[0]) {
            return Types.subtypeOf(upperBound)
          }
        }
        return original
      }
      else -> return toResolve
    }
  }
}

public fun resolveTypeVariable(context: Type, contextRawType: Class<*>, unknown: TypeVariable<*>): Type {
  val declaredByRaw = declaringClassOf(unknown) ?: return unknown

  // We can't reduce this further.
  val declaredBy = getGenericSupertype(context, contextRawType, declaredByRaw)
  if (declaredBy is ParameterizedType) {
    val index = declaredByRaw.typeParameters.indexOf(unknown)
    return declaredBy.actualTypeArguments[index]
  }
  return unknown
}

/**
 * Returns the generic supertype for `supertype`. For example, given a class `IntegerSet`, the result for when supertype is `Set.class` is `Set<Integer>` and the
 * result when the supertype is `Collection.class` is `Collection<Integer>`.
 */
public fun getGenericSupertype(context: Type, rawTypeInitial: Class<*>, toResolve: Class<*>): Type {
  var rawType = rawTypeInitial
  if (toResolve == rawType) {
    return context
  }

  // we skip searching through interfaces if unknown is an interface
  if (toResolve.isInterface) {
    val interfaces = rawType.interfaces
    for (i in interfaces.indices) {
      if (interfaces[i] == toResolve) {
        return rawType.genericInterfaces[i]
      } else if (toResolve.isAssignableFrom(interfaces[i])) {
        return getGenericSupertype(rawType.genericInterfaces[i], interfaces[i], toResolve)
      }
    }
  }

  // check our supertypes
  if (!rawType.isInterface) {
    while (rawType != Any::class.java) {
      val rawSupertype = rawType.superclass
      if (rawSupertype == toResolve) {
        return rawType.genericSuperclass
      } else if (toResolve.isAssignableFrom(rawSupertype)) {
        return getGenericSupertype(rawType.genericSuperclass, rawSupertype, toResolve)
      }
      rawType = rawSupertype
    }
  }

  // we can't resolve this further
  return toResolve
}

public val Any?.hashCodeOrZero: Int
  get() {
    return this?.hashCode() ?: 0
  }

public fun Type.typeToString(): String {
  return if (this is Class<*>) name else toString()
}

/**
 * Returns the declaring class of `typeVariable`, or `null` if it was not declared by
 * a class.
 */
public fun declaringClassOf(typeVariable: TypeVariable<*>): Class<*>? {
  val genericDeclaration = typeVariable.genericDeclaration
  return if (genericDeclaration is Class<*>) genericDeclaration else null
}

public fun Type.checkNotPrimitive() {
  require(!(this is Class<*> && isPrimitive)) { "Unexpected primitive $this. Use the boxed type." }
}

public fun Type.toStringWithAnnotations(annotations: Set<Annotation>): String {
  return toString() + if (annotations.isEmpty()) " (with no annotations)" else " annotated $annotations"
}

/**
 * Loads the generated JsonAdapter for classes annotated [JsonClass]. This works because it
 * uses the same naming conventions as `JsonClassCodeGenProcessor`.
 */
public fun Moshi.generatedAdapter(
  type: Type,
  rawType: Class<*>
): JsonAdapter<*>? {
  val jsonClass = rawType.getAnnotation(JsonClass::class.java)
  if (jsonClass == null || !jsonClass.generateAdapter) {
    return null
  }
  val adapterClassName = Types.generatedJsonAdapterName(rawType.name)
  var possiblyFoundAdapter: Class<out JsonAdapter<*>>? = null
  return try {
    @Suppress("UNCHECKED_CAST")
    val adapterClass = Class.forName(adapterClassName, true, rawType.classLoader) as Class<out JsonAdapter<*>>
    possiblyFoundAdapter = adapterClass
    var constructor: Constructor<out JsonAdapter<*>>
    var args: Array<Any>
    if (type is ParameterizedType) {
      val typeArgs = type.actualTypeArguments
      try {
        // Common case first
        constructor = adapterClass.getDeclaredConstructor(Moshi::class.java, Array<Type>::class.java)
        args = arrayOf(this, typeArgs)
      } catch (e: NoSuchMethodException) {
        constructor = adapterClass.getDeclaredConstructor(Array<Type>::class.java)
        args = arrayOf(typeArgs)
      }
    } else {
      try {
        // Common case first
        constructor = adapterClass.getDeclaredConstructor(Moshi::class.java)
        args = arrayOf(this)
      } catch (e: NoSuchMethodException) {
        constructor = adapterClass.getDeclaredConstructor()
        args = emptyArray()
      }
    }
    constructor.isAccessible = true
    constructor.newInstance(*args).nullSafe()
  } catch (e: ClassNotFoundException) {
    throw RuntimeException("Failed to find the generated JsonAdapter class for $type", e)
  } catch (e: NoSuchMethodException) {
    if (possiblyFoundAdapter != null && type !is ParameterizedType && possiblyFoundAdapter.typeParameters.isNotEmpty()) {
      throw RuntimeException(
        "Failed to find the generated JsonAdapter constructor for '$type'. Suspiciously, the type was not parameterized but the target class '${possiblyFoundAdapter.canonicalName}' is generic. Consider using Types#newParameterizedType() to define these missing type variables.",
        e
      )
    } else {
      throw RuntimeException(
        "Failed to find the generated JsonAdapter constructor for $type", e
      )
    }
  } catch (e: IllegalAccessException) {
    throw RuntimeException("Failed to access the generated JsonAdapter for $type", e)
  } catch (e: InstantiationException) {
    throw RuntimeException("Failed to instantiate the generated JsonAdapter for $type", e)
  } catch (e: InvocationTargetException) {
    throw e.rethrowCause()
  }
}

public val Class<*>.isKotlin: Boolean
  get() = METADATA != null && isAnnotationPresent(METADATA)

/**
 * Reflectively looks up the defaults constructor of a kotlin class.
 *
 * @receiver the target kotlin class to instantiate.
 * @param T the type of `targetClass`.
 * @return the instantiated `targetClass` instance.
 */
public fun <T> Class<T>.lookupDefaultsConstructor(): Constructor<T> {
  checkNotNull(DEFAULT_CONSTRUCTOR_MARKER) {
    "DefaultConstructorMarker not on classpath. Make sure the Kotlin stdlib is on the classpath."
  }
  val defaultConstructor = findConstructor()
  defaultConstructor.isAccessible = true
  return defaultConstructor
}

private fun <T> Class<T>.findConstructor(): Constructor<T> {
  for (constructor in declaredConstructors) {
    val paramTypes = constructor.parameterTypes
    if (paramTypes.isNotEmpty() && paramTypes[paramTypes.size - 1] == DEFAULT_CONSTRUCTOR_MARKER) {
      @Suppress("UNCHECKED_CAST")
      return constructor as Constructor<T>
    }
  }
  throw IllegalStateException("No defaults constructor found for $this")
}

public fun missingProperty(
  propertyName: String?,
  jsonName: String?,
  reader: JsonReader
): JsonDataException {
  val path = reader.path
  val message = if (jsonName == propertyName) {
    "Required value '$propertyName' missing at $path"
  } else {
    "Required value '$propertyName' (JSON name '$jsonName') missing at $path"
  }
  return JsonDataException(message)
}

public fun unexpectedNull(
  propertyName: String,
  jsonName: String,
  reader: JsonReader
): JsonDataException {
  val path = reader.path
  val message: String = if (jsonName == propertyName) {
    "Non-null value '$propertyName' was null at $path"
  } else {
    "Non-null value '$propertyName' (JSON name '$jsonName') was null at $path"
  }
  return JsonDataException(message)
}

// A sneaky way to mark value as known to be not null, allowing smart casts and skipping the null-check intrinsic
// Safe to use here because it's already compiled and not being used externally
@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
public inline fun <T : Any> knownNotNull(value: T?) {
  contract {
    returns() implies (value != null)
  }
}

// Public due to inline access in MoshiKotlinTypesExtensions
public fun <T> Class<T>.boxIfPrimitive(): Class<T> {
  // cast is safe: long.class and Long.class are both of type Class<Long>
  @Suppress("UNCHECKED_CAST")
  val wrapped = PRIMITIVE_TO_WRAPPER_TYPE[this] as Class<T>?
  return wrapped ?: this
}

internal class ParameterizedTypeImpl private constructor(
  private val ownerType: Type?,
  private val rawType: Type,
  @JvmField
  val typeArguments: Array<Type>
) : ParameterizedType {
  override fun getActualTypeArguments() = typeArguments.clone()

  override fun getRawType() = rawType

  override fun getOwnerType() = ownerType

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  override fun equals(other: Any?) =
    other is ParameterizedType && Types.equals(this, other as ParameterizedType?)

  override fun hashCode(): Int {
    return typeArguments.contentHashCode() xor rawType.hashCode() xor ownerType.hashCodeOrZero
  }

  override fun toString(): String {
    val result = StringBuilder(30 * (typeArguments.size + 1))
    result.append(rawType.typeToString())
    if (typeArguments.isEmpty()) {
      return result.toString()
    }
    result.append("<").append(typeArguments[0].typeToString())
    for (i in 1 until typeArguments.size) {
      result.append(", ").append(typeArguments[i].typeToString())
    }
    return result.append(">").toString()
  }

  companion object {
    @JvmName("create")
    @JvmStatic
    operator fun invoke(
      ownerType: Type?,
      rawType: Type,
      vararg typeArguments: Type
    ): ParameterizedTypeImpl {
      // Require an owner type if the raw type needs it.
      if (rawType is Class<*>) {
        val enclosingClass = rawType.enclosingClass
        if (ownerType != null) {
          require(!(enclosingClass == null || ownerType.rawType != enclosingClass)) { "unexpected owner type for $rawType: $ownerType" }
        } else require(enclosingClass == null) { "unexpected owner type for $rawType: null" }
      }
      @Suppress("UNCHECKED_CAST")
      val finalTypeArgs = typeArguments.clone() as Array<Type>
      for (t in finalTypeArgs.indices) {
        finalTypeArgs[t].checkNotPrimitive()
        finalTypeArgs[t] = finalTypeArgs[t].canonicalize()
      }
      return ParameterizedTypeImpl(ownerType?.canonicalize(), rawType.canonicalize(), finalTypeArgs)
    }
  }
}

internal class GenericArrayTypeImpl private constructor(private val componentType: Type) : GenericArrayType {
  override fun getGenericComponentType() = componentType

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  override fun equals(other: Any?) =
    other is GenericArrayType && Types.equals(this, other as GenericArrayType?)

  override fun hashCode() = componentType.hashCode()

  override fun toString() = componentType.typeToString() + "[]"

  companion object {
    @JvmName("create")
    @JvmStatic
    operator fun invoke(componentType: Type): GenericArrayTypeImpl {
      return GenericArrayTypeImpl(componentType.canonicalize())
    }
  }
}

/**
 * The WildcardType interface supports multiple upper bounds and multiple lower bounds. We only
 * support what the Java 6 language needs - at most one bound. If a lower bound is set, the upper
 * bound must be Object.class.
 */
internal class WildcardTypeImpl private constructor(
  private val upperBound: Type,
  private val lowerBound: Type?
) : WildcardType {

  override fun getUpperBounds() = arrayOf(upperBound)

  override fun getLowerBounds() = lowerBound?.let { arrayOf(it) } ?: EMPTY_TYPE_ARRAY

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  override fun equals(other: Any?) = other is WildcardType && Types.equals(this, other as WildcardType?)

  override fun hashCode(): Int {
    // This equals Arrays.hashCode(getLowerBounds()) ^ Arrays.hashCode(getUpperBounds()).
    return (if (lowerBound != null) 31 + lowerBound.hashCode() else 1) xor 31 + upperBound.hashCode()
  }

  override fun toString(): String {
    return when {
      lowerBound != null -> "? super ${lowerBound.typeToString()}"
      upperBound === Any::class.java -> "?"
      else -> "? extends ${upperBound.typeToString()}"
    }
  }

  companion object {
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      upperBounds: Array<Type>,
      lowerBounds: Array<Type>
    ): WildcardTypeImpl {
      require(lowerBounds.size <= 1)
      require(upperBounds.size == 1)
      return if (lowerBounds.size == 1) {
        lowerBounds[0].checkNotPrimitive()
        require(!(upperBounds[0] !== Any::class.java))
        WildcardTypeImpl(
          lowerBound = lowerBounds[0].canonicalize(),
          upperBound = Any::class.java
        )
      } else {
        upperBounds[0].checkNotPrimitive()
        WildcardTypeImpl(
          lowerBound = null,
          upperBound = upperBounds[0].canonicalize()
        )
      }
    }
  }
}
