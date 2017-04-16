@file:Suppress("UNCHECKED_CAST")

package com.squareup.moshi

import com.squareup.moshi.ClassJsonAdapter.*
import java.lang.reflect.Field
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
 * the default value is used. If no default value is present a [JsonDataException] will be
 * thrown during deserialization.
 *
 * Data classes to be (de-)serialized must conform to the following rules:
 *
 * - The primary constructor may not contain transient properties unless a default value is provided.
 * - If the class is a platform type (kotlin.*), it may not contain private fields.
 *
 * In most cases, delegated properties should be annotated with `@delegate:Transient` to
 * avoid the inclusion of the delegate in the serialized JSON.
 */
class KotlinJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
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

    constructor.parameters
        .asSequence()
        .filterNot { it.isOptional }
        .map { concretePropertyByName[it.name]!! }
        .firstOrNull { it.findAnnotation<Transient>() != null }
        ?.let { throw IllegalArgumentException("Transient properties in primary constructor " +
            "without default values are unsupported. ($it)") }

    val isPlatformType = isPlatformType(Types.getRawType(type))
    val notIncludedParams = constructorParameterByName
        .filterNot { it.value.isOptional }
        .keys.map { concretePropertyByName[it]!! }
        .filter { !includeField(isPlatformType, it.javaField!!.modifiers) }
    if (notIncludedParams.isNotEmpty()) {
      throw IllegalArgumentException("Primary constructor contains parameters " +
          "not found in serialized JSON: $notIncludedParams")
    }

    val propertyByParam = constructor.parameters.associate { it to concretePropertyByName[it.name]!! }
    val adapterByParam = constructor.parameters.map { param ->
      val javaField = propertyByParam[param]!!.javaField!!
      val paramType = Types.resolve(javaField.type, Types.getRawType(javaField.type), javaField.genericType)
      val paramAnnotations = param.annotations.toSet()

      return@map moshi.adapter<Any>(paramType, paramAnnotations)
    }.toTypedArray<JsonAdapter<*>>()

    return KotlinJsonAdapter(constructor, type.createFieldBindings(moshi), propertyByParam, adapterByParam)
  }

  /**
   * A [Sequence] of supertypes, including the type itself
   * and excluding [Object].
   */
  private val Type.hierarchy get() = generateSequence(this) {
    val superClass = Types.getGenericSuperclass(it)
    return@generateSequence if (superClass != Object::class.java) superClass else null
  }

  private fun Type.createFieldBindings(moshi: Moshi): LinkedHashMap<String, ClassJsonAdapter.FieldBinding<*>> {
    val fields = LinkedHashMap<String, FieldBinding<*>>()
    for (type in hierarchy) {
      ClassJsonAdapter.createFieldBindings(moshi, type, fields)
    }
    return fields
  }

}

@Suppress("LoopToCallChain")
internal class KotlinJsonAdapter<T>(
    private val constructor: KFunction<T>,
    fieldBindings: LinkedHashMap<String, ClassJsonAdapter.FieldBinding<*>>,
    propertyByParam: Map<KParameter, KProperty1<out Any, Any?>>,
    private val adapterByParamIdx: Array<JsonAdapter<*>>
) : JsonAdapter<T>() {

  private val options: JsonReader.Options
  private val fullFieldsArray = fieldBindings.values.toTypedArray()
  private val nonParamFieldsArray: Array<FieldBinding<*>>

  init {
    val paramNames = constructor.parameters.map { it.name!! }.toSet()
    val jsonNamesForParams = constructor.parameters
        .map { propertyByParam[it]!! }
        .map { it.javaField!!.getAnnotation(Json::class.java)?.name ?: it.name }
        .toTypedArray()
    val jsonNamesForFields = fieldBindings
        .keys
        .filter { it !in paramNames }
        .toTypedArray()
    nonParamFieldsArray = fieldBindings
        .entries
        .filter { it.key !in paramNames }
        .map { it.value }
        .toTypedArray()

    options = JsonReader.Options.of(*(jsonNamesForParams + jsonNamesForFields))
  }

  override fun fromJson(reader: JsonReader): T {
    try {
      val valuesByParam = mutableMapOf<KParameter, Any?>()
      var lazyPendingBindings: MutableList<Pair<Field, Any>>? = null

      reader.beginObject()
      while (reader.hasNext()) {
        val index = reader.selectName(options)
        if (index == -1) {
          reader.nextName()
          reader.skipValue()
          continue
        }

        val numParams = constructor.parameters.size
        if (index < numParams) {
          val param = constructor.parameters[index]
          valuesByParam[param] = adapterByParamIdx[index].fromJson(reader)
        } else {
          val fieldBinding = nonParamFieldsArray[index - numParams]
          val field = fieldBinding.field
          val fieldValue = fieldBinding.adapter.fromJson(reader)

          // Many data classes do not contain any properties
          // outside of the primary constructor, so the allocation
          // of the list is deferred to here.
          if (lazyPendingBindings == null) {
            lazyPendingBindings = mutableListOf()
          }
          lazyPendingBindings.add(field to fieldValue)
        }
      }
      reader.endObject()

      for (param in constructor.parameters) {
        if (!param.isOptional && param !in valuesByParam) {
          throw JsonDataException("JSON is missing value for ${param.name}")
        }
      }

      return constructor.callBy(valuesByParam).apply {
        lazyPendingBindings?.forEach { it.first.set(this, it.second) }
      }
    } catch (e: IllegalAccessException) {
      throw AssertionError()
    }
  }

  override fun toJson(writer: JsonWriter, value: T) {
    try {
      writer.beginObject()
      for (fieldBinding in fullFieldsArray) {
        writer.name(fieldBinding.name)
        fieldBinding.write(writer, value)
      }
      writer.endObject()
    } catch (e: IllegalAccessException) {
      throw AssertionError()
    }

  }

}