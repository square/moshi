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
package com.squareup.moshi.kotlin.codegen.test.extra

import com.squareup.moshi.Json

public abstract class AbstractClassInModuleA {
  // Ignored to ensure processor sees them across module boundaries.
  // @Transient doesn't work for this case because it's source-only and jvm modifiers aren't currently visible in KSP.

  // Note that we target the field because otherwise it is stored on the synthetic holder method for
  // annotations, which isn't visible from kapt
  @field:Json(ignore = true) private lateinit var lateinitIgnored: String
  @field:Json(ignore = true) private var regularIgnored: String = "regularIgnored"
}
