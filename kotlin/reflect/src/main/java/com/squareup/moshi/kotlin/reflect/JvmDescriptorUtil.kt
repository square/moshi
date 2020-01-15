package com.squareup.moshi.kotlin.reflect

import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal val Class<*>.descriptor: String
  get() {
    return when {
      isPrimitive -> when (kotlin) {
        Byte::class -> "B"
        Char::class -> "C"
        Double::class -> "D"
        Float::class -> "F"
        Int::class -> "I"
        Long::class -> "J"
        Short::class -> "S"
        Boolean::class -> "Z"
        Void::class -> "V"
        else -> throw RuntimeException("Unrecognized primitive $this")
      }
      isArray -> name.replace('.', '/')
      else -> "L$name;".replace('.', '/')
    }
  }

internal val Method.descriptor: String
  get() {
    return buildString {
      append('(')
      parameterTypes.joinTo(this, separator = "", transform = { it.descriptor })
      append(')')
      append(returnType.descriptor)
    }
  }

/**
 * Returns the JVM signature in the form "$Name$MethodDescriptor", for example: `equals(Ljava/lang/Object;)Z`.
 *
 * Useful for comparing with [JvmMethodSignature].
 *
 * For reference, see the [JVM specification, section 4.3](http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3).
 */
internal val Method.jvmMethodSignature: String get() = "$name$descriptor"

internal val Constructor<*>.descriptor: String
  get() {
    return buildString {
      append('(')
      parameterTypes.joinTo(this, separator = "", transform = { it.descriptor })
      append(')')
      append('V')
    }
  }

/**
 * Returns the JVM signature in the form "<init>$MethodDescriptor", for example: `"<init>(Ljava/lang/Object;)V")`.
 *
 * Useful for comparing with [JvmMethodSignature].
 *
 * For reference, see the [JVM specification, section 4.3](http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3).
 */
internal val Constructor<*>.jvmMethodSignature: String get() = "<init>$descriptor"

/**
 * Returns the JVM signature in the form "$Name:$FieldDescriptor", for example: `"value:Ljava/lang/String;"`.
 *
 * Useful for comparing with [JvmFieldSignature].
 *
 * For reference, see the [JVM specification, section 4.3](http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3).
 */
internal val Field.jvmFieldSignature: String get() = "$name:${type.descriptor}"