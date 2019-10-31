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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.TypeName

/** A parameter in user code that should be populated by generated code. */
internal data class TargetParameter(
  val name: String,
  val index: Int,
  val type: TypeName,
  val hasDefault: Boolean,
  val jsonName: String? = null,
  val qualifiers: Set<AnnotationSpec>? = null
)
