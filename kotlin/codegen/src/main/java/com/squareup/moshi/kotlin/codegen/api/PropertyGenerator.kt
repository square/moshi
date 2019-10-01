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
package com.squareup.moshi.kotlin.codegen.api

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.PropertySpec

/** Generates functions to encode and decode a property as JSON. */
internal class PropertyGenerator(
  val target: TargetProperty,
  val delegateKey: DelegateKey,
  val isTransient: Boolean = false
) {
  val name = target.name
  val jsonName = target.jsonName ?: target.name
  val hasDefault = target.hasDefault

  lateinit var localName: String
  lateinit var localIsPresentName: String

  val isRequired get() = !delegateKey.nullable && !hasDefault

  val hasConstructorParameter get() = target.parameterIndex != -1

  /**
   * IsPresent is required if the following conditions are met:
   * - Is not transient
   * - Has a default
   * - Is not a constructor parameter (for constructors we use a defaults mask)
   * - Is nullable (because we differentiate absent from null)
   *
   * This is used to indicate that presence should be checked first before possible assigning null
   * to an absent value
   */
  val hasLocalIsPresentName = !isTransient && hasDefault && !hasConstructorParameter && delegateKey.nullable
  val hasConstructorDefault = hasDefault && hasConstructorParameter

  fun allocateNames(nameAllocator: NameAllocator) {
    localName = nameAllocator.newName(name)
    localIsPresentName = nameAllocator.newName("${name}Set")
  }

  fun generateLocalProperty(): PropertySpec {
    return PropertySpec.builder(localName, target.type.copy(nullable = true))
        .mutable(true)
        .apply {
          if (hasConstructorDefault) {
            // We default to the primitive default type, as reflectively invoking the constructor
            // without this (even though it's a throwaway) will fail argument type resolution in
            // the reflective invocation.
            initializer(target.type.defaultPrimitiveValue())
          } else {
            initializer("null")
          }
        }
        .build()
  }

  fun generateLocalIsPresentProperty(): PropertySpec {
    return PropertySpec.builder(localIsPresentName, BOOLEAN)
        .mutable(true)
        .initializer("false")
        .build()
  }
}
