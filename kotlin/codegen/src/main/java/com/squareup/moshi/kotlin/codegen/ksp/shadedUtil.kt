/*
 * Copyright (C) 2020 Square, Inc.
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
package com.squareup.moshi.kotlin.codegen.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/*
 * Copied experimental utilities from KSP.
 */

/**
 * Find a class in the compilation classpath for the given name.
 *
 * @param name fully qualified name of the class to be loaded; using '.' as separator.
 * @return a KSClassDeclaration, or null if not found.
 */
internal fun Resolver.getClassDeclarationByName(name: String): KSClassDeclaration? =
  getClassDeclarationByName(getKSNameFromString(name))

internal fun <T : Annotation> KSAnnotated.getAnnotationsByType(annotationKClass: KClass<T>): Sequence<T> {
  return this.annotations.filter {
    it.shortName.getShortName() == annotationKClass.simpleName && it.annotationType.resolve().declaration
      .qualifiedName?.asString() == annotationKClass.qualifiedName
  }.map { it.toAnnotation(annotationKClass.java) }
}

internal fun <T : Annotation> KSAnnotated.isAnnotationPresent(annotationKClass: KClass<T>): Boolean =
  getAnnotationsByType(annotationKClass).firstOrNull() != null

@Suppress("UNCHECKED_CAST")
private fun <T : Annotation> KSAnnotation.toAnnotation(annotationClass: Class<T>): T {
  return Proxy.newProxyInstance(
    annotationClass.classLoader,
    arrayOf(annotationClass),
    createInvocationHandler(annotationClass)
  ) as T
}

@Suppress("TooGenericExceptionCaught")
private fun KSAnnotation.createInvocationHandler(clazz: Class<*>): InvocationHandler {
  val cache = ConcurrentHashMap<Pair<Class<*>, Any>, Any>(arguments.size)
  return InvocationHandler { proxy, method, _ ->
    if (method.name == "toString" && arguments.none { it.name?.asString() == "toString" }) {
      clazz.canonicalName +
        arguments.map { argument: KSValueArgument ->
          // handles default values for enums otherwise returns null
          val methodName = argument.name?.asString()
          val value = proxy.javaClass.methods.find { m -> m.name == methodName }?.invoke(proxy)
          "$methodName=$value"
        }.toList()
    } else {
      val argument = try {
        arguments.first { it.name?.asString() == method.name }
      } catch (e: NullPointerException) {
        throw IllegalArgumentException("This is a bug using the default KClass for an annotation", e)
      }
      when (val result = argument.value ?: method.defaultValue) {
        is Proxy -> result
        is List<*> -> {
          val value = { result.asArray(method) }
          cache.getOrPut(Pair(method.returnType, result), value)
        }
        else -> {
          when {
            method.returnType.isEnum -> {
              val value = { result.asEnum(method.returnType) }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.isAnnotation -> {
              val value = { (result as KSAnnotation).asAnnotation(method.returnType) }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "java.lang.Class" -> {
              val value = { (result as KSType).asClass() }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "byte" -> {
              val value = { result.asByte() }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "short" -> {
              val value = { result.asShort() }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            else -> result // original value
          }
        }
      }
    }
  }
}

@Suppress("UNCHECKED_CAST")
private fun KSAnnotation.asAnnotation(
  annotationInterface: Class<*>,
): Any {
  return Proxy.newProxyInstance(
    this.javaClass.classLoader, arrayOf(annotationInterface),
    this.createInvocationHandler(annotationInterface)
  ) as Proxy
}

@Suppress("UNCHECKED_CAST")
private fun List<*>.asArray(method: Method) =
  when (method.returnType.componentType.name) {
    "boolean" -> (this as List<Boolean>).toBooleanArray()
    "byte" -> (this as List<Byte>).toByteArray()
    "short" -> (this as List<Short>).toShortArray()
    "char" -> (this as List<Char>).toCharArray()
    "double" -> (this as List<Double>).toDoubleArray()
    "float" -> (this as List<Float>).toFloatArray()
    "int" -> (this as List<Int>).toIntArray()
    "long" -> (this as List<Long>).toLongArray()
    "java.lang.Class" -> (this as List<KSType>).map {
      Class.forName(it.declaration.qualifiedName!!.asString())
    }.toTypedArray()
    "java.lang.String" -> (this as List<String>).toTypedArray()
    else -> { // arrays of enums or annotations
      when {
        method.returnType.componentType.isEnum -> {
          this.toArray(method) { result -> result.asEnum(method.returnType.componentType) }
        }
        method.returnType.componentType.isAnnotation -> {
          this.toArray(method) { result ->
            (result as KSAnnotation).asAnnotation(method.returnType.componentType)
          }
        }
        else -> throw IllegalStateException("Unable to process type ${method.returnType.componentType.name}")
      }
    }
  }

@Suppress("UNCHECKED_CAST")
private fun List<*>.toArray(method: Method, valueProvider: (Any) -> Any): Array<Any?> {
  val array: Array<Any?> = java.lang.reflect.Array.newInstance(
    method.returnType.componentType,
    this.size
  ) as Array<Any?>
  for (r in 0 until this.size) {
    array[r] = this[r]?.let { valueProvider.invoke(it) }
  }
  return array
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.asEnum(returnType: Class<T>): T =
  returnType.getDeclaredMethod("valueOf", String::class.java)
    .invoke(
      null,
      // Change from upstream KSP - https://github.com/google/ksp/pull/685
      if (this is KSType) {
        this.declaration.simpleName.getShortName()
      } else {
        this.toString()
      }
    ) as T

private fun Any.asByte(): Byte = if (this is Int) this.toByte() else this as Byte

private fun Any.asShort(): Short = if (this is Int) this.toShort() else this as Short

private fun KSType.asClass() = Class.forName(this.declaration.qualifiedName!!.asString())
