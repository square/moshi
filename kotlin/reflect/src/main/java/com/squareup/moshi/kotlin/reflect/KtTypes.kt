/*
 * Copyright (C) 2020 Square, Inc.
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

import kotlinx.metadata.Flag
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmClassifier.TypeAlias
import kotlinx.metadata.KmClassifier.TypeParameter
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.isLocal
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type

internal val KmType.isNullable: Boolean get() = Flag.Type.IS_NULLABLE(flags)

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

internal val KmType.canonicalName: String
  get() {
    return buildString {
      val classifierString = when (val cl = classifier) {
        is KmClassifier.Class -> createClassName(cl.name)
        is TypeAlias -> createClassName(cl.name)
        is TypeParameter -> arguments[cl.id].type?.canonicalName ?: "TypeVar(${cl.id})"
      }
      append(classifierString)

      val args = arguments.joinToString(", ") {
        "${it.variance?.name} ${it.type?.canonicalName ?: "*"}"
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
 * Example ClassName that we want to canonicalize: `"org/foo/bar/Baz.Nested"`.
 *
 * Local classes are prefixed with ".", but for Moshi's use case we don't deal with those.
 */
private fun createClassName(kotlinMetadataName: String): String {
  require(!kotlinMetadataName.isLocal) {
    "Local/anonymous classes are not supported!"
  }
  return kotlinMetadataName.replace("/", ".")
}

internal data class KtParameter(
  val km: KmValueParameter,
  val index: Int,
  val rawType: Class<*>,
  val annotations: List<Annotation>
) {
  val name get() = km.name
  val declaresDefaultValue get() = Flag.ValueParameter.DECLARES_DEFAULT_VALUE(km.flags)
  val isNullable get() = km.type!!.isNullable
}

internal data class KtConstructor(
  val type: Class<*>,
  val km: KmConstructor,
  val jvm: Constructor<*>,
  val parameters: List<KtParameter>,
  val isDefault: Boolean
) {
  init {
    jvm.isAccessible = true
  }

  fun <R> callBy(args: Map<KtParameter, Any?>): R {
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
        parameter.declaresDefaultValue -> {
          arguments += defaultPrimitiveValue(parameter.rawType)
          mask = mask or (1 shl (index % Integer.SIZE))
        }
        else -> {
          throw IllegalArgumentException(
            "No argument provided for a required parameter: $parameter"
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
    arguments.add(null)

    @Suppress("UNCHECKED_CAST")
    return jvm.newInstance(*arguments.toTypedArray()) as R
  }
}

internal data class KtProperty(
  val km: KmProperty,
  val jvmField: Field?,
  val jvmGetter: Method?,
  val jvmSetter: Method?,
  val jvmAnnotationsMethod: Method?,
  val parameter: KtParameter?
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
    ?: error(
      "No type information available for property '${km.name}' with type '${km.returnType.canonicalName}'."
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
