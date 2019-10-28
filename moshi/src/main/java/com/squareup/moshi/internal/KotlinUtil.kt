/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.moshi.internal

import com.squareup.moshi.Types
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance

fun KType.toType(): Type {
  classifier?.let {
    when (it) {
      is KTypeParameter -> throw IllegalArgumentException("Type parameters are not supported")
      is KClass<*> -> {
        val javaType = it.java
        return when {
          javaType.isArray -> Types.arrayOf(javaType.componentType)
          javaType.isPrimitive -> it.javaObjectType
          arguments.isEmpty() -> javaType
          else -> {
            val typeArguments = arguments.toTypedArray { it.toType() }
            val enclosingClass = javaType.enclosingClass
            if (enclosingClass != null) {
              Types.newParameterizedTypeWithOwner(enclosingClass, javaType,
                  *typeArguments)
            } else {
              Types.newParameterizedType(javaType, *typeArguments)
            }
          }
        }
      }
      else -> throw IllegalArgumentException("Unsupported classifier: $this")
    }
  }

  // Can happen for intersection types
  throw IllegalArgumentException("Unrepresentable type: $this")
}

fun KTypeProjection.toType(): Type {
  val javaType = type?.toType() ?: return Any::class.java
  return when (variance) {
    null -> Any::class.java
    KVariance.INVARIANT -> javaType
    KVariance.IN -> Types.subtypeOf(javaType)
    KVariance.OUT -> Types.supertypeOf(javaType)
  }
}

private inline fun <T, reified R> List<T>.toTypedArray(mapper: (T) -> R): Array<R> {
  return Array(size) {
    mapper.invoke(get(it))
  }
}
