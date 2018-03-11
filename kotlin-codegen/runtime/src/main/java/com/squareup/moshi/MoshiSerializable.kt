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
package com.squareup.moshi

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

@Retention(RUNTIME)
@Target(CLASS)
annotation class MoshiSerializable

class MoshiSerializableFactory : JsonAdapter.Factory {

  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {

    val rawType = Types.getRawType(type)
    if (!rawType.isAnnotationPresent(MoshiSerializable::class.java)) {
      return null
    }

    val clsName = rawType.name.replace("$", "_")
    val constructor = try {
      @Suppress("UNCHECKED_CAST")
      val bindingClass = rawType.classLoader
          .loadClass(clsName + "JsonAdapter") as Class<out JsonAdapter<*>>
      if (type is ParameterizedType) {
        // This is generic, use the two param moshi + type constructor
        bindingClass.getDeclaredConstructor(Moshi::class.java, Array<Type>::class.java)
      } else {
        // The standard single param moshi constructor
        bindingClass.getDeclaredConstructor(Moshi::class.java)
      }
    } catch (e: ClassNotFoundException) {
      throw RuntimeException("Unable to find generated Moshi adapter class for $clsName", e)
    } catch (e: NoSuchMethodException) {
      throw RuntimeException("Unable to find generated Moshi adapter constructor for $clsName", e)
    }

    try {
      return when {
        constructor.parameterTypes.size == 1 -> constructor.newInstance(moshi)
        type is ParameterizedType -> constructor.newInstance(moshi, type.actualTypeArguments)
        else -> throw IllegalStateException("Unable to handle type $type")
      }
    } catch (e: IllegalAccessException) {
      throw RuntimeException("Unable to invoke $constructor", e)
    } catch (e: InstantiationException) {
      throw RuntimeException("Unable to invoke $constructor", e)
    } catch (e: InvocationTargetException) {
      val cause = e.cause
      if (cause is RuntimeException) {
        throw cause
      }
      if (cause is Error) {
        throw cause
      }
      throw RuntimeException(
          "Could not create generated JsonAdapter instance for type $rawType", cause)
    }
  }
}
