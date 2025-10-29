/*
 * Copyright (C) 2025 Square, Inc.
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

import com.squareup.moshi.internal.DEFAULT_CONSTRUCTOR_MARKER
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.metadata.KmClass
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmClassifier.TypeAlias
import kotlin.metadata.KmClassifier.TypeParameter
import kotlin.metadata.KmConstructor
import kotlin.metadata.KmProperty
import kotlin.metadata.KmType
import kotlin.metadata.KmValueParameter
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isLocalClassName
import kotlin.metadata.isNullable
import kotlin.metadata.isSecondary
import kotlin.metadata.jvm.signature

private val DEFAULT_CONSTRUCTOR_SIGNATURE by lazy(LazyThreadSafetyMode.NONE) {
  DEFAULT_CONSTRUCTOR_MARKER!!.descriptor
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
  } else {
    null
  }

internal val KmType.canonicalName: String
  get() {
    return buildString {
      val classifierString =
        when (val cl = classifier) {
          is KmClassifier.Class -> createClassName(cl.name)
          is TypeAlias -> createClassName(cl.name)
          is TypeParameter -> arguments[cl.id].type?.canonicalName ?: "*"
        }
      append(classifierString)

      val args =
        arguments.joinToString(", ") {
          "${it.variance?.name.orEmpty()} ${it.type?.canonicalName ?: "*"}".trim()
        }

      if (args.isNotBlank()) {
        append('<')
        append(args)
        append('>')
      }
    }
  }

/**
 * Creates a canonical class name as represented in Metadata's [kotlin.metadata.ClassName], where
 * package names in this name are separated by '/' and class names are separated by '.'.
 *
 * Example ClassName that we want to canonicalize: `"java/util/Map.Entry"`.
 *
 * Local classes are prefixed with ".", but for Moshi's use case we don't deal with those.
 */
private fun createClassName(kotlinMetadataName: String): String {
  require(!kotlinMetadataName.isLocalClassName()) {
    "Local/anonymous classes are not supported: $kotlinMetadataName"
  }
  return kotlinMetadataName.replace("/", ".")
}

internal data class KtParameter(
  val km: KmValueParameter,
  val index: Int,
  val rawType: Class<*>,
  val annotations: List<Annotation>,
) {
  val name
    get() = km.name

  val declaresDefaultValue
    get() = km.declaresDefaultValue

  val isNullable
    get() = km.type.isNullable
}

internal data class KtConstructor(
  val type: Class<*>,
  val km: KmConstructor,
  val jvm: Constructor<*>,
  val parameters: List<KtParameter>,
  val isDefault: Boolean,
) {
  init {
    jvm.isAccessible = true
  }

  fun <R> callBy(argumentsMap: Map<KtParameter, Any?>): R {
    val arguments = ArrayList<Any?>(parameters.size)
    var mask = 0
    val masks = ArrayList<Int>(1)
    var index = 0

    for (parameter in parameters) {
      if (index != 0 && index % Integer.SIZE == 0) {
        masks += mask
        mask = 0
        index = 0
      }

      val possibleArg = argumentsMap[parameter]
      val usePossibleArg = possibleArg != null || parameter in argumentsMap
      when {
        usePossibleArg -> {
          arguments += possibleArg
        }
        parameter.declaresDefaultValue -> {
          arguments += defaultPrimitiveValue(parameter.rawType)
          mask = mask or (1 shl (index % Integer.SIZE))
        }
        else -> {
          throw IllegalArgumentException(
            "No argument provided for a required parameter: $parameter",
          )
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
    arguments += null

    @Suppress("UNCHECKED_CAST")
    return jvm.newInstance(*arguments.toTypedArray()) as R
  }

  companion object {
    fun primary(rawType: Class<*>, kmClass: KmClass): KtConstructor? {
      val kmConstructor = kmClass.constructors.find { !it.isSecondary } ?: return null
      val kmConstructorSignature = kmConstructor.signature?.toString() ?: return null
      val constructorsBySignature =
        rawType.declaredConstructors.associateBy { it.jvmMethodSignature }
      val jvmConstructor = constructorsBySignature[kmConstructorSignature] ?: return null
      val parameterAnnotations = jvmConstructor.parameterAnnotations
      val parameterTypes = jvmConstructor.parameterTypes
      val parameters =
        kmConstructor.valueParameters.withIndex().map { (index, kmParam) ->
          KtParameter(kmParam, index, parameterTypes[index], parameterAnnotations[index].toList())
        }

      val anyOptional = parameters.any { it.declaresDefaultValue }
      val actualConstructor =
        if (anyOptional) {
          val prefix = jvmConstructor.jvmMethodSignature.removeSuffix(")V")
          val parameterCount = jvmConstructor.parameterTypes.size
          val maskParamsToAdd = (parameterCount + 31) / 32
          val defaultConstructorSignature = buildString {
            append(prefix)
            repeat(maskParamsToAdd) { append("I") }
            append(DEFAULT_CONSTRUCTOR_SIGNATURE)
            append(")V")
          }
          constructorsBySignature[defaultConstructorSignature] ?: return null
        } else {
          jvmConstructor
        }

      return KtConstructor(rawType, kmConstructor, actualConstructor, parameters, anyOptional)
    }
  }
}

internal data class KtProperty(
  val km: KmProperty,
  val jvmField: Field?,
  val jvmGetter: Method?,
  val jvmSetter: Method?,
  val jvmAnnotationsMethod: Method?,
  val parameter: KtParameter?,
) {
  init {
    jvmField?.isAccessible = true
    jvmGetter?.isAccessible = true
    jvmSetter?.isAccessible = true
  }

  val name
    get() = km.name

  val javaType =
    jvmField?.genericType
      ?: jvmGetter?.genericReturnType
      ?: jvmSetter?.genericReturnType
      ?: error(
        "No type information available for property '${km.name}' with type '${km.returnType.canonicalName}'.",
      )

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
