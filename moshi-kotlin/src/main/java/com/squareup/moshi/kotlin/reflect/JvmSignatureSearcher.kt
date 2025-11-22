package com.squareup.moshi.kotlin.reflect

import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.metadata.KmProperty
import kotlin.metadata.jvm.JvmFieldSignature
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.syntheticMethodForAnnotations

internal class JvmSignatureSearcher(private val clazz: Class<*>) {
  fun syntheticMethodForAnnotations(kmProperty: KmProperty): Method? =
    kmProperty.syntheticMethodForAnnotations?.let { signature -> findMethod(clazz, signature) }

  fun getter(kmProperty: KmProperty): Method? =
    kmProperty.getterSignature?.let { signature -> findMethod(clazz, signature) }

  fun setter(kmProperty: KmProperty): Method? =
    kmProperty.setterSignature?.let { signature -> findMethod(clazz, signature) }

  fun field(kmProperty: KmProperty): Field? =
    kmProperty.fieldSignature?.let { signature -> findField(clazz, signature) }

  private fun findMethod(sourceClass: Class<*>, signature: JvmMethodSignature): Method {
    val parameterTypes = signature.decodeParameterTypes()
    return try {
      if (parameterTypes.isEmpty()) {
        // Save the empty copy
        sourceClass.getDeclaredMethod(signature.name)
      } else {
        sourceClass.getDeclaredMethod(signature.name, *parameterTypes.toTypedArray())
      }
    } catch (e: NoSuchMethodException) {
      // Try finding the superclass method
      val superClass = sourceClass.superclass
      if (superClass != Any::class.java) {
        return findMethod(superClass, signature)
      } else {
        throw e
      }
    }
  }

  private fun findField(sourceClass: Class<*>, signature: JvmFieldSignature): Field {
    return try {
      sourceClass.getDeclaredField(signature.name)
    } catch (e: NoSuchFieldException) {
      // Try finding the superclass field
      val superClass = sourceClass.superclass
      if (superClass != Any::class.java) {
        return findField(superClass, signature)
      } else {
        throw e
      }
    }
  }
}
