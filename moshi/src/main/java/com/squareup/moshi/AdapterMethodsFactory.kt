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

import com.squareup.moshi.internal.canonicalize
import com.squareup.moshi.internal.checkNull
import com.squareup.moshi.internal.hasNullable
import com.squareup.moshi.internal.jsonAnnotations
import com.squareup.moshi.internal.knownNotNull
import com.squareup.moshi.internal.toStringWithAnnotations
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal class AdapterMethodsFactory(
  private val toAdapters: List<AdapterMethod>,
  private val fromAdapters: List<AdapterMethod>,
) : JsonAdapter.Factory {
  override fun create(
    type: Type,
    annotations: Set<Annotation>,
    moshi: Moshi,
  ): JsonAdapter<*>? {
    val toAdapter = get(toAdapters, type, annotations)
    val fromAdapter = get(fromAdapters, type, annotations)
    if (toAdapter == null && fromAdapter == null) return null

    val delegate: JsonAdapter<Any>? = if (toAdapter == null || fromAdapter == null) {
      try {
        moshi.nextAdapter(this, type, annotations)
      } catch (e: IllegalArgumentException) {
        val missingAnnotation = if (toAdapter == null) "@ToJson" else "@FromJson"
        throw IllegalArgumentException(
          "No $missingAnnotation adapter for ${type.toStringWithAnnotations(annotations)}",
          e,
        )
      }
    } else {
      null
    }

    toAdapter?.bind(moshi, this)
    fromAdapter?.bind(moshi, this)

    return object : JsonAdapter<Any>() {
      override fun toJson(writer: JsonWriter, value: Any?) {
        when {
          toAdapter == null -> knownNotNull(delegate).toJson(writer, value)

          !toAdapter.nullable && value == null -> writer.nullValue()

          else -> {
            try {
              toAdapter.toJson(moshi, writer, value)
            } catch (e: InvocationTargetException) {
              val cause = e.cause
              if (cause is IOException) throw cause
              throw JsonDataException("$cause at ${writer.path}", cause)
            }
          }
        }
      }

      override fun fromJson(reader: JsonReader): Any? {
        return when {
          fromAdapter == null -> knownNotNull(delegate).fromJson(reader)

          !fromAdapter.nullable && reader.peek() == JsonReader.Token.NULL -> reader.nextNull<Any>()

          else -> {
            try {
              fromAdapter.fromJson(moshi, reader)
            } catch (e: InvocationTargetException) {
              val cause = e.cause
              if (cause is IOException) throw cause
              throw JsonDataException("$cause at ${reader.path}", cause)
            }
          }
        }
      }

      override fun toString() = "JsonAdapter$annotations($type)"
    }
  }

  companion object {
    operator fun invoke(adapter: Any): AdapterMethodsFactory {
      val toAdapters = mutableListOf<AdapterMethod>()
      val fromAdapters = mutableListOf<AdapterMethod>()

      val classAndSuperclasses = generateSequence(adapter.javaClass) { it.superclass }.iterator()
      while (classAndSuperclasses.hasNext()) {
        val clazz = classAndSuperclasses.next()
        for (declaredMethod in clazz.declaredMethods) {
          if (declaredMethod.isAnnotationPresent(ToJson::class.java)) {
            val toAdapter = toAdapter(adapter, declaredMethod)
            val conflicting = get(toAdapters, toAdapter.type, toAdapter.annotations)
            checkNull(conflicting) {
              "Conflicting @ToJson methods:\n    ${it.method}\n    ${toAdapter.method}"
            }
            toAdapters.add(toAdapter)
          }
          if (declaredMethod.isAnnotationPresent(FromJson::class.java)) {
            val fromAdapter = fromAdapter(adapter, declaredMethod)
            val conflicting = get(fromAdapters, fromAdapter.type, fromAdapter.annotations)
            checkNull(conflicting) {
              "Conflicting @FromJson methods:\n    ${it.method}\n    ${fromAdapter.method}"
            }
            fromAdapters.add(fromAdapter)
          }
        }
      }

      require(toAdapters.isNotEmpty() || fromAdapters.isNotEmpty()) {
        "Expected at least one @ToJson or @FromJson method on ${adapter.javaClass.name}"
      }
      return AdapterMethodsFactory(toAdapters, fromAdapters)
    }

    /**
     * Returns an object that calls a `method` method on `adapter` in service of
     * converting an object to JSON.
     */
    private fun toAdapter(adapter: Any, method: Method): AdapterMethod {
      method.isAccessible = true
      val returnType = method.genericReturnType
      val parameterTypes = method.genericParameterTypes
      val parameterAnnotations = method.parameterAnnotations
      val methodSignatureIncludesJsonWriterAndJsonAdapter = parameterTypes.size >= 2 &&
        parameterTypes[0] == JsonWriter::class.java &&
        returnType == Void.TYPE &&
        parametersAreJsonAdapters(2, parameterTypes)
      return when {
        // void pointToJson(JsonWriter jsonWriter, Point point) {
        // void pointToJson(JsonWriter jsonWriter, Point point, JsonAdapter<?> adapter, ...) {
        methodSignatureIncludesJsonWriterAndJsonAdapter -> {
          val qualifierAnnotations = parameterAnnotations[1].jsonAnnotations

          object : AdapterMethod(
            adaptersOffset = 2,
            type = parameterTypes[1],
            parameterCount = parameterTypes.size,
            annotations = qualifierAnnotations,
            adapter = adapter,
            method = method,
            nullable = true,
          ) {
            override fun toJson(moshi: Moshi, writer: JsonWriter, value: Any?) {
              invokeMethod(writer, value)
            }
          }
        }

        parameterTypes.size == 1 && returnType != Void.TYPE -> {
          // List<Integer> pointToJson(Point point) {
          val returnTypeAnnotations = method.jsonAnnotations
          val qualifierAnnotations = parameterAnnotations[0].jsonAnnotations
          val nullable = parameterAnnotations[0].hasNullable
          object : AdapterMethod(
            adaptersOffset = 1,
            type = parameterTypes[0],
            parameterCount = parameterTypes.size,
            annotations = qualifierAnnotations,
            adapter = adapter,
            method = method,
            nullable = nullable,
          ) {

            private lateinit var delegate: JsonAdapter<Any>

            override fun bind(moshi: Moshi, factory: JsonAdapter.Factory) {
              super.bind(moshi, factory)
              val shouldSkip = Types.equals(parameterTypes[0], returnType) &&
                qualifierAnnotations == returnTypeAnnotations
              delegate = if (shouldSkip) {
                moshi.nextAdapter(factory, returnType, returnTypeAnnotations)
              } else {
                moshi.adapter(returnType, returnTypeAnnotations)
              }
            }

            override fun toJson(moshi: Moshi, writer: JsonWriter, value: Any?) {
              delegate.toJson(writer, invokeMethod(value))
            }
          }
        }

        else -> {
          throw IllegalArgumentException(
            """
              Unexpected signature for $method.
              @ToJson method signatures may have one of the following structures:
                  <any access modifier> void toJson(JsonWriter writer, T value) throws <any>;
                  <any access modifier> void toJson(JsonWriter writer, T value, JsonAdapter<any> delegate, <any more delegates>) throws <any>;
                  <any access modifier> R toJson(T value) throws <any>;

            """.trimIndent(),
          )
        }
      }
    }

    /** Returns true if `parameterTypes[offset]` contains only JsonAdapters. */
    private fun parametersAreJsonAdapters(offset: Int, parameterTypes: Array<Type>): Boolean {
      for (i in offset until parameterTypes.size) {
        val parameterType = parameterTypes[i]
        if (parameterType !is ParameterizedType) return false
        if (parameterType.rawType != JsonAdapter::class.java) return false
      }
      return true
    }

    /**
     * Returns an object that calls a `method` method on `adapter` in service of
     * converting an object from JSON.
     */
    private fun fromAdapter(adapter: Any, method: Method): AdapterMethod {
      method.isAccessible = true
      val returnType = method.genericReturnType
      val returnTypeAnnotations = method.jsonAnnotations
      val parameterTypes = method.genericParameterTypes
      val parameterAnnotations = method.parameterAnnotations
      val methodSignatureIncludesJsonReaderAndJsonAdapter = parameterTypes.isNotEmpty() &&
        parameterTypes[0] == JsonReader::class.java &&
        returnType != Void.TYPE &&
        parametersAreJsonAdapters(1, parameterTypes)
      return when {
        methodSignatureIncludesJsonReaderAndJsonAdapter -> {
          // Point pointFromJson(JsonReader jsonReader) {
          // Point pointFromJson(JsonReader jsonReader, JsonAdapter<?> adapter, ...) {
          object : AdapterMethod(
            adaptersOffset = 1,
            type = returnType,
            parameterCount = parameterTypes.size,
            annotations = returnTypeAnnotations,
            adapter = adapter,
            method = method,
            nullable = true,
          ) {
            override fun fromJson(moshi: Moshi, reader: JsonReader) = invokeMethod(reader)
          }
        }

        parameterTypes.size == 1 && returnType != Void.TYPE -> {
          // Point pointFromJson(List<Integer> o) {
          val qualifierAnnotations = parameterAnnotations[0].jsonAnnotations
          val nullable = parameterAnnotations[0].hasNullable
          object : AdapterMethod(
            adaptersOffset = 1,
            type = returnType,
            parameterCount = parameterTypes.size,
            annotations = returnTypeAnnotations,
            adapter = adapter,
            method = method,
            nullable = nullable,
          ) {
            lateinit var delegate: JsonAdapter<Any>

            override fun bind(moshi: Moshi, factory: JsonAdapter.Factory) {
              super.bind(moshi, factory)
              delegate = if (Types.equals(parameterTypes[0], returnType) && qualifierAnnotations == returnTypeAnnotations) {
                moshi.nextAdapter(factory, parameterTypes[0], qualifierAnnotations)
              } else {
                moshi.adapter(parameterTypes[0], qualifierAnnotations)
              }
            }

            override fun fromJson(moshi: Moshi, reader: JsonReader): Any? {
              val intermediate = delegate.fromJson(reader)
              return invokeMethod(intermediate)
            }
          }
        }

        else -> {
          throw IllegalArgumentException(
            """
              Unexpected signature for $method.
              @FromJson method signatures may have one of the following structures:
                  <any access modifier> R fromJson(JsonReader jsonReader) throws <any>;
                  <any access modifier> R fromJson(JsonReader jsonReader, JsonAdapter<any> delegate, <any more delegates>) throws <any>;
                  <any access modifier> R fromJson(T value) throws <any>;

            """.trimIndent(),
          )
        }
      }
    }

    /** Returns the matching adapter method from the list. */
    private fun get(
      adapterMethods: List<AdapterMethod>,
      type: Type,
      annotations: Set<Annotation>,
    ): AdapterMethod? {
      for (adapterMethod in adapterMethods) {
        if (Types.equals(adapterMethod.type, type) && adapterMethod.annotations == annotations) {
          return adapterMethod
        }
      }
      return null
    }
  }

  internal abstract class AdapterMethod(
    private val adaptersOffset: Int,
    type: Type,
    parameterCount: Int,
    val annotations: Set<Annotation>,
    val adapter: Any,
    val method: Method,
    val nullable: Boolean,
  ) {
    val type = type.canonicalize()
    private val jsonAdapters: Array<JsonAdapter<*>?> = arrayOfNulls(parameterCount - adaptersOffset)

    open fun bind(moshi: Moshi, factory: JsonAdapter.Factory) {
      if (jsonAdapters.isNotEmpty()) {
        val parameterTypes = method.genericParameterTypes
        val parameterAnnotations = method.parameterAnnotations
        for (i in adaptersOffset until parameterTypes.size) {
          val type = (parameterTypes[i] as ParameterizedType).actualTypeArguments[0]
          val jsonAnnotations = parameterAnnotations[i].jsonAnnotations
          jsonAdapters[i - adaptersOffset] =
            if (Types.equals(this.type, type) && annotations == jsonAnnotations) {
              moshi.nextAdapter(factory, type, jsonAnnotations)
            } else {
              moshi.adapter<Any>(type, jsonAnnotations)
            }
        }
      }
    }

    open fun toJson(moshi: Moshi, writer: JsonWriter, value: Any?): Unit = throw AssertionError()

    open fun fromJson(moshi: Moshi, reader: JsonReader): Any? = throw AssertionError()

    /** Invoke the method with one fixed argument, plus any number of JSON adapter arguments. */
    protected fun invokeMethod(arg: Any?): Any? {
      val args = arrayOfNulls<Any>(1 + jsonAdapters.size)
      args[0] = arg
      jsonAdapters.copyInto(args, 1, 0, jsonAdapters.size)

      return try {
        method.invoke(adapter, *args)
      } catch (e: IllegalAccessException) {
        throw AssertionError()
      }
    }

    /** Invoke the method with two fixed arguments, plus any number of JSON adapter arguments. */
    protected fun invokeMethod(arg0: Any?, arg1: Any?): Any? {
      val args = arrayOfNulls<Any>(2 + jsonAdapters.size)
      args[0] = arg0
      args[1] = arg1
      jsonAdapters.copyInto(args, 2, 0, jsonAdapters.size)

      return try {
        method.invoke(adapter, *args)
      } catch (e: IllegalAccessException) {
        throw AssertionError()
      }
    }
  }
}
