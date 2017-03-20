@file:Suppress("UNCHECKED_CAST")

package com.squareup.moshi

import com.squareup.moshi.ClassJsonAdapter.includeField
import com.squareup.moshi.ClassJsonAdapter.isPlatformType
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

/**
 * A [JsonAdapter.Factory] for Kotlin data classes. If this is added to Moshi,
 * data classes will be treated differently from other classes.
 *
 * Moshi will call its primary constructor to create the object. This allows for delegated properties
 * to be initialized correctly. If the JSON does not contain a value for a parameter with a default value
 * and [crashOnMissingValues] is set to false, the default value is used. If no default value is present,
 * an [IllegalArgumentException] will be thrown during deserialization.
 *
 * Data classes to be (de-)serialized must conform to the following rules:
 *
 * - Any properties not found in the primary constructor must be transient, including delegated properties.
 * - The primary constructor may not contain transient properties unless a default value is provided.
 * - If the class is a platform type (kotlin.*), it may not contain private fields.
 */
class KotlinJsonAdapterFactory(private val crashOnMissingValues: Boolean = false) : JsonAdapter.Factory {
  override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
    val kclass = (type as? Class<*>)?.kotlin ?: return null
    if (!kclass.isData) return null
    // This gives us a few guarantees:
    // 1. Primary constructor exists
    // 2. Constructor params are var/val and have backing fields
    // 3. Class is not abstract or an interface
    // 4. Class is final

    val constructor = kclass.primaryConstructor!!
    constructor.isAccessible = true

    val concretePropertyByName = kclass.declaredMemberProperties
        .filter { it.javaField != null }
        .associateBy { it.name }
    val constructorParameterByName = constructor.parameters.associateBy { it.name }
    for ((name, property) in concretePropertyByName) {
      if (name !in constructorParameterByName.keys &&
          !Modifier.isTransient(property.javaField!!.modifiers)) {
        throw IllegalArgumentException("Property $name is not transient. " +
            "Any properties not found in the primary constructor need to be transient. " +
            "If this is a delegated property, mark the delegate as transient " +
            "using @delegate:Transient.")
      }
    }

    constructor.parameters
        .asSequence()
        .filterNot { it.isOptional }
        .map { concretePropertyByName[it.name]!! }
        .firstOrNull { it.findAnnotation<Transient>() != null }
        ?.let { throw IllegalArgumentException("Transient properties in primary constructor are unsupported. ($it)") }

    val isPlatformType = isPlatformType(Types.getRawType(type))
    val notIncludedParams = constructorParameterByName
        .filterNot { it.value.isOptional }
        .keys.map { concretePropertyByName[it]!! }
        .filter { !includeField(isPlatformType, it.javaField!!.modifiers) }
    if (notIncludedParams.isNotEmpty()) {
      throw IllegalArgumentException("Primary constructor contains parameters " +
          "not found in serialized JSON: $notIncludedParams")
    }

    val toJsonAdapter = ClassJsonAdapter.FACTORY.create(type, annotations, moshi)
    val propertyByParam = constructor.parameters.associate { it to concretePropertyByName[it.name]!! }
    val adapterByParam = constructor.parameters.associate { param ->
      val javaField = propertyByParam[param]!!.javaField!!
      val paramType = Types.resolve(javaField.type, Types.getRawType(javaField.type), javaField.genericType)
      val annotations = param.annotations.toSet()

      param to moshi.adapter<Any>(paramType, annotations)
    }

    return KotlinJsonAdapter(
        constructor,
        toJsonAdapter as JsonAdapter<Any?>,
        propertyByParam,
        adapterByParam,
        crashOnMissingValues
    )
  }

}

@Suppress("LoopToCallChain")
internal class KotlinJsonAdapter<T>(
    private val constructor: KFunction<T>,
    private val toJsonAdapter: JsonAdapter<T>,
    propertyByParam: Map<KParameter, KProperty1<out Any, Any?>>,
    private val adapterByParam: Map<KParameter, JsonAdapter<*>>,
    private val crashOnMissingValues: Boolean
) : JsonAdapter<T>() {

  private val jsonNames = constructor.parameters
      .map { propertyByParam[it]!! }
      .map { it.javaField!!.getAnnotation(Json::class.java)?.name ?: it.name }
      .toTypedArray()
  private val options = JsonReader.Options.of(*jsonNames)

  override fun fromJson(reader: JsonReader): T {
    try {
      val valuesByParam = mutableMapOf<KParameter, Any?>()

      reader.beginObject()
      while (reader.hasNext()) {
        val index = reader.selectName(options)
        val param = if (index != -1) {
          constructor.parameters[index]
        } else {
          reader.nextName()
          reader.skipValue()
          continue
        }

        valuesByParam[param] = adapterByParam[param]!!.fromJson(reader)
      }
      reader.endObject()

      for (param in constructor.parameters) {
        if ((crashOnMissingValues || !param.isOptional) && param !in valuesByParam) {
          throw IllegalArgumentException("JSON is missing value for ${param.name}")
        }
      }

      return constructor.callBy(valuesByParam)
    } catch (e: IllegalAccessException) {
      throw AssertionError()
    }
  }

  override fun toJson(writer: JsonWriter, value: T) {
    toJsonAdapter.toJson(writer, value)
  }

}