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
package com.squareup.moshi.kotlin.reflect

import kotlinx.metadata.jvm.JvmMethodSignature
import java.lang.reflect.Constructor

private val PRIMITIVE_CLASS_TO_DESC = mapOf(
  Byte::class.javaPrimitiveType to 'B',
  Char::class.javaPrimitiveType to 'C',
  Double::class.javaPrimitiveType to 'D',
  Float::class.javaPrimitiveType to 'F',
  Int::class.javaPrimitiveType to 'I',
  Long::class.javaPrimitiveType to 'J',
  Short::class.javaPrimitiveType to 'S',
  Boolean::class.javaPrimitiveType to 'Z',
  Void::class.javaPrimitiveType to 'V'
)

internal val Class<*>.descriptor: String
  get() {
    return when {
      isPrimitive -> PRIMITIVE_CLASS_TO_DESC[this]?.toString() ?: throw RuntimeException("Unrecognized primitive $this")
      isArray -> "[${componentType.descriptor}"
      else -> "L$name;".replace('.', '/')
    }
  }

private class Counter(var index: Int)

private fun readType(counter: Counter, desc: String): Class<*> {
  return when (val c = desc[counter.index]) {
    '[' -> {
      // It's an array
      counter.index++
      val start = counter.index
      readType(counter, desc)
      // We ignore the read class because we just want to reuse the descriptor name in the string
      // since that's how array component lookups work
      val descriptorName = buildString {
        for (i in start until counter.index) {
          var subc = desc[i]
          if (subc == '/') {
            subc = '.'
          }
          append(subc)
        }
      }
      Class.forName("[$descriptorName")
    }
    'B' -> {
      counter.index++
      Byte::class.javaPrimitiveType!!
    }
    'C' -> {
      counter.index++
      Char::class.javaPrimitiveType!!
    }
    'D' -> {
      counter.index++
      Double::class.javaPrimitiveType!!
    }
    'F' -> {
      counter.index++
      Float::class.javaPrimitiveType!!
    }
    'I' -> {
      counter.index++
      Int::class.javaPrimitiveType!!
    }
    'J' -> {
      counter.index++
      Long::class.javaPrimitiveType!!
    }
    'S' -> {
      counter.index++
      Short::class.javaPrimitiveType!!
    }
    'Z' -> {
      counter.index++
      Boolean::class.javaPrimitiveType!!
    }
    'V' -> {
      counter.index++
      Void::class.javaPrimitiveType!!
    }
    'L' -> {
      // It's a ClassName, read it until ';'
      counter.index++
      var c2 = desc[counter.index]
      val className = buildString {
        while (c2 != ';') {
          if (c2 == '/') {
            // convert package splits to '.'
            c2 = '.'
          }
          append(c2)
          counter.index++
          c2 = desc[counter.index]
        }
      }

      counter.index++ // Read off the ';'
      Class.forName(className)
    }
    else -> error("Unknown character $c")
  }
}

internal fun JvmMethodSignature.decodeParameterTypes(): List<Class<*>> {
  val classList = mutableListOf<Class<*>>()
  val counter = Counter(0)
  while (counter.index < desc.length) {
    when (desc[counter.index]) {
      '(' -> {
        counter.index++
        continue
      }
      ')' -> break
    }
    classList += readType(counter, desc)
  }
  return classList
}

/**
 * Returns the JVM signature in the form "<init>$MethodDescriptor", for example: `"<init>(Ljava/lang/Object;)V")`.
 *
 * Useful for comparing with [JvmMethodSignature].
 *
 * For reference, see the [JVM specification, section 4.3](http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3).
 */
internal val Constructor<*>.jvmMethodSignature: String get() = "<init>${parameterTypes.joinToString(separator = "", prefix = "(", postfix = ")V") { it.descriptor }}"
