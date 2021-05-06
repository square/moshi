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
package com.squareup.moshi.kotlin.codegen.annotation

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.ToJson
import java.util.Locale

@JsonQualifier
annotation class UppercaseInAnnotationPackage

class UppercaseInAnnotationPackageJsonAdapter {
  @ToJson
  fun toJson(@UppercaseInAnnotationPackage s: String): String {
    return s.uppercase(Locale.US)
  }
  @FromJson
  @UppercaseInAnnotationPackage
  fun fromJson(s: String): String {
    return s.lowercase(Locale.US)
  }
}
