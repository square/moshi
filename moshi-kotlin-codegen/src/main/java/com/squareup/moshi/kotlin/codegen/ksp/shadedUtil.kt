/*
 * Copyright 2020 Google LLC
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.kotlin.codegen.ksp

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
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

internal fun <T : Annotation> KSAnnotated.getAnnotationsByType(annotationKClass: KClass<T>): Sequence<T> {
  return this.annotations.filter {
    it.shortName.getShortName() == annotationKClass.simpleName &&
      it.annotationType.resolve().declaration
        .qualifiedName?.asString() == annotationKClass.qualifiedName
  }.map { it.toAnnotation(annotationKClass.java) }
}

internal fun <T : Annotation> KSAnnotated.isAnnotationPresent(annotationKClass: KClass<T>): Boolean = getAnnotationsByType(annotationKClass).firstOrNull() != null

@Suppress("UNCHECKED_CAST")
private fun <T : Annotation> KSAnnotation.toAnnotation(annotationClass: Class<T>): T {
  return Proxy.newProxyInstance(
    annotationClass.classLoader,
    arrayOf(annotationClass),
    createInvocationHandler(annotationClass),
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
      val argument = arguments.first { it.name?.asString() == method.name }
      when (val result = argument.value ?: method.defaultValue) {
        is Proxy -> result
        is List<*> -> {
          val value = { result.asArray(method, clazz) }
          cache.getOrPut(Pair(method.returnType, result), value)
        }
        else -> {
          when {
            // Workaround for java annotation value array type
            // https://github.com/google/ksp/issues/1329
            method.returnType.isArray -> {
              if (result !is Array<*>) {
                val value = { result.asArray(method, clazz) }
                cache.getOrPut(Pair(method.returnType, value), value)
              } else {
                throw IllegalStateException("unhandled value type, please file a bug at https://github.com/google/ksp/issues/new")
              }
            }
            method.returnType.isEnum -> {
              val value = { result.asEnum(method.returnType) }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.isAnnotation -> {
              val value = { (result as KSAnnotation).asAnnotation(method.returnType) }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "java.lang.Class" -> {
              cache.getOrPut(Pair(method.returnType, result)) {
                when (result) {
                  is KSType -> result.asClass(clazz)
                  // Handles com.intellij.psi.impl.source.PsiImmediateClassType using reflection
                  // since api doesn't contain a reference to this
                  else -> Class.forName(
                    result.javaClass.methods
                      .first { it.name == "getCanonicalText" }
                      .invoke(result, false) as String,
                  )
                }
              }
            }
            method.returnType.name == "byte" -> {
              val value = { result.asByte() }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "short" -> {
              val value = { result.asShort() }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "long" -> {
              val value = { result.asLong() }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "float" -> {
              val value = { result.asFloat() }
              cache.getOrPut(Pair(method.returnType, result), value)
            }
            method.returnType.name == "double" -> {
              val value = { result.asDouble() }
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
    this.javaClass.classLoader,
    arrayOf(annotationInterface),
    this.createInvocationHandler(annotationInterface),
  ) as Proxy
}

@Suppress("UNCHECKED_CAST")
private fun List<*>.asArray(method: Method, proxyClass: Class<*>) = when (method.returnType.componentType.name) {
  "boolean" -> (this as List<Boolean>).toBooleanArray()

  "byte" -> (this as List<Byte>).toByteArray()

  "short" -> (this as List<Short>).toShortArray()

  "char" -> (this as List<Char>).toCharArray()

  "double" -> (this as List<Double>).toDoubleArray()

  "float" -> (this as List<Float>).toFloatArray()

  "int" -> (this as List<Int>).toIntArray()

  "long" -> (this as List<Long>).toLongArray()

  "java.lang.Class" -> (this as List<KSType>).asClasses(proxyClass).toTypedArray()

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
    this.size,
  ) as Array<Any?>
  for (r in 0 until this.size) {
    array[r] = this[r]?.let { valueProvider.invoke(it) }
  }
  return array
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.asEnum(returnType: Class<T>): T = returnType.getDeclaredMethod("valueOf", String::class.java)
  .invoke(
    null,

    if (this is KSType) {
      this.declaration.simpleName.getShortName()} else if (this is KSClassDeclaration) {
        this.simpleName.getShortName()
    } else {
      this.toString()
    },
  ) as T

private fun Any.asByte(): Byte = if (this is Int) this.toByte() else this as Byte

private fun Any.asShort(): Short = if (this is Int) this.toShort() else this as Short

private fun Any.asLong(): Long = if (this is Int) this.toLong() else this as Long

private fun Any.asFloat(): Float = if (this is Int) this.toFloat() else this as Float

private fun Any.asDouble(): Double = if (this is Int) this.toDouble() else this as Double

// for Class/KClass member
internal class KSTypeNotPresentException(val ksType: KSType, cause: Throwable) : RuntimeException(cause)

// for Class[]/Array<KClass<*>> member.
internal class KSTypesNotPresentException(val ksTypes: List<KSType>, cause: Throwable) : RuntimeException(cause)

private fun KSType.asClass(proxyClass: Class<*>) = try {
  Class.forName(this.declaration.toJavaClassName(), true, proxyClass.classLoader)
} catch (e: Exception) {
  throw KSTypeNotPresentException(this, e)
}

private fun List<KSType>.asClasses(proxyClass: Class<*>) = try {
  this.map { type -> type.asClass(proxyClass) }
} catch (e: Exception) {
  throw KSTypesNotPresentException(this, e)
}

private fun Any.asArray(method: Method, proxyClass: Class<*>) = listOf(this).asArray(method, proxyClass)

private fun KSDeclaration.toJavaClassName(): String {
  val nameDelimiter = '.'
  val packageNameString = packageName.asString()
  val qualifiedNameString = qualifiedName!!.asString()
  val simpleNames = qualifiedNameString
    .removePrefix("${packageNameString}$nameDelimiter")
    .split(nameDelimiter)

  return if (simpleNames.size > 1) {
    buildString {
      append(packageNameString)
      append(nameDelimiter)

      simpleNames.forEachIndexed { index, s ->
        if (index > 0) {
          append('$')
        }
        append(s)
      }
    }
  } else {
    qualifiedNameString
  }
}
