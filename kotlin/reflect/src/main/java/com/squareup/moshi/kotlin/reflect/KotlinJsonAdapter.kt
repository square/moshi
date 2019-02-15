/*
 * Copyright (C) 2017 Square, Inc.
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
package com.squareup.moshi.kotlin.reflect

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.internal.Util
import com.squareup.moshi.internal.Util.generatedAdapter
import com.squareup.moshi.internal.Util.resolve
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.AbstractMap.SimpleEntry
import kotlin.collections.Map.Entry
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaType

/** Classes annotated with this are eligible for this adapter. */
private val KOTLIN_METADATA = Class.forName("kotlin.Metadata") as Class<out Annotation>

/**
 * Placeholder value used when a field is absent from the JSON. Note that this code
 * distinguishes between absent values and present-but-null values.
 */
private val ABSENT_VALUE = Any()

/**
 * This class encodes Kotlin classes using their properties. It decodes them by first invoking the
 * constructor, and then by setting any additional properties that exist, if any.
 */
internal class KotlinJsonAdapter<T>(
    val constructor: KFunction<T>,
    val bindings: List<Binding<T, Any?>?>,
    val options: JsonReader.Options) : JsonAdapter<T>() {

  override fun fromJson(reader: JsonReader): T {
    val constructorSize = constructor.parameters.size

    // Read each value into its slot in the array.
    val values = Array<Any?>(bindings.size) { ABSENT_VALUE }
    reader.beginObject()
    while (reader.hasNext()) {
      val index = reader.selectName(options)
      val binding = if (index != -1) bindings[index] else null

      if (binding == null) {
        reader.skipName()
        reader.skipValue()
        continue
      }

      if (values[index] !== ABSENT_VALUE) {
        throw JsonDataException(
            "Multiple values for '${binding.property.name}' at ${reader.path}")
      }

      values[index] = binding.adapter.fromJson(reader)

      if (values[index] == null && !binding.property.returnType.isMarkedNullable) {
        throw JsonDataException(
            "Non-null value '${binding.property.name}' was null at ${reader.path}")
      }
    }
    reader.endObject()

    // Confirm all parameters are present, optional, or nullable.
    for (i in 0 until constructorSize) {
      if (values[i] === ABSENT_VALUE && !constructor.parameters[i].isOptional) {
        if (!constructor.parameters[i].type.isMarkedNullable) {
          throw JsonDataException(
              "Required value '${constructor.parameters[i].name}' missing at ${reader.path}")
        }
        values[i] = null // Replace absent with null.
      }
    }

    // Call the constructor using a Map so that absent optionals get defaults.
    val result = constructor.callBy(IndexedParameterMap(constructor.parameters, values))

    // Set remaining properties.
    for (i in constructorSize until bindings.size) {
      val binding = bindings[i]!!
      val value = values[i]
      binding.set(result, value)
    }

    return result
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    if (value == null) throw NullPointerException("value == null")

    writer.beginObject()
    for (binding in bindings) {
      if (binding == null) continue // Skip constructor parameters that aren't properties.

      writer.name(binding.name)
      binding.adapter.toJson(writer, binding.get(value))
    }
    writer.endObject()
  }

  override fun toString() = "KotlinJsonAdapter(${constructor.returnType})"

  data class Binding<K, P>(
      val name: String,
      val adapter: JsonAdapter<P>,
      val property: KProperty1<K, P>,
      val parameter: KParameter?) {
    fun get(value: K) = property.get(value)

    fun set(result: K, value: P) {
      if (value !== ABSENT_VALUE) {
        (property as KMutableProperty1<K, P>).set(result, value)
      }
    }
  }

  /** A simple [Map] that uses parameter indexes instead of sorting or hashing. */
  class IndexedParameterMap(val parameterKeys: List<KParameter>, val parameterValues: Array<Any?>)
    : AbstractMap<KParameter, Any?>() {

    override val entries: Set<Entry<KParameter, Any?>>
      get() {
        val allPossibleEntries = parameterKeys.mapIndexed { index, value ->
          SimpleEntry<KParameter, Any?>(value, parameterValues[index])
        }
        return allPossibleEntries.filterTo(LinkedHashSet<Entry<KParameter, Any?>>()) {
          it.value !== ABSENT_VALUE
        }
      }

    override fun containsKey(key: KParameter) = parameterValues[key.index] !== ABSENT_VALUE

    override fun get(key: KParameter): Any? {
      val value = parameterValues[key.index]
      return if (value !== ABSENT_VALUE) value else null
    }
  }
}

class KotlinJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi)
      : JsonAdapter<*>? {
    if (!annotations.isEmpty()) return null

    val rawType = Types.getRawType(type)
    if (rawType.isInterface) return null
    if (rawType.isEnum) return null
    if (!rawType.isAnnotationPresent(KOTLIN_METADATA)) return null
    if (Util.isPlatformType(rawType)) return null
    try {
      val generatedAdapter = generatedAdapter(moshi, type, rawType)
      if (generatedAdapter != null) {
        return generatedAdapter
      }
    } catch (e: RuntimeException) {
      if (e.cause !is ClassNotFoundException) {
        throw e
      }
      // Fall back to a reflective adapter when the generated adapter is not found.
    }

    if (rawType.isLocalClass) {
      throw IllegalArgumentException("Cannot serialize local class or object expression ${rawType.name}")
    }
    val rawTypeKotlin = rawType.kotlin
    if (rawTypeKotlin.isAbstract) {
      throw IllegalArgumentException("Cannot serialize abstract class ${rawType.name}")
    }
    if (rawTypeKotlin.isInner) {
      throw IllegalArgumentException("Cannot serialize inner class ${rawType.name}")
    }
    if (rawTypeKotlin.objectInstance != null) {
      throw IllegalArgumentException("Cannot serialize object declaration ${rawType.name}")
    }

    val constructor = rawTypeKotlin.primaryConstructor ?: return null
    val parametersByName = constructor.parameters.associateBy { it.name }
    constructor.isAccessible = true

    val bindingsByName = LinkedHashMap<String, KotlinJsonAdapter.Binding<Any, Any?>>()

    for (property in rawTypeKotlin.memberProperties) {
      val parameter = parametersByName[property.name]

      if (Modifier.isTransient(property.javaField?.modifiers ?: 0)) {
        if (parameter != null && !parameter.isOptional) {
          throw IllegalArgumentException(
              "No default value for transient constructor $parameter")
        }
        continue
      }

      if (parameter != null && parameter.type != property.returnType) {
        throw IllegalArgumentException("'${property.name}' has a constructor parameter of type " +
            "${parameter.type} but a property of type ${property.returnType}.")
      }

      if (property !is KMutableProperty1 && parameter == null) continue

      property.isAccessible = true
      var allAnnotations = property.annotations
      var jsonAnnotation = property.findAnnotation<Json>()

      if (parameter != null) {
        allAnnotations += parameter.annotations
        if (jsonAnnotation == null) {
          jsonAnnotation = parameter.findAnnotation<Json>()
        }
      }

      val name = jsonAnnotation?.name ?: property.name
      val resolvedPropertyType = resolve(type, rawType, property.returnType.javaType)
      val adapter = moshi.adapter<Any>(
          resolvedPropertyType, Util.jsonAnnotations(allAnnotations.toTypedArray()), property.name)

      bindingsByName[property.name] = KotlinJsonAdapter.Binding(name, adapter,
          property as KProperty1<Any, Any?>, parameter)
    }

    val bindings = ArrayList<KotlinJsonAdapter.Binding<Any, Any?>?>()

    for (parameter in constructor.parameters) {
      val binding = bindingsByName.remove(parameter.name)
      if (binding == null && !parameter.isOptional) {
        throw IllegalArgumentException("No property for required constructor ${parameter}")
      }
      bindings += binding
    }

    bindings += bindingsByName.values

    val options = JsonReader.Options.of(*bindings.map { it?.name ?: "\u0000" }.toTypedArray())
    return KotlinJsonAdapter(constructor, bindings, options).nullSafe()
  }
}
