package com.squareup.moshi.kotlin.reflect

import com.squareup.moshi.internal.DEFAULT_CONSTRUCTOR_MARKER
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.metadata.KmClass
import kotlin.metadata.KmClassifier
import kotlin.metadata.isSecondary
import kotlin.metadata.isValue
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.signature

private val DEFAULT_CONSTRUCTOR_SIGNATURE by lazy(LazyThreadSafetyMode.NONE) {
  DEFAULT_CONSTRUCTOR_MARKER!!.descriptor
}

/**
 * Simple facade over KM constructor-ish types, which could be a constructor or a static creator method (value classes).
 */
internal sealed class KmExecutable<T : Executable> {
  abstract val parameters: List<KtParameter>
  abstract val isDefault: Boolean
  abstract val needsDefaultMarker: Boolean

  companion object {
    /**
     * Checks if a type (from KM metadata) is a value class and returns its box-impl and unbox-impl methods if so.
     *
     * Returns two nulls if this isn't a value class.
     */
    internal fun findValueClassMethods(
      classifier: KmClassifier,
      classLoader: ClassLoader,
    ): Pair<Method?, Method?> {
      if (classifier !is KmClassifier.Class) {
        // Not a class type (could be type parameter, etc.)
        return null to null
      }

      // Convert kotlin metadata class name to Java class name
      val className = classifier.name.replace('/', '.')
      val clazz = try {
        classLoader.loadClass(className)
      } catch (_: ClassNotFoundException) {
        return null to null
      }

      // Check if it's a value class using Kotlin metadata
      val metadata = clazz.getAnnotation(Metadata::class.java) ?: return null to null
      val classMetadata = KotlinClassMetadata.readLenient(metadata)
      if (classMetadata !is KotlinClassMetadata.Class) {
        return null to null
      }
      val kmClass = classMetadata.kmClass
      if (!kmClass.isValue) {
        return null to null
      }

      // Find the box-impl and unbox-impl methods
      val boxImplMethod = clazz.declaredMethods
        .firstOrNull { it.name == "box-impl" }
        ?: return null to null

      val unboxImplMethod = clazz.declaredMethods
        .firstOrNull { it.name == "unbox-impl" }
        ?: return null to null

      boxImplMethod.isAccessible = true
      unboxImplMethod.isAccessible = true
      return boxImplMethod to unboxImplMethod
    }

    private fun Executable.defaultsSignature(): String {
      val suffixDescriptor = when (this) {
        is Constructor<*> -> "V"
        is Method -> returnType.descriptor
      }
      val rawPrefix = jvmMethodSignature.removeSuffix(suffixDescriptor).removeSuffix(")")
      val prefix = when (this) {
        is Constructor<*> -> {
          rawPrefix
        }

        is Method -> {
          // Need to add $default to the end of the method name
          val (name, rest) = rawPrefix.split("(", limit = 2)
          // ktlint doesn't support multi-dollar prefixes
          @Suppress("CanConvertToMultiDollarString")
          "$name\$default($rest"
        }
      }
      val parameterCount = parameterTypes.size
      val maskParamsToAdd = (parameterCount + 31) / 32
      val defaultConstructorSignature = buildString {
        append(prefix)
        repeat(maskParamsToAdd) { append("I") }
        append(DEFAULT_CONSTRUCTOR_SIGNATURE)
        append(')')
        append(suffixDescriptor)
      }
      return defaultConstructorSignature
    }

    operator fun invoke(rawType: Class<*>, kmClass: KmClass): KmExecutable<*>? {
      // If this is a value class, the "constructor" will actually be a static creator function
      val constructorsBySignature = if (kmClass.isValue) {
        // kmConstructorSignature is something like constructor-impl(I)I
        // Need to look up the matching static function
        rawType.declaredMethods
          .filter { Modifier.isStatic(it.modifiers) }
          .associateBy { it.jvmMethodSignature }
      } else {
        rawType.declaredConstructors.associateBy { it.jvmMethodSignature }
      }
      val kmConstructor = kmClass.constructors.find { !it.isSecondary }
        ?: return null
      val kmConstructorSignature = kmConstructor.signature?.toString() ?: return null
      val jvmConstructor = constructorsBySignature[kmConstructorSignature] ?: return null

      val parameterAnnotations = jvmConstructor.parameterAnnotations
      val parameterTypes = jvmConstructor.parameterTypes
      val parameters =
        kmConstructor.valueParameters.withIndex().map { (index, kmParam) ->
          // Check if this parameter's type is a value class
          val (parameterValueClassBoxer, parameterValueClassUnboxer) =
            findValueClassMethods(kmParam.type.classifier, rawType.classLoader)
          KtParameter(
            kmParam,
            index,
            parameterTypes[index],
            parameterAnnotations[index].toList(),
            parameterValueClassBoxer,
            parameterValueClassUnboxer,
          )
        }
      val anyOptional = parameters.any { it.declaresDefaultValue }
      val actualConstructor =
        if (anyOptional) {
          val defaultsSignature = jvmConstructor.defaultsSignature()
          constructorsBySignature[defaultsSignature] ?: return null
        } else {
          jvmConstructor
        }

      actualConstructor.isAccessible = true

      return if (kmClass.isValue) {
        // Things get quirky here. KM will return the primary constructor for the value class
        // as a constructor-impl static function, BUT this function always only returns the underlying
        // type. What we want is the boxed type, so we need to be able to invoke the constructor and
        // then be able to pass it on to the box-impl function to get the full instance.
        // We can't just skip ahead and use only the box-impl function because the constructor is
        // the only one that handles default values

        val boxImpl = constructorsBySignature.entries
          .first { it.key.startsWith("box-impl") }
          .value
        KmExecutableFunction(
          actualConstructor as Method,
          parameters,
          anyOptional,
          boxImpl as Method,
        )
      } else {
        KmExecutableConstructor(
          actualConstructor as Constructor<*>,
          parameters,
          anyOptional,
        )
      }
    }
  }

  private fun prepareFinalArgs(arguments: Array<Any?>, masks: List<Int>): Array<Any?> {
    if (!needsDefaultMarker) return arguments

    val allArgs = ArrayList<Any?>(arguments.size + masks.size + 1)
    allArgs.addAll(arguments)
    if (isDefault) {
      allArgs.addAll(masks)
    }
    allArgs.add(null) // DefaultConstructorMarker
    return allArgs.toTypedArray()
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> newInstance(arguments: Array<Any?>, masks: List<Int>): T {
    return when (this) {
      is KmExecutableConstructor -> {
        val finalArgs = prepareFinalArgs(arguments, masks)
        defaultsExecutable.newInstance(*finalArgs)
      }

      is KmExecutableFunction -> {
        val finalArgs = prepareFinalArgs(arguments, masks)
        // First get the instance returned by the constructor-impl
        val instance = defaultsExecutable.invoke(null, *finalArgs)
        // Then box it
        boxImpl.invoke(null, instance)
      }
    } as T
  }

  class KmExecutableConstructor(
    val defaultsExecutable: Constructor<*>,
    override val parameters: List<KtParameter>,
    override val isDefault: Boolean,
  ) : KmExecutable<Constructor<*>>() {
    override val needsDefaultMarker = isDefault || parameters.any { it.isValueClass }
  }

  class KmExecutableFunction(
    val defaultsExecutable: Method,
    override val parameters: List<KtParameter>,
    override val isDefault: Boolean,
    val boxImpl: Method,
  ) : KmExecutable<Method>() {
    override val needsDefaultMarker = isDefault || parameters.any { it.isValueClass }
  }
}
