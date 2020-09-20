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
import kotlinx.metadata.Flag
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmFlexibleTypeUpperBound
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/** Classes annotated with this are eligible for this adapter. */
private val KOTLIN_METADATA = Metadata::class.java

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
  private val constructor: KtConstructor,
  private val allBindings: List<Binding<T, Any?>?>,
  private val nonTransientBindings: List<Binding<T, Any?>>,
  private val options: JsonReader.Options
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
      val binding = nonTransientBindings[index]

      val propertyIndex = binding.propertyIndex
      if (values[propertyIndex] !== ABSENT_VALUE) {
        throw JsonDataException(
          "Multiple values for '${binding.property.name}' at ${reader.path}"
        )
      }

      values[propertyIndex] = binding.adapter.fromJson(reader)

      if (values[propertyIndex] == null && !binding.property.km.returnType.isNullable) {
        throw Util.unexpectedNull(
          binding.property.name,
          binding.jsonName,
          reader
        )
      }
    }
    reader.endObject()

    // Confirm all parameters are present, optional, or nullable.
    for (i in 0 until constructorSize) {
      val param = constructor.parameters[i]
      if (values[i] === ABSENT_VALUE && !param.declaresDefaultValue) {
        if (!param.isNullable) {
          throw Util.missingProperty(
            constructor.parameters[i].name,
            allBindings[i]?.jsonName,
            reader
          )
        }
        values[i] = null // Replace absent with null.
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
    val jsonName: String?,
    val adapter: JsonAdapter<P>,
    val property: KtProperty,
    val propertyIndex: Int = property.parameter?.index ?: -1
  ) {

    fun get(value: K): Any? {
      property.jvmGetter?.let { getter ->
        return getter.invoke(value)
      }
      property.jvmField?.let {
        return it.get(value)
      }

      error("Could not get JVM field or invoke JVM getter for property '$name'")
    }

    fun set(result: K, value: P) {
      if (value !== ABSENT_VALUE) {
        property.jvmSetter?.let { setter ->
          setter.invoke(result, value)
          return
        }
        property.jvmField?.set(result, value)
      }
    }
  }

  /** A simple [Map] that uses parameter indexes instead of sorting or hashing. */
  class IndexedParameterMap(
    private val parameterKeys: List<KtParameter>,
    private val parameterValues: Array<Any?>
  ) : AbstractMutableMap<KtParameter, Any?>() {

    override fun put(key: KtParameter, value: Any?): Any? = null

    override val entries: MutableSet<MutableMap.MutableEntry<KtParameter, Any?>>
      get() {
        val allPossibleEntries = parameterKeys.mapIndexed { index, value ->
          SimpleEntry<KtParameter, Any?>(value, parameterValues[index])
        }
        return allPossibleEntries.filterTo(mutableSetOf()) {
          it.value !== ABSENT_VALUE
        }
      }

    override fun containsKey(key: KtParameter) = parameterValues[key.index] !== ABSENT_VALUE

    override fun get(key: KtParameter): Any? {
      val value = parameterValues[key.index]
      return if (value !== ABSENT_VALUE) value else null
    }
  }
}

class KotlinJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi):
    JsonAdapter<*>? {
      if (annotations.isNotEmpty()) return null

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

      require(!rawType.isLocalClass) {
        "Cannot serialize local class or object expression ${rawType.name}"
      }

      val kmClass = rawType.header()?.toKmClass() ?: return null

      require(!Flag.IS_ABSTRACT(kmClass.flags)) {
        "Cannot serialize abstract class ${rawType.name}"
      }
      require(!Flag.Class.IS_INNER(kmClass.flags)) {
        "Cannot serialize inner class ${rawType.name}"
      }
      require(!Flag.Class.IS_OBJECT(kmClass.flags)) {
        "Cannot serialize object declaration ${rawType.name}"
      }
      require(!Flag.Class.IS_COMPANION_OBJECT(kmClass.flags)) {
        "Cannot serialize companion object declaration ${rawType.name}"
      }
      require(!Flag.IS_SEALED(kmClass.flags)) {
        "Cannot reflectively serialize sealed class ${rawType.name}. Please register an adapter."
      }

      val ktConstructor = KtConstructor.create(rawType, kmClass) ?: return null
      val parametersByName = ktConstructor.parameters.associateBy { it.name }

      val bindingsByName = LinkedHashMap<String, KotlinJsonAdapter.Binding<Any, Any?>>()

      // TODO this doesn't cover platform types
      val allPropertiesSequence = kmClass.properties.asSequence() +
        generateSequence(rawType) { it.superclass }
          .mapNotNull { it.header()?.toKmClass() }
          .flatMap { it.properties.asSequence() }
          .filterNot { Flag.IS_PRIVATE(it.flags) || Flag.IS_PRIVATE_TO_THIS(it.flags) }
          .filter { Flag.Property.IS_VAR(it.flags) }

      val signatureSearcher = JvmSignatureSearcher(rawType)

      for (property in allPropertiesSequence.distinctBy { it.name }) {
        val propertyField = signatureSearcher.field(property)
        val parameterData = parametersByName[property.name]

        if (Modifier.isTransient(propertyField?.modifiers ?: 0)) {
          parameterData?.run {
            require(declaresDefaultValue) {
              "No default value for transient constructor parameter '$name' on type '${rawType.canonicalName}'"
            }
          }
          continue
        }

        if (parameterData != null) {
          require(parameterData.km.type valueEquals property.returnType) {
            "'${property.name}' has a constructor parameter of type ${parameterData.km.type?.canonicalName} but a property of type ${property.returnType.canonicalName}."
          }
        }

        if (!Flag.Property.IS_VAR(property.flags) && parameterData == null) continue

        val getterMethod = signatureSearcher.getter(property)
        val setterMethod = signatureSearcher.setter(property)
        val annotationsMethod = signatureSearcher.syntheticMethodForAnnotations(property)

        val propertyData = KtProperty(
          km = property,
          jvmField = propertyField,
          jvmGetter = getterMethod,
          jvmSetter = setterMethod,
          jvmAnnotationsMethod = annotationsMethod,
          parameter = parameterData
        )
        val allAnnotations = propertyData.annotations.toMutableList()
        val jsonAnnotation = allAnnotations.filterIsInstance<Json>().firstOrNull()

        val name = jsonAnnotation?.name ?: property.name
        val resolvedPropertyType = resolve(type, rawType, propertyData.javaType)
        val adapter = moshi.adapter<Any>(
          resolvedPropertyType,
          Util.jsonAnnotations(allAnnotations.toTypedArray()),
          property.name
        )

        @Suppress("UNCHECKED_CAST")
        bindingsByName[property.name] = KotlinJsonAdapter.Binding(
          name,
          jsonAnnotation?.name ?: name,
          adapter,
          propertyData
        )
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

      val nonTransientBindings = bindings.filterNotNull()
      val options = JsonReader.Options.of(*nonTransientBindings.map { it.name }.toTypedArray())
      return KotlinJsonAdapter(ktConstructor, bindings, nonTransientBindings, options).nullSafe()
    }

  private infix fun KmType?.valueEquals(other: KmType?): Boolean {
    return when {
      this === other -> true
      this != null && other != null -> {
        // Note we don't check abbreviatedType because typealiases and their backing types are equal
        // for our purposes.
        arguments valueEquals other.arguments &&
          classifier == other.classifier &&
          flags == other.flags &&
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
      for (i in 0 until size) {
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
        variance == other.variance &&
          type valueEquals other.type
      }
      else -> false
    }
  }

  private infix fun KmFlexibleTypeUpperBound?.valueEquals(
    other: KmFlexibleTypeUpperBound?
  ): Boolean {
    return when {
      this === other -> true
      this != null && other != null -> {
        typeFlexibilityId == other.typeFlexibilityId &&
          type valueEquals other.type
      }
      else -> false
    }
  }
}

private fun Class<*>.header(): KotlinClassHeader? {
  val metadata = getAnnotation(KOTLIN_METADATA) ?: return null
  return with(metadata) {
    KotlinClassHeader(
      kind = kind,
      metadataVersion = metadataVersion,
      bytecodeVersion = bytecodeVersion,
      data1 = data1,
      data2 = data2,
      extraString = extraString,
      packageName = packageName,
      extraInt = extraInt
    )
  }
}

private fun KotlinClassHeader.toKmClass(): KmClass? {
  val classMetadata = KotlinClassMetadata.read(this)
  if (classMetadata !is KotlinClassMetadata.Class) {
    return null
  }

  return classMetadata.toKmClass()
}

private class JvmSignatureSearcher(private val clazz: Class<*>) {

  fun syntheticMethodForAnnotations(
    kmProperty: KmProperty
  ): Method? = kmProperty.syntheticMethodForAnnotations?.let { signature ->
    findMethod(clazz, signature)
  }

  fun getter(
    kmProperty: KmProperty
  ): Method? = kmProperty.getterSignature?.let { signature ->
    findMethod(clazz, signature)
  }

  fun setter(
    kmProperty: KmProperty
  ): Method? = kmProperty.setterSignature?.let { signature ->
    findMethod(clazz, signature)
  }

  fun field(
    kmProperty: KmProperty
  ): Field? = kmProperty.fieldSignature?.let { signature ->
    findField(clazz, signature)
  }

  private fun findMethod(sourceClass: Class<*>, signature: JvmMethodSignature): Method {
    val parameterTypes = signature.decodeParameterTypes()
    return try {
      if (parameterTypes.isEmpty()) {
        // Save the empty copy
        sourceClass.getDeclaredMethod(signature.name)
      } else {
        sourceClass.getDeclaredMethod(signature.name, *parameterTypes.toTypedArray())
      }
    } catch (e: NoSuchMethodException) {
      // Try finding the superclass method
      val superClass = sourceClass.superclass
      if (superClass != Any::class.java) {
        return findMethod(superClass, signature)
      } else {
        throw e
      }
    }
  }

  private fun findField(sourceClass: Class<*>, signature: JvmFieldSignature): Field {
    return try {
      sourceClass.getDeclaredField(signature.name)
    } catch (e: NoSuchFieldException) {
      // Try finding the superclass field
      val superClass = sourceClass.superclass
      if (superClass != Any::class.java) {
        return findField(superClass, signature)
      } else {
        throw e
      }
    }
  }
}
