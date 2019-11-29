/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.kotlin.codegen

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Types

/**
 * A concrete type like `List<String>` with enough information to know how to resolve its type
 * variables.
 */
internal class AppliedType private constructor(
  val element: TypeElement,
  private val mirror: DeclaredType
) {
  /** Returns all supertypes of this, recursively. Includes both interface and class supertypes. */
  fun supertypes(
    types: Types,
    result: LinkedHashSet<AppliedType> = LinkedHashSet()
  ): LinkedHashSet<AppliedType> {
    result.add(this)
    for (supertype in types.directSupertypes(mirror)) {
      val supertypeDeclaredType = supertype as DeclaredType
      val supertypeElement = supertypeDeclaredType.asElement() as TypeElement
      val appliedSupertype = AppliedType(supertypeElement, supertypeDeclaredType)
      appliedSupertype.supertypes(types, result)
    }
    return result
  }

  override fun toString() = mirror.toString()

  companion object {
    fun get(typeElement: TypeElement): AppliedType {
      return AppliedType(typeElement, typeElement.asType() as DeclaredType)
    }
  }
}
