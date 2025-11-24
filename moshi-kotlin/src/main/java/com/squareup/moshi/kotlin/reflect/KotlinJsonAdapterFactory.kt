/*
 * Copyright (C) 2017 Square, Inc.
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
package com.squareup.moshi.kotlin.reflect

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.internal.generatedAdapter
import com.squareup.moshi.internal.isPlatformType
import com.squareup.moshi.internal.jsonAnnotations
import com.squareup.moshi.internal.missingProperty
import com.squareup.moshi.internal.resolve
import com.squareup.moshi.internal.unexpectedNull
import com.squareup.moshi.rawType
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.KmFlexibleTypeUpperBound
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeProjection
import kotlin.metadata.Modality
import kotlin.metadata.Visibility
import kotlin.metadata.isInner
import kotlin.metadata.isNullable
import kotlin.metadata.isVar
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.kind
import kotlin.metadata.modality
import kotlin.metadata.visibility

/** Classes annotated with this are eligible for this adapter. */
private val KOTLIN_METADATA = Metadata::class.java

/**
 * Placeholder value used when a field is absent from the JSON. Note that this code
 * distinguishes between absent values and present-but-null values.
 */
internal val ABSENT_VALUE = Any()

/**
 * This class encodes Kotlin classes using their properties. It decodes them by first invoking the
 * constructor, and then by setting any additional properties that exist, if any.
 */
internal class KotlinJsonAdapter<T>(
  private val constructor: KtConstructor,
  private val allBindings: List<Binding<T, Any?>?>,
  private val nonIgnoredBindings: List<Binding<T, Any?>>,
  private val options: JsonReader.Options,
) : JsonAdapter<T>() {

  override fun fromJson(reader: JsonReader): T {
    val constructorSize = constructor.parameters.size

    // Read each value into its slot in the array.
    val values = Array<Any?>(allBindings.size) { ABSENT_VALUE }
    reader.beginObject()
    while (reader.hasNext()) {
      val index = reader.selectName(options)
      if (index == -1) {
        reader.skipName()
        reader.skipValue()
        continue
      }
      val binding = nonIgnoredBindings[index]

      val propertyIndex = binding.propertyIndex
      if (values[propertyIndex] !== ABSENT_VALUE) {
        throw JsonDataException("Multiple values for '${binding.property.name}' at ${reader.path}")
      }

      values[propertyIndex] = binding.adapter.fromJson(reader)

      if (values[propertyIndex] == null && !binding.property.km.returnType.isNullable) {
        throw unexpectedNull(binding.property.name, binding.jsonName, reader)
      }
    }
    reader.endObject()

    // Confirm all parameters are present, optional, or nullable.
    for (i in 0 until constructorSize) {
      if (values[i] === ABSENT_VALUE) {
        val param = constructor.parameters[i]
        if (!param.declaresDefaultValue) {
          if (!param.isNullable) {
            throw missingProperty(
              constructor.parameters[i].name,
              allBindings[i]?.jsonName,
              reader,
            )
          }
          values[i] = null // Replace absent with null.
        }
      }
    }

    // Call the constructor using a Map so that absent optionals get defaults.
    val result = constructor.callBy<T>(IndexedParameterMap(constructor.parameters, values))

    // Set remaining properties.
    for (i in constructorSize until allBindings.size) {
      val binding = allBindings[i]!!
      val value = values[i]
      binding.set(result, value)
    }

    return result
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    if (value == null) throw NullPointerException("value == null")

    writer.beginObject()
    for (binding in allBindings) {
      if (binding == null) continue // Skip constructor parameters that aren't properties.

      writer.name(binding.name)
      binding.adapter.toJson(writer, binding.get(value))
    }
    writer.endObject()
  }

  override fun toString() = "KotlinJsonAdapter(${constructor.type.canonicalName})"

  data class Binding<K, P>(
    val name: String,
    val jsonName: String,
    val adapter: JsonAdapter<P>,
    val property: KtProperty,
    val propertyIndex: Int = property.parameter?.index ?: -1,
  ) {

    fun get(value: K): Any? {
      val rawValue = if (property.jvmGetter != null) {
        property.jvmGetter.invoke(value)
      } else if (property.jvmField != null) {
        property.jvmField.get(value)
      } else {
        error("Could not get JVM field or invoke JVM getter for property '$name'")
      }

      // If this property is a value class, box the raw value
      return if (property.isValueClass && rawValue != null) {
        property.valueClassBoxer!!.invoke(null, rawValue)
      } else {
        rawValue
      }
    }

    fun set(result: K, value: P) {
      if (value !== ABSENT_VALUE) {
        // If this property is a value class, unbox the value before setting
        val actualValue =
          if (property.isValueClass && value != null) {
            property.valueClassUnboxer!!.invoke(value)
          } else {
            value
          }

        val setter = property.jvmSetter
        if (setter != null) {
          setter.invoke(result, actualValue)
          return
        }
        property.jvmField?.set(result, actualValue)
      }
    }
  }
}

/**
 * A JsonAdapter for inline types that reads/writes the single property value directly
 * without wrapping it in a JSON object.
 */
private class InlineKotlinJsonAdapter<T>(
  private val constructor: KtConstructor,
  private val binding: KotlinJsonAdapter.Binding<T, Any?>,
) : JsonAdapter<T>() {

  override fun fromJson(reader: JsonReader): T {
    // Read the value directly
    val value = binding.adapter.fromJson(reader)
    if (value == null && !binding.property.km.returnType.isNullable) {
      throw unexpectedNull(binding.property.name, binding.jsonName, reader)
    }

    @Suppress("UNCHECKED_CAST")
    return constructor.callBy(IndexedParameterMap(constructor.parameters, arrayOf(value)))
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    if (value == null) throw NullPointerException("value == null")
    binding.adapter.toJson(writer, binding.get(value))
  }

  override fun toString() = "InlineKotlinJsonAdapter(${constructor.type.canonicalName})"
}

public class KotlinJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (annotations.isNotEmpty()) return null

    val rawType = type.rawType
    if (rawType.isInterface) return null
    if (rawType.isEnum) return null
    if (!rawType.isAnnotationPresent(KOTLIN_METADATA)) return null
    if (rawType.isPlatformType) return null
    try {
      val generatedAdapter = moshi.generatedAdapter(type, rawType)
      if (generatedAdapter != null) {
        return generatedAdapter
      }
    } catch (e: RuntimeException) {
      if (e.cause !is ClassNotFoundException) {
        throw e
      }
      // Fall back to a reflective adapter when the generated adapter is not found.
    }

    require(!rawType.isLocalClass) {
      "Cannot serialize local class or object expression ${rawType.name}"
    }

    require(!rawType.isAnonymousClass) { "Cannot serialize anonymous class ${rawType.name}" }

    val kmClass = rawType.header()?.toKmClass() ?: return null

    require(kmClass.modality != Modality.ABSTRACT) {
      "Cannot serialize abstract class ${rawType.name}"
    }
    require(!kmClass.isInner) { "Cannot serialize inner class ${rawType.name}" }
    require(kmClass.kind != ClassKind.OBJECT) {
      "Cannot serialize object declaration ${rawType.name}"
    }
    require(kmClass.kind != ClassKind.COMPANION_OBJECT) {
      "Cannot serialize companion object declaration ${rawType.name}"
    }
    require(kmClass.modality != Modality.SEALED) {
      "Cannot reflectively serialize sealed class ${rawType.name}. Please register an adapter."
    }

    val ktConstructor = KtConstructor.primary(rawType, kmClass) ?: return null

    val allPropertiesSequence =
      kmClass.properties.asSequence() +
        generateSequence(rawType) { it.superclass }
          .mapNotNull { it.header()?.toKmClass() }
          .flatMap { it.properties.asSequence() }
          .filterNot {
            it.visibility == Visibility.PRIVATE || it.visibility == Visibility.PRIVATE_TO_THIS
          }
          .filter { it.isVar }

    val signatureSearcher = JvmSignatureSearcher(rawType)
    val bindingsByName = LinkedHashMap<String, KotlinJsonAdapter.Binding<Any, Any?>>()
    val parametersByName = ktConstructor.parameters.associateBy { it.name }

    for (property in allPropertiesSequence.distinctBy { it.name }) {
      val propertyField = signatureSearcher.field(property)
      val ktParameter = parametersByName[property.name]

      if (ktParameter != null) {
        require(ktParameter.km.type valueEquals property.returnType) {
          "'${property.name}' has a constructor parameter of type ${ktParameter.km.type.canonicalName} but a property of type ${property.returnType.canonicalName}."
        }
      }

      if (!property.isVar && ktParameter == null) continue

      val getterMethod = signatureSearcher.getter(property)
      val setterMethod = signatureSearcher.setter(property)
      val annotationsMethod = signatureSearcher.syntheticMethodForAnnotations(property)

      // Check if the property's return type is a value class
      val (propertyValueClassBoxer, propertyValueClassUnboxer) =
        KmExecutable.findValueClassMethods(property.returnType.classifier, rawType.classLoader)

      val ktProperty =
        KtProperty(
          km = property,
          jvmField = propertyField,
          jvmGetter = getterMethod,
          jvmSetter = setterMethod,
          jvmAnnotationsMethod = annotationsMethod,
          parameter = ktParameter,
          valueClassBoxer = propertyValueClassBoxer,
          valueClassUnboxer = propertyValueClassUnboxer,
        )
      val allAnnotations = ktProperty.annotations.toMutableList()
      val jsonAnnotation = allAnnotations.filterIsInstance<Json>().firstOrNull()

      val isIgnored =
        Modifier.isTransient(propertyField?.modifiers ?: 0) || jsonAnnotation?.ignore == true

      if (isIgnored) {
        ktParameter?.run {
          require(declaresDefaultValue) {
            "No default value for transient/ignored constructor parameter '$name' on type '${rawType.canonicalName}'"
          }
        }
        continue
      }

      val name = jsonAnnotation?.name ?: property.name
      val resolvedPropertyType = ktProperty.javaType.resolve(type, rawType)
      val adapter =
        moshi.adapter<Any?>(
          resolvedPropertyType,
          allAnnotations.toTypedArray().jsonAnnotations,
          property.name,
        )

      bindingsByName[property.name] =
        KotlinJsonAdapter.Binding(name, jsonAnnotation?.name ?: name, adapter, ktProperty)
    }

    val bindings = ArrayList<KotlinJsonAdapter.Binding<Any, Any?>?>()

    for (parameter in ktConstructor.parameters) {
      val binding = bindingsByName.remove(parameter.name)
      require(binding != null || parameter.declaresDefaultValue) {
        "No property for required constructor parameter '${parameter.name}' on type '${rawType.canonicalName}'"
      }
      bindings += binding
    }

    var index = bindings.size
    for (bindingByName in bindingsByName) {
      bindings += bindingByName.value.copy(propertyIndex = index++)
    }

    val nonIgnoredBindings = bindings.filterNotNull()

    // Check if this is an inline type
    val jsonClassAnnotation = rawType.getAnnotation(JsonClass::class.java)
    if (jsonClassAnnotation?.inline == true) {
      require(nonIgnoredBindings.size == 1) {
        "@JsonClass with inline = true requires exactly one non-transient property, " +
          "but ${rawType.canonicalName} has ${nonIgnoredBindings.size}: " +
          "${nonIgnoredBindings.joinToString { it.name }}."
      }
      @Suppress("UNCHECKED_CAST")
      return InlineKotlinJsonAdapter(
        ktConstructor,
        nonIgnoredBindings[0],
      ).nullSafe()
    }

    val options = JsonReader.Options.of(*nonIgnoredBindings.map { it.name }.toTypedArray())
    return KotlinJsonAdapter(ktConstructor, bindings, nonIgnoredBindings, options).nullSafe()
  }

  private infix fun KmType?.valueEquals(other: KmType?): Boolean {
    return when {
      this === other -> true

      this != null && other != null -> {
        // Note we don't check abbreviatedType because typealiases and their backing types are equal
        // for our purposes.
        arguments valueEquals other.arguments &&
          classifier == other.classifier &&
          isNullable == other.isNullable &&
          flexibleTypeUpperBound valueEquals other.flexibleTypeUpperBound &&
          outerType valueEquals other.outerType
      }

      else -> false
    }
  }

  private infix fun List<KmTypeProjection>.valueEquals(other: List<KmTypeProjection>): Boolean {
    // check collections aren't same
    if (this !== other) {
      // fast check of sizes
      if (this.size != other.size) return false

      // check this and other contains same elements at position
      for (i in indices) {
        if (!(get(i) valueEquals other[i])) {
          return false
        }
      }
    }
    // collections are same or they contain same elements with same order
    return true
  }

  private infix fun KmTypeProjection?.valueEquals(other: KmTypeProjection?): Boolean {
    return when {
      this === other -> true

      this != null && other != null -> {
        variance == other.variance && type valueEquals other.type
      }

      else -> false
    }
  }

  private infix fun KmFlexibleTypeUpperBound?.valueEquals(
    other: KmFlexibleTypeUpperBound?,
  ): Boolean {
    return when {
      this === other -> true

      this != null && other != null -> {
        typeFlexibilityId == other.typeFlexibilityId && type valueEquals other.type
      }

      else -> false
    }
  }
  private fun Class<*>.header(): Metadata? {
    val metadata = getAnnotation(KOTLIN_METADATA) ?: return null
    return with(metadata) {
      Metadata(
        kind = kind,
        metadataVersion = metadataVersion,
        data1 = data1,
        data2 = data2,
        extraString = extraString,
        packageName = packageName,
        extraInt = extraInt,
      )
    }
  }

  private fun Metadata.toKmClass(): KmClass? {
    val classMetadata = KotlinClassMetadata.readLenient(this)
    if (classMetadata !is KotlinClassMetadata.Class) {
      return null
    }

    return classMetadata.kmClass
  }
}
