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
package com.squareup.moshi

import java.lang.reflect.Modifier
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
private object ABSENT_VALUE

/**
 * This class encodes Kotlin classes using their properties. It decodes them by first invoking the
 * constructor, and then by setting any additional properties that exist, if any.
 */
internal class KotlinJsonAdapter<T> private constructor(
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
        reader.nextName()
        reader.skipValue()
        continue
      }

      if (values[index] !== ABSENT_VALUE) {
        throw JsonDataException(
            "Multiple values for ${constructor.parameters[index].name} at ${reader.path}")
      }

      values[index] = binding.adapter.fromJson(reader)
    }
    reader.endObject()

    // Confirm all parameters are present, optional, or nullable.
    for (i in 0 until constructorSize) {
      if (values[i] === ABSENT_VALUE && !constructor.parameters[i].isOptional) {
        if (!constructor.parameters[i].type.isMarkedNullable) {
          throw JsonDataException(
              "Required value ${constructor.parameters[i].name} missing at ${reader.path}")
        }
        values[i] = null // Replace absent with null.
      }
    }

    // Call the constructor using a Map so that absent optionals get defaults.
    val result = constructor.callBy(IndexedParameterMap(constructor.parameters, values))

    // Set remaining properties.
    for (i in constructorSize until bindings.size) {
      bindings[i]!!.set(result, values[i])
    }

    return result
  }

  override fun toJson(writer: JsonWriter, value: T) {
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
    init {
      if (property !is KMutableProperty1 && parameter == null) {
        throw IllegalArgumentException("No constructor or var property for ${property}")
      }
    }

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

  companion object {
    @JvmField val FACTORY = Factory { type, annotations, moshi ->
      if (!annotations.isEmpty()) return@Factory null

      val rawType = Types.getRawType(type)
      val platformType = ClassJsonAdapter.isPlatformType(rawType)
      if (platformType) return@Factory null

      if (!rawType.isAnnotationPresent(KOTLIN_METADATA)) return@Factory null

      val constructor = rawType.kotlin.primaryConstructor ?: return@Factory null
      val parametersByName = constructor.parameters.associateBy { it.name }
      constructor.isAccessible = true

      val bindingsByName = LinkedHashMap<String, Binding<Any, Any?>>()

      for (property in rawType.kotlin.memberProperties) {
        if (Modifier.isTransient(property.javaField?.modifiers ?: 0)) continue

        property.isAccessible = true
        var allAnnotations = property.annotations
        var jsonAnnotation = property.findAnnotation<Json>()

        val parameter = parametersByName[property.name]
        if (parameter != null) {
          allAnnotations += parameter.annotations
          if (jsonAnnotation == null) {
            jsonAnnotation = parameter.findAnnotation<Json>()
          }
        }

        val name = jsonAnnotation?.name ?: property.name
        val adapter = moshi.adapter<Any>(
            property.returnType.javaType, Util.jsonAnnotations(allAnnotations.toTypedArray()))

        bindingsByName[property.name] =
            Binding(name, adapter, property as KProperty1<Any, Any?>, parameter)
      }

      val bindings = ArrayList<Binding<Any, Any?>?>()

      for (parameter in constructor.parameters) {
        val binding = bindingsByName.remove(parameter.name)
        if (binding == null && !parameter.isOptional) {
          throw IllegalArgumentException("No property for required constructor ${parameter}")
        }
        bindings += binding
      }

      bindings += bindingsByName.values

      val options = JsonReader.Options.of(*bindings.map { it?.name ?: "\u0000" }.toTypedArray())
      KotlinJsonAdapter(constructor, bindings, options)
    }
  }
}
