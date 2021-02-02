/*
 * Copyright (C) 2020 Square, Inc.
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
package com.squareup.moshi

import com.squareup.moshi.internal.Util
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/** Returns the raw [Class] type of this type. */
public val Type.rawType: Class<*> get() = Types.getRawType(this)

/**
 * Checks if [this] contains [T]. Returns the subset of [this] without [T], or null if
 * [this] does not contain [T].
 */
public inline fun <reified T : Annotation> Set<Annotation>.nextAnnotations(): Set<Annotation>? = Types.nextAnnotations(this, T::class.java)

/**
 * Returns a type that represents an unknown type that extends [T]. For example, if
 * [T] is [CharSequence], this returns `out CharSequence`. If
 * [T] is [Any], this returns `*`, which is shorthand for `out Any?`.
 */
@ExperimentalStdlibApi
public inline fun <reified T> subtypeOf(): WildcardType {
  var type = typeOf<T>().javaType
  if (type is Class<*>) {
    type = Util.boxIfPrimitive(type)
  }
  return Types.subtypeOf(type)
}

/**
 * Returns a type that represents an unknown supertype of [T] bound. For example, if [T] is
 * [String], this returns `in String`.
 */
@ExperimentalStdlibApi
public inline fun <reified T> supertypeOf(): WildcardType {
  var type = typeOf<T>().javaType
  if (type is Class<*>) {
    type = Util.boxIfPrimitive(type)
  }
  return Types.supertypeOf(type)
}

/** Returns a [GenericArrayType] with [this] as its [GenericArrayType.getGenericComponentType]. */
@ExperimentalStdlibApi
public fun KType.asArrayType(): GenericArrayType = javaType.asArrayType()

/** Returns a [GenericArrayType] with [this] as its [GenericArrayType.getGenericComponentType]. */
public fun KClass<*>.asArrayType(): GenericArrayType = java.asArrayType()

/** Returns a [GenericArrayType] with [this] as its [GenericArrayType.getGenericComponentType]. */
public fun Type.asArrayType(): GenericArrayType = Types.arrayOf(this)
