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
package com.squareup.moshi

import com.squareup.moshi.internal.jsonAnnotations
import com.squareup.moshi.internal.jsonName
import com.squareup.moshi.internal.knownNotNull
import com.squareup.moshi.internal.missingProperty
import com.squareup.moshi.internal.resolve
import com.squareup.moshi.internal.rethrowCause
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.RecordComponent
import java.lang.reflect.Type

/**
 * A [JsonAdapter] that supports Java `record` classes via reflection.
 *
 * **NOTE:** Java records require JDK 16 or higher.
 */
internal class RecordJsonAdapter<T>(
  private val constructor: MethodHandle,
  private val targetClass: String,
  componentBindings: Map<String, ComponentBinding<Any>>
) : JsonAdapter<T>() {

  data class ComponentBinding<T>(
    val componentName: String,
    val jsonName: String,
    val adapter: JsonAdapter<T>,
    val accessor: MethodHandle
  )

  private val componentBindingsArray = componentBindings.values.toTypedArray()
  private val options = JsonReader.Options.of(*componentBindings.keys.toTypedArray())

  override fun fromJson(reader: JsonReader): T? {
    val resultsArray = arrayOfNulls<Any>(componentBindingsArray.size)

    reader.beginObject()
    while (reader.hasNext()) {
      val index = reader.selectName(options)
      if (index == -1) {
        reader.skipName()
        reader.skipValue()
        continue
      }
      resultsArray[index] = componentBindingsArray[index].adapter.fromJson(reader)
    }
    reader.endObject()

    return try {
      @Suppress("UNCHECKED_CAST")
      constructor.invokeWithArguments(*resultsArray) as T
    } catch (e: InvocationTargetException) {
      throw e.rethrowCause()
    } catch (e: Throwable) {
      // Don't throw a fatal error if it's just an absent primitive.
      for (i in componentBindingsArray.indices) {
        if (resultsArray[i] == null && componentBindingsArray[i].accessor.type().returnType().isPrimitive) {
          throw missingProperty(
            propertyName = componentBindingsArray[i].componentName,
            jsonName = componentBindingsArray[i].jsonName,
            reader = reader
          )
        }
      }
      throw AssertionError(e)
    }
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    writer.beginObject()

    for (binding in componentBindingsArray) {
      writer.name(binding.jsonName)
      val componentValue = try {
        binding.accessor.invoke(value)
      } catch (e: InvocationTargetException) {
        throw e.rethrowCause()
      } catch (e: Throwable) {
        throw AssertionError(e)
      }
      binding.adapter.toJson(writer, componentValue)
    }

    writer.endObject()
  }

  override fun toString() = "JsonAdapter($targetClass)"

  companion object Factory : JsonAdapter.Factory {

    private val VOID_CLASS = knownNotNull(Void::class.javaPrimitiveType)

    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (annotations.isNotEmpty()) {
        return null
      }

      if (type !is Class<*> && type !is ParameterizedType) {
        return null
      }

      val rawType = type.rawType
      if (!rawType.isRecord) {
        return null
      }

      val components = rawType.recordComponents
      val bindings = LinkedHashMap<String, ComponentBinding<Any>>()
      val lookup = MethodHandles.lookup()
      val componentRawTypes = Array<Class<*>>(components.size) { i ->
        val component = components[i]
        val componentBinding =
          createComponentBinding(type, rawType, moshi, lookup, component)
        val replaced = bindings.put(componentBinding.jsonName, componentBinding)
        if (replaced != null) {
          throw IllegalArgumentException(
            "Conflicting components:\n    ${replaced.componentName}\n    ${componentBinding.componentName}"
          )
        }
        component.type
      }

      val constructor = try {
        lookup.findConstructor(rawType, methodType(VOID_CLASS, componentRawTypes))
      } catch (e: NoSuchMethodException) {
        throw AssertionError(e)
      } catch (e: IllegalAccessException) {
        throw AssertionError(e)
      }

      return RecordJsonAdapter<Any>(constructor, rawType.simpleName, bindings).nullSafe()
    }

    private fun createComponentBinding(
      type: Type,
      rawType: Class<*>,
      moshi: Moshi,
      lookup: MethodHandles.Lookup,
      component: RecordComponent
    ): ComponentBinding<Any> {
      val componentName = component.name
      val jsonName = component.jsonName(componentName)

      val componentType = component.genericType.resolve(type, rawType)
      val qualifiers = component.jsonAnnotations
      val adapter = moshi.adapter<Any>(componentType, qualifiers)

      val accessor = try {
        lookup.unreflect(component.accessor)
      } catch (e: IllegalAccessException) {
        throw AssertionError(e)
      }

      return ComponentBinding(componentName, jsonName, adapter, accessor)
    }
  }
}
