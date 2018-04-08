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
package com.squareup.moshi

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

/** Generates functions to encode and decode a property as JSON. */
internal class PropertyGenerator(
  val delegateKey: DelegateKey,
  val name: String,
  val serializedName: String,
  val hasConstructorParameter: Boolean,
  val hasDefault: Boolean,
  val typeName: TypeName
) {
  lateinit var localName: String
  lateinit var localIsPresentName: String

  val isRequired
    get() = !delegateKey.nullable && !hasDefault

  /** We prefer to use 'null' to mean absent, but for some properties those are distinct. */
  val differentiateAbsentFromNull
    get() = delegateKey.nullable && hasDefault

  fun allocateNames(nameAllocator: NameAllocator) {
    localName = nameAllocator.newName(name)
    localIsPresentName = nameAllocator.newName("${name}Set")
  }

  fun generateLocalProperty(): PropertySpec {
    return PropertySpec.builder(localName, typeName.asNullable())
        .mutable(true)
        .initializer("null")
        .build()
  }

  fun generateLocalIsPresentProperty(): PropertySpec {
    return PropertySpec.builder(localIsPresentName, BOOLEAN)
        .mutable(true)
        .initializer("false")
        .build()
  }
}
