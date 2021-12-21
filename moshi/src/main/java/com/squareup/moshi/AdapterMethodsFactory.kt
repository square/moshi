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

import kotlin.Throws
import java.lang.reflect.InvocationTargetException
import java.lang.IllegalAccessException
import com.squareup.moshi.internal.Util
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import okio.IOException

internal class AdapterMethodsFactory(
  private val toAdapters: List<AdapterMethod>,
  private val fromAdapters: List<AdapterMethod>
) : JsonAdapter.Factory {
  override fun create(
    type: Type, annotations: Set<Annotation>, moshi: Moshi
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
          "No "
            + missingAnnotation
            + " adapter for "
            + Util.typeAnnotatedWithAnnotations(type, annotations),
          e
        )
      }
    } else {
      null
    }

    toAdapter?.bind(moshi, this)
    fromAdapter?.bind(moshi, this)

    return object : JsonAdapter<Any>() {
      override fun toJson(writer: JsonWriter, value: Any?) {
        if (toAdapter == null) {
          delegate!!.toJson(writer, value)
        } else if (!toAdapter.nullable && value == null) {
          writer.nullValue()
        } else {
          try {
            toAdapter.toJson(moshi, writer, value)
          } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is IOException) throw cause
            throw JsonDataException("$cause at ${writer.getPath()}", cause)
          }
        }
      }

      @Throws(IOException::class)
      override fun fromJson(reader: JsonReader): Any? {
        return if (fromAdapter == null) {
          delegate!!.fromJson(reader)
        } else if (!fromAdapter.nullable && reader.peek() == JsonReader.Token.NULL) {
          reader.nextNull<Any>()
          null
        } else {
          try {
            fromAdapter.fromJson(moshi, reader)
          } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is IOException) throw cause
            throw JsonDataException("$cause at ${reader.getPath()}", cause)
          }
        }
      }

      override fun toString(): String {
        return "JsonAdapter$annotations($type)"
      }
    }
  }

  public companion object {
    @JvmStatic
    public fun get(adapter: Any): AdapterMethodsFactory {
      val toAdapters = mutableListOf<AdapterMethod>()
      val fromAdapters = mutableListOf<AdapterMethod>()

      var c: Class<*> = adapter.javaClass
      while (c != Any::class.java) {
        for (m in c.declaredMethods) {
          if (m.isAnnotationPresent(ToJson::class.java)) {
            val toAdapter = toAdapter(adapter, m)
            val conflicting = get(toAdapters, toAdapter.type, toAdapter.annotations)
            if (conflicting != null) {
              throw IllegalArgumentException("""Conflicting @ToJson methods:
    ${conflicting.method}
    ${toAdapter.method}""")
            }
            toAdapters.add(toAdapter)
          }
          if (m.isAnnotationPresent(FromJson::class.java)) {
            val fromAdapter = fromAdapter(adapter, m)
            val conflicting = get(fromAdapters, fromAdapter.type, fromAdapter.annotations)
            if (conflicting != null) {
              throw IllegalArgumentException("""Conflicting @FromJson methods:
    ${conflicting.method}
    ${fromAdapter.method}""")
            }
            fromAdapters.add(fromAdapter)
          }
        }
        c = c.superclass
      }
      require(!(toAdapters.isEmpty() && fromAdapters.isEmpty())) { "Expected at least one @ToJson or @FromJson method on ${adapter.javaClass.name}" }
      return AdapterMethodsFactory(toAdapters, fromAdapters)
    }

    /**
     * Returns an object that calls a `method` method on `adapter` in service of
     * converting an object to JSON.
     */
    fun toAdapter(adapter: Any, method: Method): AdapterMethod {
      method.isAccessible = true
      val returnType = method.genericReturnType
      val parameterTypes = method.genericParameterTypes
      val parameterAnnotations = method.parameterAnnotations
      return if (parameterTypes.size >= 2
        && parameterTypes[0] == JsonWriter::class.java
        && returnType == Void.TYPE
        && parametersAreJsonAdapters(2, parameterTypes)
      ) {
        // void pointToJson(JsonWriter jsonWriter, Point point) {
        // void pointToJson(JsonWriter jsonWriter, Point point, JsonAdapter<?> adapter, ...) {
        val qualifierAnnotations = Util.jsonAnnotations(parameterAnnotations[1])
        object : AdapterMethod(
          parameterTypes[1],
          qualifierAnnotations,
          adapter,
          method,
          parameterTypes.size,
          2,
          true
        ) {
          @Throws(IOException::class, InvocationTargetException::class)
          override fun toJson(moshi: Moshi, writer: JsonWriter, value: Any?) {
            invoke(writer, value)
          }
        }
      } else if (parameterTypes.size == 1 && returnType != Void.TYPE) {
        // List<Integer> pointToJson(Point point) {
        val returnTypeAnnotations = Util.jsonAnnotations(method)
        val qualifierAnnotations = Util.jsonAnnotations(parameterAnnotations[0])
        val nullable = Util.hasNullable(parameterAnnotations[0])
        object : AdapterMethod(
          parameterTypes[0],
          qualifierAnnotations,
          adapter,
          method,
          parameterTypes.size,
          1,
          nullable
        ) {
          private var delegate: JsonAdapter<Any>? = null
          override fun bind(moshi: Moshi, factory: JsonAdapter.Factory) {
            super.bind(moshi, factory)
            delegate = if (Types.equals(parameterTypes[0], returnType)
              && qualifierAnnotations == returnTypeAnnotations)
              moshi.nextAdapter(factory, returnType, returnTypeAnnotations)
            else moshi.adapter(returnType, returnTypeAnnotations)
          }

          @Throws(IOException::class, InvocationTargetException::class)
          override fun toJson(moshi: Moshi, writer: JsonWriter, value: Any?) {
            val intermediate = invoke(value)
            delegate!!.toJson(writer, intermediate)
          }
        }
      } else {
        throw IllegalArgumentException(
          """Unexpected signature for $method.
@ToJson method signatures may have one of the following structures:
    <any access modifier> void toJson(JsonWriter writer, T value) throws <any>;
    <any access modifier> void toJson(JsonWriter writer, T value, JsonAdapter<any> delegate, <any more delegates>) throws <any>;
    <any access modifier> R toJson(T value) throws <any>;
"""
        )
      }
    }

    /** Returns true if `parameterTypes[offset]` contains only JsonAdapters.  */
    private fun parametersAreJsonAdapters(offset: Int, parameterTypes: Array<Type>): Boolean {
      var i = offset
      val length = parameterTypes.size
      while (i < length) {
        if (parameterTypes[i] !is ParameterizedType) return false
        if ((parameterTypes[i] as ParameterizedType).rawType != JsonAdapter::class.java) return false
        i++
      }
      return true
    }

    /**
     * Returns an object that calls a `method` method on `adapter` in service of
     * converting an object from JSON.
     */
    fun fromAdapter(adapter: Any, method: Method): AdapterMethod {
      method.isAccessible = true
      val returnType = method.genericReturnType
      val returnTypeAnnotations = Util.jsonAnnotations(method)
      val parameterTypes = method.genericParameterTypes
      val parameterAnnotations = method.parameterAnnotations
      return if (parameterTypes.isNotEmpty()
        && parameterTypes[0] == JsonReader::class.java
        && returnType != Void.TYPE
        && parametersAreJsonAdapters(  1, parameterTypes)
      ) {
        // Point pointFromJson(JsonReader jsonReader) {
        // Point pointFromJson(JsonReader jsonReader, JsonAdapter<?> adapter, ...) {
        object : AdapterMethod(
          returnType, returnTypeAnnotations, adapter, method, parameterTypes.size, 1, true) {
          @Throws(IOException::class, InvocationTargetException::class)
          override fun fromJson(moshi: Moshi, reader: JsonReader): Any? {
            return invoke(reader)
          }
        }
      } else if (parameterTypes.size == 1 && returnType != Void.TYPE) {
        // Point pointFromJson(List<Integer> o) {
        val qualifierAnnotations = Util.jsonAnnotations(parameterAnnotations[0])
        val nullable = Util.hasNullable(parameterAnnotations[0])
        object : AdapterMethod(
          returnType, returnTypeAnnotations, adapter, method, parameterTypes.size, 1, nullable) {
          var delegate: JsonAdapter<Any>? = null

          override fun bind(moshi: Moshi, factory: JsonAdapter.Factory) {
            super.bind(moshi, factory)
            delegate = if (Types.equals(parameterTypes[0], returnType)
              && qualifierAnnotations == returnTypeAnnotations
            ) moshi.nextAdapter(factory, parameterTypes[0], qualifierAnnotations)
            else moshi.adapter(parameterTypes[0], qualifierAnnotations)
          }

          @Throws(IOException::class, InvocationTargetException::class)
          override fun fromJson(moshi: Moshi, reader: JsonReader): Any? {
            val intermediate = delegate!!.fromJson(reader)
            return invoke(intermediate)
          }
        }
      } else {
        throw IllegalArgumentException(
          """Unexpected signature for $method.
@FromJson method signatures may have one of the following structures:
    <any access modifier> R fromJson(JsonReader jsonReader) throws <any>;
    <any access modifier> R fromJson(JsonReader jsonReader, JsonAdapter<any> delegate, <any more delegates>) throws <any>;
    <any access modifier> R fromJson(T value) throws <any>;
"""
        )
      }
    }

    /** Returns the matching adapter method from the list.  */
    private fun get(
      adapterMethods: List<AdapterMethod>, type: Type, annotations: Set<Annotation>
    ): AdapterMethod? {
      var i = 0
      val size = adapterMethods.size
      while (i < size) {
        val adapterMethod = adapterMethods[i]
        if (Types.equals(adapterMethod.type, type) && adapterMethod.annotations == annotations) {
          return adapterMethod
        }
        i++
      }
      return null
    }
  }


  internal abstract class AdapterMethod(
    type: Type,
    val annotations: Set<Annotation>,
    val adapter: Any,
    val method: Method,
    parameterCount: Int,
    adaptersOffset: Int,
    val nullable: Boolean
  ) {
    val type: Type
    val adaptersOffset: Int
    val jsonAdapters: Array<JsonAdapter<*>?>

    init {
      this.type = Util.canonicalize(type)
      this.adaptersOffset = adaptersOffset
      jsonAdapters = arrayOfNulls(parameterCount - adaptersOffset)
    }

    open fun bind(moshi: Moshi, factory: JsonAdapter.Factory) {
      if (jsonAdapters.isNotEmpty()) {
        val parameterTypes = method.genericParameterTypes
        val parameterAnnotations = method.parameterAnnotations
        var i = adaptersOffset
        val size = parameterTypes.size
        while (i < size) {
          val type = (parameterTypes[i] as ParameterizedType).actualTypeArguments[0]
          val jsonAnnotations = Util.jsonAnnotations(parameterAnnotations[i])
          jsonAdapters[i - adaptersOffset] =
            if (Types.equals(this.type, type) && annotations == jsonAnnotations) {
              moshi.nextAdapter(factory, type, jsonAnnotations
              )
            } else {
              moshi.adapter<Any>(type, jsonAnnotations)
            }
          i++
        }
      }
    }

    @Throws(IOException::class, InvocationTargetException::class)
    open fun toJson(moshi: Moshi, writer: JsonWriter, value: Any?) {
      throw AssertionError()
    }

    @Throws(IOException::class, InvocationTargetException::class)
    open fun fromJson(moshi: Moshi, reader: JsonReader): Any? {
      throw AssertionError()
    }

    /** Invoke the method with one fixed argument, plus any number of JSON adapter arguments.  */
    @Throws(InvocationTargetException::class)
    protected operator fun invoke(a1: Any?): Any? {
      val args = arrayOfNulls<Any>(1 + jsonAdapters.size)
      args[0] = a1
      System.arraycopy(jsonAdapters, 0, args, 1, jsonAdapters.size)

      return try {
        method.invoke(adapter, *args)
      } catch (e: IllegalAccessException) {
        throw AssertionError()
      }
    }

    /** Invoke the method with two fixed arguments, plus any number of JSON adapter arguments.  */
    @Throws(InvocationTargetException::class)
    protected operator fun invoke(a1: Any?, a2: Any?): Any? {
      val args = arrayOfNulls<Any>(2 + jsonAdapters.size)
      args[0] = a1
      args[1] = a2
      System.arraycopy(jsonAdapters, 0, args, 2, jsonAdapters.size)

      return try {
        method.invoke(adapter, *args)
      } catch (e: IllegalAccessException) {
        throw AssertionError()
      }
    }
  }
}
