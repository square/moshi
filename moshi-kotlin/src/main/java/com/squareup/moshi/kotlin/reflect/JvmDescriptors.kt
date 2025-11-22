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

import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import kotlin.metadata.jvm.JvmMethodSignature

private val PRIMITIVE_CLASS_TO_SYMBOL =
  buildMap(capacity = 9) {
    put(Byte::class.javaPrimitiveType, 'B')
    put(Char::class.javaPrimitiveType, 'C')
    put(Double::class.javaPrimitiveType, 'D')
    put(Float::class.javaPrimitiveType, 'F')
    put(Int::class.javaPrimitiveType, 'I')
    put(Long::class.javaPrimitiveType, 'J')
    put(Short::class.javaPrimitiveType, 'S')
    put(Boolean::class.javaPrimitiveType, 'Z')
    put(Void::class.javaPrimitiveType, 'V')
  }

internal val Class<*>.descriptor: String
  get() {
    return when {
      isPrimitive ->
        PRIMITIVE_CLASS_TO_SYMBOL[this]?.toString()
          ?: throw RuntimeException("Unrecognized primitive $this")

      isArray -> "[${componentType.descriptor}"
      else -> "L$name;".replace('.', '/')
    }
  }

private class TypeSignatureReader(var index: Int) {
  fun readType(desc: String): Class<*> {
    return when (val c = desc[index]) {
      '[' -> {
        // It's an array
        index++
        val start = index
        this.readType(desc)
        // We ignore the read class because we just want to reuse the descriptor name in the string
        // since that's how array component lookups work
        val descriptorName = buildString {
          for (i in start until index) {
            var subc = desc[i]
            if (subc == '/') {
              subc = '.'
            }
            this.append(subc)
          }
        }
        Class.forName("[$descriptorName")
      }

      'B' -> {
        index++
        Byte::class.javaPrimitiveType!!
      }

      'C' -> {
        index++
        Char::class.javaPrimitiveType!!
      }

      'D' -> {
        index++
        Double::class.javaPrimitiveType!!
      }

      'F' -> {
        index++
        Float::class.javaPrimitiveType!!
      }

      'I' -> {
        index++
        Int::class.javaPrimitiveType!!
      }

      'J' -> {
        index++
        Long::class.javaPrimitiveType!!
      }

      'S' -> {
        index++
        Short::class.javaPrimitiveType!!
      }

      'Z' -> {
        index++
        Boolean::class.javaPrimitiveType!!
      }

      'V' -> {
        index++
        Void::class.javaPrimitiveType!!
      }

      'L' -> {
        // It's a ClassName, read it until ';'
        index++
        var c2 = desc[index]
        val className = buildString {
          while (c2 != ';') {
            if (c2 == '/') {
              // convert package splits to '.'
              c2 = '.'
            }
            this.append(c2)
            index++
            c2 = desc[index]
          }
        }

        index++ // Read off the ';'
        Class.forName(className)
      }

      else -> error("Unknown character $c")
    }
  }
}

internal fun JvmMethodSignature.decodeParameterTypes(): List<Class<*>> {
  val classList = mutableListOf<Class<*>>()
  val typeSignatureReader = TypeSignatureReader(0)
  while (typeSignatureReader.index < descriptor.length) {
    when (descriptor[typeSignatureReader.index]) {
      '(' -> {
        typeSignatureReader.index++
        continue
      }

      ')' -> break
    }
    classList += typeSignatureReader.readType(descriptor)
  }
  return classList
}

/**
 * Returns the JVM signature in the form "<init>$MethodDescriptor", for example:
 * `"<init>(Ljava/lang/Object;)V")`.
 *
 * Useful for comparing with [JvmMethodSignature].
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3">JVM specification, section 4.3</a>
 */
internal val Executable.jvmMethodSignature: String
  get() = buildString {
    when (this@jvmMethodSignature) {
      is Constructor<*> -> append("<init>")
      is Method -> append(name)
    }
    parameterTypes.joinTo(buffer = this, separator = "", prefix = "(", postfix = ")") { it.descriptor }
    when (this@jvmMethodSignature) {
      is Constructor<*> -> append("V")
      is Method -> append(returnType.descriptor)
    }
  }
