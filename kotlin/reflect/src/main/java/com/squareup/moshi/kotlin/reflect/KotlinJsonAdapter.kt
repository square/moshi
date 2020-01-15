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
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmClassifier.TypeAlias
import kotlinx.metadata.KmClassifier.TypeParameter
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFlexibleTypeUpperBound
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.isLocal
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.AbstractMap.SimpleEntry
import kotlin.collections.Map.Entry

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
    private val constructor: ConstructorData,
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
            "Multiple values for '${binding.property.name}' at ${reader.path}")
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
      if (values[i] === ABSENT_VALUE && !constructor.parameters[i].isOptional) {
        if (!constructor.parameters[i].km.type!!.isNullable) {
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
      val property: PropertyData,
      val propertyIndex: Int = property.parameter?.index ?: -1
  ) {

    fun get(value: K): Any? {
      property.jvmGetter?.let { getter ->
        return getter.invoke(value)
      }
      property.jvmField?.let {
        return it.get(value)
      }

      return null
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
      private val parameterKeys: List<ParameterData>,
      private val parameterValues: Array<Any?>
  ) : AbstractMap<ParameterData, Any?>() {

    override val entries: Set<Entry<ParameterData, Any?>>
      get() {
        val allPossibleEntries = parameterKeys.mapIndexed { index, value ->
          SimpleEntry<ParameterData, Any?>(value, parameterValues[index])
        }
        return allPossibleEntries.filterTo(LinkedHashSet<Entry<ParameterData, Any?>>()) {
          it.value !== ABSENT_VALUE
        }
      }

    override fun containsKey(key: ParameterData) = parameterValues[key.index] !== ABSENT_VALUE

    override fun get(key: ParameterData): Any? {
      val value = parameterValues[key.index]
      return if (value !== ABSENT_VALUE) value else null
    }
  }
}

class KotlinJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi)
      : JsonAdapter<*>? {
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

    val kmClass = rawType.toKmClass(throwOnNotClass = true) ?: return null

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

    val kmConstructor = kmClass.constructors.find { Flag.Constructor.IS_PRIMARY(it.flags) }
        ?: return null
    val kmConstructorSignature = kmConstructor.signature?.asString() ?: return null
    val constructorsBySignature = rawType.declaredConstructors.associateBy { it.jvmMethodSignature }
    val jvmConstructor = constructorsBySignature[kmConstructorSignature] ?: return null
    val parameterAnnotations = jvmConstructor.parameterAnnotations
    val parameterTypes = jvmConstructor.parameterTypes
    val parameters = kmConstructor.valueParameters.withIndex()
        .map { (index, kmParam) ->
          ParameterData(kmParam, index, parameterTypes[index], parameterAnnotations[index].toList())
        }

    val anyOptional = parameters.any { it.isOptional }
    val actualConstructor = if (anyOptional) {
      val prefix = jvmConstructor.jvmMethodSignature.removeSuffix(")V")
      val parameterCount = jvmConstructor.parameterTypes.size
      val maskParamsToAdd = if (parameterCount == 0) {
        0
      } else {
        (parameterCount + 31) / 32
      }
      val defaultConstructorSignature = buildString {
        append(prefix)
        repeat(maskParamsToAdd) {
          append("I")
        }
        append(Util.DEFAULT_CONSTRUCTOR_MARKER!!.descriptor)
        append(")V")
      }
      constructorsBySignature[defaultConstructorSignature] ?: return null
    } else {
      jvmConstructor
    }

    val constructorData = ConstructorData(
        rawType,
        kmConstructor,
        actualConstructor,
        parameters,
        anyOptional
    )
    val parametersByName = parameters.associateBy { it.name }

    val bindingsByName = LinkedHashMap<String, KotlinJsonAdapter.Binding<Any, Any?>>()

    // TODO this doesn't cover platform types
    val allPropertiesSequence = kmClass.properties.asSequence() +
        generateSequence(rawType) { it.superclass }
            .mapNotNull { it.toKmClass(false) }
            .flatMap { it.properties.asSequence() }
            .filterNot { Flag.IS_PRIVATE(it.flags) || Flag.IS_PRIVATE_TO_THIS(it.flags) }
            .filter { Flag.Property.IS_VAR(it.flags) }

    for (property in allPropertiesSequence.distinctBy { it.name }) {
      val propertyField = property.fieldSignature?.let { signature ->
        val signatureString = signature.asString()
        rawType.declaredFields.find { it.jvmFieldSignature == signatureString }
      }
      val parameterData = parametersByName[property.name]

      if (Modifier.isTransient(propertyField?.modifiers ?: 0)) {
        if (parameterData == null) {
          continue
        }
        require(parameterData.isOptional) {
          "No default value for transient constructor parameter '${parameterData.name}' on type '${rawType.canonicalName}'"
        }
        continue
      }

      if (parameterData != null) {
        require(parameterData.km.type isEqualTo property.returnType) {
          "'${property.name}' has a constructor parameter of type ${parameterData.km.type?.canonicalName} but a property of type ${property.returnType.canonicalName}."
        }
      }

      if (!Flag.Property.IS_VAR(property.flags) && parameterData == null) continue

      val getterMethod = property.getterSignature?.let { signature ->
        val signatureString = signature.asString()
        rawType.allMethods().find { it.jvmMethodSignature == signatureString }
      }
      val setterMethod = property.setterSignature?.let { signature ->
        val signatureString = signature.asString()
        rawType.allMethods().find { it.jvmMethodSignature == signatureString }
      }
      val annotationsMethod = property.syntheticMethodForAnnotations?.let { signature ->
        val signatureString = signature.asString()
        rawType.allMethods().find { it.jvmMethodSignature == signatureString }
      }

      val propertyData = PropertyData(
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
          resolvedPropertyType, Util.jsonAnnotations(allAnnotations.toTypedArray()), property.name)

      @Suppress("UNCHECKED_CAST")
      bindingsByName[property.name] = KotlinJsonAdapter.Binding(
          name,
          jsonAnnotation?.name ?: name,
          adapter,
          propertyData
      )
    }

    val bindings = ArrayList<KotlinJsonAdapter.Binding<Any, Any?>?>()

    for (parameter in constructorData.parameters) {
      val binding = bindingsByName.remove(parameter.name)
      require(binding != null || parameter.isOptional) {
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
    return KotlinJsonAdapter(constructorData, bindings, nonTransientBindings, options).nullSafe()
  }

  private infix fun KmType?.isEqualTo(other: KmType?): Boolean {
    return when {
      this === other -> true
      this != null && other != null -> {
        abbreviatedType == other.abbreviatedType
            && arguments areEqualTo other.arguments
            && classifier == other.classifier
            && flags == other.flags
            && flexibleTypeUpperBound isEqualTo other.flexibleTypeUpperBound
            && outerType isEqualTo other.outerType
      }
      this == null && other == null -> true
      else -> false
    }
  }

  private infix fun Collection<KmTypeProjection>.areEqualTo(
      other: Collection<KmTypeProjection>): Boolean {
    // check collections aren't same
    if (this !== other) {
      // fast check of sizes
      if (this.size != other.size) return false
      val areNotEqual = this.asSequence()
          .zip(other.asSequence())
          // check this and other contains same elements at position
          .map { (fromThis, fromOther) -> fromThis isEqualTo fromOther }
          // searching for first negative answer
          .contains(false)
      if (areNotEqual) return false
    }
    // collections are same or they are contains same elements with same order
    return true
  }

  private infix fun KmTypeProjection?.isEqualTo(other: KmTypeProjection?): Boolean {
    return when {
      this === other -> true
      this != null && other != null -> {
        variance == other.variance
            && type isEqualTo other.type
      }
      this == null && other == null -> true
      else -> false
    }
  }

  private infix fun KmFlexibleTypeUpperBound?.isEqualTo(other: KmFlexibleTypeUpperBound?): Boolean {
    return when {
      this === other -> true
      this != null && other != null -> {
        typeFlexibilityId == other.typeFlexibilityId
            && type isEqualTo other.type
      }
      this == null && other == null -> true
      else -> false
    }
  }
}

private val KmType.isNullable: Boolean get() = Flag.Type.IS_NULLABLE(flags)
private val KmType.canonicalName: String
  get() {
    return buildString {
      val classifierString = when (val cl = classifier) {
        is KmClassifier.Class -> createClassName(cl.name)
        is TypeAlias -> createClassName(cl.name)
        is TypeParameter -> arguments[cl.id].type?.canonicalName ?: "TypeVar(${cl.id})"
      }
      append(classifierString)

      val args = arguments.joinToString(", ") {
        "${it.variance?.name} ${it.type?.canonicalName ?: "*"}".trim()
      }

      if (args.isNotBlank()) {
        append('<')
        append(args)
        append('>')
      }

      // TODO not sure if we care about expressing the other type information here
    }
  }

/**
 * Creates a canonical class name as represented in Metadata's [kotlinx.metadata.ClassName], where
 * package names in this name are separated by '/' and class names are separated by '.'.
 *
 * For example: `"org/foo/bar/Baz.Nested"`.
 *
 * Local classes are prefixed with ".", but for KotlinPoetMetadataSpecs' use case we don't deal
 * with those.
 */
private fun createClassName(kotlinMetadataName: String): String {
  require(!kotlinMetadataName.isLocal) {
    "Local/anonymous classes are not supported!"
  }
  // Top-level: package/of/class/MyClass
  // Nested A:  package/of/class/MyClass.NestedClass
  val simpleName = kotlinMetadataName.substringAfterLast(
      '/', // Drop the package name, e.g. "package/of/class/"
      '.' // Drop any enclosing classes, e.g. "MyClass."
  )
  val packageName = kotlinMetadataName.substringBeforeLast("/", missingDelimiterValue = "")
  val simpleNames = kotlinMetadataName.removeSuffix(simpleName)
      .removeSuffix(".") // Trailing "." if any
      .removePrefix(packageName)
      .removePrefix("/")
      .let {
        if (it.isNotEmpty()) {
          it.split(".")
        } else {
          // Don't split, otherwise we end up with an empty string as the first element!
          emptyList()
        }
      }
      .plus(simpleName)

  return "${packageName.replace("/", ".")}.${simpleNames.joinToString(".")}"
}

@Suppress("SameParameterValue")
private fun String.substringAfterLast(vararg delimiters: Char): String {
  val index = lastIndexOfAny(delimiters)
  return if (index == -1) this else substring(index + 1, length)
}

private fun defaultPrimitiveValue(type: Type): Any? =
    if (type is Class<*> && type.isPrimitive) {
      when (type) {
        Boolean::class.java -> false
        Char::class.java -> 0.toChar()
        Byte::class.java -> 0.toByte()
        Short::class.java -> 0.toShort()
        Int::class.java -> 0
        Float::class.java -> 0f
        Long::class.java -> 0L
        Double::class.java -> 0.0
        Void.TYPE -> throw IllegalStateException("Parameter with void type is illegal")
        else -> throw UnsupportedOperationException("Unknown primitive: $type")
      }
    } else null

private fun Class<*>.toKmClass(throwOnNotClass: Boolean): KmClass? {
  val metadata = getAnnotation(KOTLIN_METADATA) ?: return null
  val header = with(metadata) {
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
  val classMetadata = KotlinClassMetadata.read(header)
  if (classMetadata !is KotlinClassMetadata.Class) {
    if (throwOnNotClass) {
      throw IllegalStateException("Cannot serialize class with metadata $classMetadata")
    } else {
      return null
    }
  }

  return classMetadata.toKmClass()
}

private fun Class<*>.allMethods(): Sequence<Method> {
  return declaredMethods.asSequence() + methods.asSequence()
}

internal data class ParameterData(
    val km: KmValueParameter,
    val index: Int,
    val rawType: Class<*>,
    val annotations: List<Annotation>
) {
  val name get() = km.name
  val isOptional get() = Flag.ValueParameter.DECLARES_DEFAULT_VALUE(km.flags)
}

internal data class ConstructorData(
    val type: Class<*>,
    val km: KmConstructor,
    val jvm: Constructor<*>,
    val parameters: List<ParameterData>,
    val isDefault: Boolean
) {
  init {
    jvm.isAccessible = true
  }

  fun <R> callBy(args: Map<ParameterData, Any?>): R {
    val arguments = ArrayList<Any?>(parameters.size)
    var mask = 0
    val masks = ArrayList<Int>(1)
    var index = 0

    @Suppress("UseWithIndex")
    for (parameter in parameters) {
      if (index != 0 && index % Integer.SIZE == 0) {
        masks.add(mask)
        mask = 0
      }

      when {
        args.containsKey(parameter) -> {
          arguments.add(args[parameter])
        }
        parameter.isOptional -> {
          arguments += defaultPrimitiveValue(parameter.rawType)
          mask = mask or (1 shl (index % Integer.SIZE))
        }
        else -> {
          throw IllegalArgumentException(
              "No argument provided for a required parameter: $parameter")
        }
      }

      index++
    }

    if (!isDefault) {
      @Suppress("UNCHECKED_CAST")
      return jvm.newInstance(*arguments.toTypedArray()) as R
    }

    masks += mask
    arguments.addAll(masks)

    // DefaultConstructorMarker
    arguments.add(null)

    @Suppress("UNCHECKED_CAST")
    return jvm.newInstance(*arguments.toTypedArray()) as R
  }
}

internal data class PropertyData(
    val km: KmProperty,
    val jvmField: Field?,
    val jvmGetter: Method?,
    val jvmSetter: Method?,
    val jvmAnnotationsMethod: Method?,
    val parameter: ParameterData?
) {
  init {
    jvmField?.isAccessible = true
    jvmGetter?.isAccessible = true
    jvmSetter?.isAccessible = true
  }

  val name get() = km.name

  val javaType = jvmField?.genericType
      ?: jvmGetter?.genericReturnType
      ?: jvmSetter?.genericReturnType
      ?: error("No type information available for property '${km.name}' with type '${km.returnType.canonicalName}'.")

  val annotations: Set<Annotation> by lazy {
    val set = LinkedHashSet<Annotation>()
    jvmField?.annotations?.let { set += it }
    jvmGetter?.annotations?.let { set += it }
    jvmSetter?.annotations?.let { set += it }
    jvmAnnotationsMethod?.annotations?.let { set += it }
    parameter?.annotations?.let { set += it }
    set
  }
}
