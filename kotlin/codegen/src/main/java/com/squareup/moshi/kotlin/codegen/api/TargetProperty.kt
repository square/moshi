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

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

/** A property in user code that maps to JSON. */
internal data class TargetProperty(
  val propertySpec: PropertySpec,
  val parameter: TargetParameter?,
  val visibility: KModifier,
  val jsonName: String?
) {
  val name: String get() = propertySpec.name
  val type: TypeName get() = propertySpec.type
  val parameterIndex get() = parameter?.index ?: -1
  val hasDefault get() = parameter?.hasDefault ?: true

  override fun toString() = name
}
