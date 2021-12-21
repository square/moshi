/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi

import com.squareup.moshi.internal.Util
import java.lang.reflect.*
import java.util.*
import javax.annotation.CheckReturnValue
import kotlin.collections.LinkedHashSet

/** Factory methods for types.  */
@CheckReturnValue
public object Types {
  /**
   * Resolves the generated [JsonAdapter] fully qualified class name for a given [ ] `clazz`. This is the same lookup logic used by both the
   * Moshi code generation as well as lookup for any JsonClass-annotated classes. This can be useful
   * if generating your own JsonAdapters without using Moshi's first party code gen.
   *
   * @param clazz the class to calculate a generated JsonAdapter name for.
   * @return the resolved fully qualified class name to the expected generated JsonAdapter class.
   * Note that this name will always be a top-level class name and not a nested class.
   */
  @JvmStatic
  public fun generatedJsonAdapterName(clazz: Class<*>): String {
    if (clazz.getAnnotation(JsonClass::class.java) == null) {
      throw IllegalArgumentException("Class does not have a JsonClass annotation: $clazz")
    }
    return generatedJsonAdapterName(clazz.name)
  }

  /**
   * Resolves the generated [JsonAdapter] fully qualified class name for a given [ ] `className`. This is the same lookup logic used by both
   * the Moshi code generation as well as lookup for any JsonClass-annotated classes. This can be
   * useful if generating your own JsonAdapters without using Moshi's first party code gen.
   *
   * @param className the fully qualified class to calculate a generated JsonAdapter name for.
   * @return the resolved fully qualified class name to the expected generated JsonAdapter class.
   * Note that this name will always be a top-level class name and not a nested class.
   */
  @JvmStatic
  public fun generatedJsonAdapterName(className: String): String {
    return className.replace("$", "_") + "JsonAdapter"
  }

  /**
   * Checks if `annotations` contains `jsonQualifier`. Returns the subset of `annotations` without `jsonQualifier`, or null if `annotations` does not contain
   * `jsonQualifier`.
   */
  @JvmStatic
  public fun nextAnnotations(
    annotations: Set<Annotation>, jsonQualifier: Class<out Annotation>
  ): Set<Annotation>? {
    if (!jsonQualifier.isAnnotationPresent(JsonQualifier::class.java)) {
      throw IllegalArgumentException("$jsonQualifier is not a JsonQualifier.")
    }
    if (annotations.isEmpty()) {
      return null
    }
    annotations.forEach { annotation ->
      if ((jsonQualifier == annotation.annotationClass.java)) {
        val delegateAnnotations = annotations.toMutableSet()
        delegateAnnotations.remove(annotation)
        return delegateAnnotations.toSet()
      }
    }
    return null
  }

  /**
   * Returns a new parameterized type, applying `typeArguments` to `rawType`. Use this
   * method if `rawType` is not enclosed in another type.
   */
  @JvmStatic
  public fun newParameterizedType(rawType: Type, vararg typeArguments: Type): ParameterizedType {
    if (typeArguments.isEmpty()) {
      throw IllegalArgumentException("Missing type arguments for $rawType")
    }
    return Util.ParameterizedTypeImpl(null, rawType, *typeArguments)
  }

  /**
   * Returns a new parameterized type, applying `typeArguments` to `rawType`. Use this
   * method if `rawType` is enclosed in `ownerType`.
   */
  @JvmStatic
  public fun newParameterizedTypeWithOwner(
    ownerType: Type, rawType: Type, vararg typeArguments: Type
  ): ParameterizedType {
    if (typeArguments.isEmpty()) {
      throw IllegalArgumentException("Missing type arguments for $rawType")
    }
    return Util.ParameterizedTypeImpl(ownerType, rawType, *typeArguments)
  }

  /** Returns an array type whose elements are all instances of `componentType`.  */
  @JvmStatic
  public fun arrayOf(componentType: Type): GenericArrayType {
    return Util.GenericArrayTypeImpl(componentType)
  }

  /**
   * Returns a type that represents an unknown type that extends `bound`. For example, if
   * `bound` is `CharSequence.class`, this returns `? extends CharSequence`. If
   * `bound` is `Object.class`, this returns `?`, which is shorthand for `?
   * extends Object`.
   */
  @JvmStatic
  public fun subtypeOf(bound: Type): WildcardType {
    val upperBounds = if (bound is WildcardType) {
      bound.upperBounds
    } else {
      arrayOf<Type>(bound)
    }
    return Util.WildcardTypeImpl(upperBounds, Util.EMPTY_TYPE_ARRAY)
  }

  /**
   * Returns a type that represents an unknown supertype of `bound`. For example, if `bound` is `String.class`, this returns `? super String`.
   */
  @JvmStatic
  public fun supertypeOf(bound: Type): WildcardType {
    val lowerBounds: Array<Type> = if (bound is WildcardType) {
      bound.lowerBounds
    } else {
      arrayOf<Type>(bound)
    }
    return Util.WildcardTypeImpl(arrayOf<Type>(Any::class.java), lowerBounds)
  }

  @JvmStatic
  public fun getRawType(type: Type?): Class<*> {
    when (type) {
      is Class<*> -> {
        // type is a normal class.
        return type
      }
      is ParameterizedType -> {
        // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
        // suspects some pathological case related to nested classes exists.
        val rawType = type.rawType
        return rawType as Class<*>
      }
      is GenericArrayType -> {
        val componentType = type.genericComponentType
        return java.lang.reflect.Array.newInstance(getRawType(componentType), 0).javaClass
      }
      is TypeVariable<*> -> {
        // We could use the variable's bounds, but that won't work if there are multiple. having a raw
        // type that's more general than necessary is okay.
        return Any::class.java
      }
      is WildcardType -> {
        return getRawType(type.upperBounds[0])
      }
      else -> {
        val className = if (type == null) "null" else type.javaClass.name
        throw IllegalArgumentException(
          ("Expected a Class, ParameterizedType, or "
            + "GenericArrayType, but <"
            + type
            + "> is of type "
            + className)
        )
      }
    }
  }

  /**
   * Returns the element type of this collection type.
   *
   * @throws IllegalArgumentException if this type is not a collection.
   */
  @JvmStatic
  public fun collectionElementType(context: Type, contextRawType: Class<*>): Type {
    var collectionType = getSupertype(context, contextRawType, MutableCollection::class.java)

    if (collectionType is WildcardType) {
      collectionType = collectionType.upperBounds[0]
    }
    if (collectionType is ParameterizedType) {
      return collectionType.actualTypeArguments[0]
    }
    return Any::class.java
  }

  /** Returns true if `a` and `b` are equal.  */
  @JvmStatic
  public fun equals(a: Type?, b: Type?): Boolean {
    if (a === b) {
      return true // Also handles (a == null && b == null).
    }
    else if (a is Class<*>) {
      if (b is GenericArrayType) {
        return equals(
          a.componentType, b.genericComponentType
        )
      }
      return (a == b) // Class already specifies equals().
    }
    else if (a is ParameterizedType) {
      if (b !is ParameterizedType) return false
      val aTypeArguments = if (a is Util.ParameterizedTypeImpl) a.typeArguments else a.actualTypeArguments
      val bTypeArguments = if (b is Util.ParameterizedTypeImpl) b.typeArguments else b.actualTypeArguments
      return (equals(a.ownerType, b.ownerType)
        && (a.rawType == b.rawType)
        && Arrays.equals(aTypeArguments, bTypeArguments))
    }
    else if (a is GenericArrayType) {
      if (b is Class<*>) {
        return equals(
          b.componentType, a.genericComponentType
        )
      }
      if (b !is GenericArrayType) return false
      return equals(a.genericComponentType, b.genericComponentType)
    }
    else if (a is WildcardType) {
      if (b !is WildcardType) return false
      return (Arrays.equals(a.upperBounds, b.upperBounds)
        && Arrays.equals(a.lowerBounds, b.lowerBounds))
    }
    else if (a is TypeVariable<*>) {
      if (b !is TypeVariable<*>) return false
      return (a.genericDeclaration == b.genericDeclaration
        && (a.name == b.name))
    }
    else {
      // This isn't a supported type.
      return false
    }
  }

  /**
   * @param clazz the target class to read the `fieldName` field annotations from.
   * @param fieldName the target field name on `clazz`.
   * @return a set of [JsonQualifier]-annotated [Annotation] instances retrieved from
   * the targeted field. Can be empty if none are found.
   */
  @JvmStatic
  @Deprecated("this is no longer needed in Kotlin 1.6.0 (which has direct annotation\n" + "        instantiation) and is obsolete.")
  public fun getFieldJsonQualifierAnnotations(
    clazz: Class<*>, fieldName: String
  ): Set<Annotation> {
    try {
      val field = clazz.getDeclaredField(fieldName)
      field.isAccessible = true
      val fieldAnnotations = field.declaredAnnotations
      val annotations: MutableSet<Annotation> = LinkedHashSet(fieldAnnotations.size)
      fieldAnnotations.forEach { annotation ->
        if (annotation.annotationClass.java.isAnnotationPresent(JsonQualifier::class.java)) {
          annotations.add(annotation)
        }
      }
      return annotations.toSet()
    } catch (e: NoSuchFieldException) {
      throw IllegalArgumentException(
        "Could not access field " + fieldName + " on class " + clazz.canonicalName, e
      )
    }
  }

  @Suppress("UNCHECKED_CAST")
  @JvmStatic
  public fun <T : Annotation?> createJsonQualifierImplementation(annotationType: Class<T>): T {
    if (!annotationType.isAnnotation) {
      throw IllegalArgumentException("$annotationType must be an annotation.")
    }
    if (!annotationType.isAnnotationPresent(JsonQualifier::class.java)) {
      throw IllegalArgumentException("$annotationType must have @JsonQualifier.")
    }
    if (annotationType.declaredMethods.isNotEmpty()) {
      throw IllegalArgumentException("$annotationType must not declare methods.")
    }
    return Proxy.newProxyInstance(
      annotationType.classLoader,
      arrayOf<Class<*>>(annotationType),
      InvocationHandler { proxy, method, args ->
        when (method.name) {
          "annotationType" -> annotationType
          "equals" -> {
            val o = args[0]
            annotationType.isInstance(o)
          }
          "hashCode" -> 0
          "toString" -> "@${annotationType.name}()"
          else -> method.invoke(proxy, *args)
        }
      }) as T
  }

  /**
   * Returns a two element array containing this map's key and value types in positions 0 and 1
   * respectively.
   */
  @JvmStatic
  public fun mapKeyAndValueTypes(context: Type, contextRawType: Class<*>): Array<Type> {
    // Work around a problem with the declaration of java.util.Properties. That class should extend
    // Hashtable<String, String>, but it's declared to extend Hashtable<Object, Object>.
    if (context == Properties::class.java) return arrayOf(String::class.java, String::class.java)

    val mapType = getSupertype(context, contextRawType, MutableMap::class.java)
    if (mapType is ParameterizedType) {
      return mapType.actualTypeArguments
    }
    return arrayOf(Any::class.java, Any::class.java)
  }

  /**
   * Returns the generic form of `supertype`. For example, if this is `ArrayList<String>`, this returns `Iterable<String>` given the input `Iterable.class`.
   *
   * @param supertype a superclass of, or interface implemented by, this.
   */
  @JvmStatic
  public fun getSupertype(context: Type, contextRawType: Class<*>, supertype: Class<*>): Type {
    if (!supertype.isAssignableFrom(contextRawType)) throw IllegalArgumentException()
    return Util.resolve(
      context, contextRawType, Util.getGenericSupertype(context, contextRawType, supertype)
    )
  }

  @JvmStatic
  public fun getGenericSuperclass(type: Type): Type {
    val rawType = getRawType(type)
    return Util.resolve(type, rawType, rawType.genericSuperclass)
  }

  /**
   * Returns the element type of `type` if it is an array type, or null if it is not an array
   * type.
   */
  @JvmStatic
  public fun arrayComponentType(type: Type): Type? {
    return when (type) {
      is GenericArrayType -> {
        type.genericComponentType
      }
      is Class<*> -> {
        type.componentType
      }
      else -> {
        null
      }
    }
  }
}
