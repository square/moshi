/*
 * Copyright (C) 2018 Square, Inc.
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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.asClassName
import javax.lang.model.element.ElementKind.CLASS
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Types

private val OBJECT_CLASS = ClassName("java.lang", "Object")

/**
 * A concrete type like `List<String>` with enough information to know how to resolve its type
 * variables.
 */
internal class AppliedType private constructor(
  val element: TypeElement,
  private val mirror: DeclaredType
) {
  /** Returns all supertypes of this, recursively. Only [CLASS] is used as we can't really use other types. */
  @OptIn(DelicateKotlinPoetApi::class)
  fun superclasses(
    types: Types,
    result: LinkedHashSet<AppliedType> = LinkedHashSet()
  ): LinkedHashSet<AppliedType> {
    result.add(this)
    for (supertype in types.directSupertypes(mirror)) {
      val supertypeDeclaredType = supertype as DeclaredType
      val supertypeElement = supertypeDeclaredType.asElement() as TypeElement
      if (supertypeElement.kind != CLASS) {
        continue
      } else if (supertypeElement.asClassName() == OBJECT_CLASS) {
        // Don't load properties for java.lang.Object.
        continue
      }
      val appliedSuperclass = AppliedType(supertypeElement, supertypeDeclaredType)
      appliedSuperclass.superclasses(types, result)
    }
    return result
  }

  override fun toString() = mirror.toString()

  companion object {
    operator fun invoke(typeElement: TypeElement): AppliedType {
      return AppliedType(typeElement, typeElement.asType() as DeclaredType)
    }
  }
}
