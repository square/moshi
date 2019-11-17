package com.squareup.moshi.kotlin.codegen

import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.metadata.ImmutableKmClass
import com.squareup.kotlinpoet.metadata.specs.ClassInspector
import com.squareup.kotlinpoet.metadata.specs.toTypeSpec
import com.squareup.kotlinpoet.metadata.toImmutableKmClass
import javax.lang.model.element.TypeElement

/**
 * This cached API over [ClassInspector] that caches certain lookups Moshi does potentially multiple
 * times. This is useful mostly because it avoids duplicate reloads in cases like common base
 * classes, common enclosing types, etc.
 */
internal class MoshiCachedClassInspector(private val classInspector: ClassInspector) {
  private val elementToSpecCache = mutableMapOf<TypeElement, TypeSpec>()
  private val kmClassToSpecCache = mutableMapOf<ImmutableKmClass, TypeSpec>()
  private val metadataToKmClassCache = mutableMapOf<Metadata, ImmutableKmClass>()

  fun toImmutableKmClass(metadata: Metadata): ImmutableKmClass {
    return metadataToKmClassCache.getOrPut(metadata) {
      metadata.toImmutableKmClass()
    }
  }

  fun toTypeSpec(kmClass: ImmutableKmClass): TypeSpec {
    return kmClassToSpecCache.getOrPut(kmClass) {
      kmClass.toTypeSpec(classInspector)
    }
  }

  fun toTypeSpec(element: TypeElement): TypeSpec {
    return elementToSpecCache.getOrPut(element) {
      toTypeSpec(toImmutableKmClass(element.metadata))
    }
  }
}