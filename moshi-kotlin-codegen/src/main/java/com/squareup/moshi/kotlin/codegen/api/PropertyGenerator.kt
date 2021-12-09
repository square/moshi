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
package com.squareup.moshi.kotlin.codegen.api

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.PropertySpec

/** Generates functions to encode and decode a property as JSON. */
@InternalMoshiCodegenApi
public class PropertyGenerator(
  public val target: TargetProperty,
  public val delegateKey: DelegateKey,
  public val isTransient: Boolean = false
) {
  public val name: String = target.name
  public val jsonName: String = target.jsonName ?: target.name
  public val hasDefault: Boolean = target.hasDefault

  public lateinit var localName: String
  public lateinit var localIsPresentName: String

  public val isRequired: Boolean get() = !delegateKey.nullable && !hasDefault

  public val hasConstructorParameter: Boolean get() = target.parameterIndex != -1

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
  public val hasLocalIsPresentName: Boolean = !isTransient && hasDefault && !hasConstructorParameter && delegateKey.nullable
  public val hasConstructorDefault: Boolean = hasDefault && hasConstructorParameter

  internal fun allocateNames(nameAllocator: NameAllocator) {
    localName = nameAllocator.newName(name)
    localIsPresentName = nameAllocator.newName("${name}Set")
  }

  internal fun generateLocalProperty(): PropertySpec {
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

  internal fun generateLocalIsPresentProperty(): PropertySpec {
    return PropertySpec.builder(localIsPresentName, BOOLEAN)
      .mutable(true)
      .initializer("false")
      .build()
  }
}
