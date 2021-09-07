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
package com.squareup.moshi.kotlin.codegen.apt

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
