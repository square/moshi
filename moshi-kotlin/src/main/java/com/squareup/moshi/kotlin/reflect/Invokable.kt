package com.squareup.moshi.kotlin.reflect

import com.squareup.moshi.internal.DEFAULT_CONSTRUCTOR_MARKER
import java.lang.reflect.Constructor
import java.lang.reflect.Method

private val DEFAULT_CONSTRUCTOR_SIGNATURE by
  lazy(LazyThreadSafetyMode.NONE) { DEFAULT_CONSTRUCTOR_MARKER!!.descriptor }

/**
 * A thin wrapper over [Constructor] and [Method] to avoid using [java.lang.reflect.Executable],
 * which is not available on Android until API 26.
 */
internal sealed interface Invokable {
  val parameterTypes: Array<Class<*>>
  val parameterAnnotations: Array<Array<Annotation>>
  val jvmMethodSignature: String

  fun setAccessible()

  fun defaultsSignature(): String

  @JvmInline
  value class InvokableConstructor(val constructor: Constructor<*>) : Invokable {
    override val parameterTypes: Array<Class<*>>
      get() = constructor.parameterTypes

    override val parameterAnnotations: Array<Array<Annotation>>
      get() = constructor.parameterAnnotations

    override val jvmMethodSignature: String
      get() = constructor.jvmMethodSignature

    override fun setAccessible() {
      constructor.isAccessible = true
    }

    override fun defaultsSignature(): String {
      val rawPrefix = jvmMethodSignature.removeSuffix("V").removeSuffix(")")
      return buildDefaultsSignature(rawPrefix, parameterTypes.size, "V")
    }
  }

  @JvmInline
  value class InvokableMethod(val method: Method) : Invokable {
    override val parameterTypes: Array<Class<*>>
      get() = method.parameterTypes

    override val parameterAnnotations: Array<Array<Annotation>>
      get() = method.parameterAnnotations

    override val jvmMethodSignature: String
      get() = method.jvmMethodSignature

    override fun setAccessible() {
      method.isAccessible = true
    }

    override fun defaultsSignature(): String {
      val suffixDescriptor = method.returnType.descriptor
      val rawPrefix = jvmMethodSignature.removeSuffix(suffixDescriptor).removeSuffix(")")
      // Need to add $default to the end of the method name
      val (name, rest) = rawPrefix.split("(", limit = 2)
      // ktlint doesn't support multi-dollar prefixes
      @Suppress("CanConvertToMultiDollarString") val prefix = "$name\$default($rest"
      return buildDefaultsSignature(prefix, parameterTypes.size, suffixDescriptor)
    }
  }
}

private fun buildDefaultsSignature(prefix: String, parameterCount: Int, suffix: String): String {
  val maskParamsToAdd = (parameterCount + 31) / 32
  return buildString {
    append(prefix)
    repeat(maskParamsToAdd) { append("I") }
    append(DEFAULT_CONSTRUCTOR_SIGNATURE)
    append(')')
    append(suffix)
  }
}
