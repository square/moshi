/*
 * Copyright (C) 2021 Square, Inc.
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
package com.squareup.moshi.kotlin.codegen.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind.CLASS
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName

private val OBJECT_CLASS = java.lang.Object::class.asClassName()

/**
 * A concrete type like `List<String>` with enough information to know how to resolve its type
 * variables.
 */
internal class AppliedType private constructor(
  val type: KSClassDeclaration,
  val typeName: TypeName = type.toClassName()
) {

  /** Returns all supertypes of this, recursively. Includes both interface and class supertypes. */
  fun supertypes(
    resolver: Resolver,
  ): LinkedHashSet<AppliedType> {
    val result: LinkedHashSet<AppliedType> = LinkedHashSet()
    result.add(this)
    for (supertype in type.getAllSuperTypes()) {
      val decl = supertype.declaration
      check(decl is KSClassDeclaration)
      if (decl.classKind != CLASS) {
        // Don't load properties for interface types.
        continue
      }
      val qualifiedName = decl.qualifiedName
      val superTypeKsClass = resolver.getClassDeclarationByName(qualifiedName!!)!!
      val typeName = decl.toClassName()
      if (typeName == ANY || typeName == OBJECT_CLASS) {
        // Don't load properties for kotlin.Any/java.lang.Object.
        continue
      }
      result.add(AppliedType(superTypeKsClass, typeName))
    }
    return result
  }

  override fun toString() = type.qualifiedName!!.asString()

  companion object {
    fun get(type: KSClassDeclaration): AppliedType {
      return AppliedType(type)
    }
  }
}
