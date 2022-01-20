/*
 * Copyright (C) 2015 Square, Inc.
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

import com.squareup.moshi.internal.checkNull
import com.squareup.moshi.internal.isKotlin
import com.squareup.moshi.internal.isPlatformType
import com.squareup.moshi.internal.jsonAnnotations
import com.squareup.moshi.internal.jsonName
import com.squareup.moshi.internal.resolve
import com.squareup.moshi.internal.rethrowCause
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier.isAbstract
import java.lang.reflect.Modifier.isProtected
import java.lang.reflect.Modifier.isPublic
import java.lang.reflect.Modifier.isStatic
import java.lang.reflect.Modifier.isTransient
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Emits a regular class as a JSON object by mapping Java fields to JSON object properties.
 *
 * # Platform Types
 *
 * Fields from platform classes are omitted from both serialization and deserialization unless they
 * are either public or protected. This includes the following packages and their subpackages:
 *  * `android.*`
 *  * `androidx.*`
 *  * `java.*`
 *  * `javax.*`
 *  * `kotlin.*`
 *  * `kotlinx.*`
 *  * `scala.*`
 */
internal class ClassJsonAdapter<T>(
  private val classFactory: ClassFactory<T>,
  fieldsMap: Map<String, FieldBinding<*>>
) : JsonAdapter<T>() {
  private val fieldsArray = fieldsMap.values.toTypedArray()
  private val options = JsonReader.Options.of(*fieldsMap.keys.toTypedArray())

  override fun fromJson(reader: JsonReader): T {
    val result: T = try {
      classFactory.newInstance()
    } catch (e: InstantiationException) {
      throw RuntimeException(e)
    } catch (e: InvocationTargetException) {
      throw e.rethrowCause()
    } catch (e: IllegalAccessException) {
      throw AssertionError()
    }

    try {
      reader.beginObject()
      while (reader.hasNext()) {
        val index = reader.selectName(options)
        if (index == -1) {
          reader.skipName()
          reader.skipValue()
          continue
        }
        fieldsArray[index].read(reader, result)
      }
      reader.endObject()
      return result
    } catch (e: IllegalAccessException) {
      throw AssertionError()
    }
  }

  override fun toJson(writer: JsonWriter, value: T?) {
    try {
      writer.beginObject()
      for (fieldBinding in fieldsArray) {
        writer.name(fieldBinding.name)
        fieldBinding.write(writer, value)
      }
      writer.endObject()
    } catch (e: IllegalAccessException) {
      throw AssertionError()
    }
  }

  override fun toString() = "JsonAdapter($classFactory)"

  internal class FieldBinding<T>(val name: String, val field: Field, val adapter: JsonAdapter<T>) {
    fun read(reader: JsonReader, value: Any?) {
      val fieldValue = adapter.fromJson(reader)
      field.set(value, fieldValue)
    }

    @Suppress("UNCHECKED_CAST") // We require that field's values are of type T.
    fun write(writer: JsonWriter, value: Any?) {
      val fieldValue = field.get(value) as T
      adapter.toJson(writer, fieldValue)
    }
  }

  companion object Factory : JsonAdapter.Factory {
    override fun create(
      type: Type,
      annotations: Set<Annotation>,
      moshi: Moshi
    ): JsonAdapter<*>? {
      if (type !is Class<*> && type !is ParameterizedType) {
        return null
      }
      val rawType = type.rawType
      if (rawType.isInterface || rawType.isEnum) return null
      if (annotations.isNotEmpty()) return null
      if (rawType.isPlatformType) {
        type.throwIfIsCollectionClass(List::class.java)
        type.throwIfIsCollectionClass(Set::class.java)
        type.throwIfIsCollectionClass(Map::class.java)
        type.throwIfIsCollectionClass(Collection::class.java)

        val messagePrefix = buildString {
          append("Platform $rawType")
          if (type is ParameterizedType) {
            append(" in $type")
          }
        }
        throw IllegalArgumentException(
          "$messagePrefix requires explicit JsonAdapter to be registered"
        )
      }

      require(!rawType.isAnonymousClass) {
        "Cannot serialize anonymous class ${rawType.name}"
      }

      require(!rawType.isLocalClass) {
        "Cannot serialize local class ${rawType.name}"
      }

      val isNonStaticNestedClass = rawType.enclosingClass != null && !isStatic(rawType.modifiers)
      require(!isNonStaticNestedClass) {
        "Cannot serialize non-static nested class ${rawType.name}"
      }

      require(!isAbstract(rawType.modifiers)) {
        "Cannot serialize abstract class ${rawType.name}"
      }

      require(!rawType.isKotlin) {
        "Cannot serialize Kotlin type ${rawType.name}. Reflective serialization of Kotlin classes without using kotlin-reflect has undefined and unexpected behavior. Please use KotlinJsonAdapterFactory from the moshi-kotlin artifact or use code gen from the moshi-kotlin-codegen artifact."
      }

      val classFactory = ClassFactory.get<Any>(rawType)
      val fields = sortedMapOf<String, FieldBinding<*>>()
      var parentType = type
      while (parentType != Any::class.java) {
        createFieldBindings(moshi, parentType, fields)
        parentType = Types.getGenericSuperclass(parentType)
      }
      return ClassJsonAdapter(classFactory, fields).nullSafe()
    }

    /**
     * Throw clear error messages for the common beginner mistake of using the concrete
     * collection classes instead of the collection interfaces, eg: ArrayList instead of List.
     */
    private fun Type.throwIfIsCollectionClass(collectionInterface: Class<*>) {
      require(!collectionInterface.isAssignableFrom(rawType)) {
        "No JsonAdapter for $this, you should probably use ${collectionInterface.simpleName} instead of ${rawType.simpleName} (Moshi only supports the collection interfaces by default) or else register a custom JsonAdapter."
      }
    }

    /** Creates a field binding for each of declared field of `type`.  */
    private fun createFieldBindings(
      moshi: Moshi,
      type: Type,
      fieldBindings: MutableMap<String, FieldBinding<*>>
    ) {
      val rawType = type.rawType
      val platformType = rawType.isPlatformType
      for (field in rawType.declaredFields) {
        if (!includeField(platformType, field.modifiers)) continue
        val jsonAnnotation = field.getAnnotation(Json::class.java)
        if (jsonAnnotation != null && jsonAnnotation.ignore) continue

        // Look up a type adapter for this type.
        val fieldType = field.genericType.resolve(type, rawType)
        val annotations = field.jsonAnnotations
        val fieldName = field.name
        val adapter = moshi.adapter<Any>(
          type = fieldType,
          annotations = annotations,
          fieldName = fieldName
        )

        // Create the binding between field and JSON.
        field.isAccessible = true

        // Store it using the field's name. If there was already a field with this name, fail!
        val jsonName = jsonAnnotation.jsonName(fieldName)
        val fieldBinding = FieldBinding(jsonName, field, adapter)
        val replaced = fieldBindings.put(jsonName, fieldBinding)
        checkNull(replaced) {
          "Conflicting fields:\n    ${it.field}\n    ${fieldBinding.field}"
        }
      }
    }

    /** Returns true if fields with `modifiers` are included in the emitted JSON.  */
    private fun includeField(platformType: Boolean, modifiers: Int): Boolean {
      return if (isStatic(modifiers) || isTransient(modifiers)) {
        false
      } else {
        isPublic(modifiers) || isProtected(modifiers) || !platformType
      }
    }
  }
}
