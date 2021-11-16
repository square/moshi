/*
 * Copyright (C) 2020 Square, Inc.
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

import com.squareup.kotlinpoet.ClassName

/**
 * Represents a proguard configuration for a given spec. This covers three main areas:
 * - Keeping the target class name to Moshi's reflective lookup of the adapter.
 * - Keeping the generated adapter class name + public constructor for reflective lookup.
 * - Keeping any used JsonQualifier annotations and the properties they are attached to.
 * - If the target class has default parameter values, also keeping the associated synthetic
 *   constructor as well as the DefaultConstructorMarker type Kotlin adds to it.
 *
 * Each rule is intended to be as specific and targeted as possible to reduce footprint, and each is
 * conditioned on usage of the original target type.
 *
 * To keep this processor as an ISOLATING incremental processor, we generate one file per target
 * class with a deterministic name (see [outputFilePathWithoutExtension]) with an appropriate
 * originating element.
 */
@InternalMoshiCodegenApi
public data class ProguardConfig(
  val targetClass: ClassName,
  val adapterName: String,
  val adapterConstructorParams: List<String>,
  val targetConstructorHasDefaults: Boolean,
  val targetConstructorParams: List<String>,
) {
  public fun outputFilePathWithoutExtension(canonicalName: String): String {
    return "META-INF/proguard/moshi-$canonicalName"
  }

  public fun writeTo(out: Appendable): Unit = out.run {
    //
    // -if class {the target class}
    // -keepnames class {the target class}
    // -if class {the target class}
    // -keep class {the generated adapter} {
    //    <init>(...);
    //    private final {adapter fields}
    // }
    //
    val targetName = targetClass.reflectionName()
    val adapterCanonicalName = ClassName(targetClass.packageName, adapterName).canonicalName
    // Keep the class name for Moshi's reflective lookup based on it
    appendLine("-if class $targetName")
    appendLine("-keepnames class $targetName")

    appendLine("-if class $targetName")
    appendLine("-keep class $adapterCanonicalName {")
    // Keep the constructor for Moshi's reflective lookup
    val constructorArgs = adapterConstructorParams.joinToString(",")
    appendLine("    public <init>($constructorArgs);")
    appendLine("}")

    if (targetConstructorHasDefaults) {
      // If the target class has default parameter values, keep its synthetic constructor
      //
      // -keepnames class kotlin.jvm.internal.DefaultConstructorMarker
      // -keepclassmembers @com.squareup.moshi.JsonClass @kotlin.Metadata class * {
      //     synthetic <init>(...);
      // }
      //
      appendLine("-if class $targetName")
      appendLine("-keepnames class kotlin.jvm.internal.DefaultConstructorMarker")
      appendLine("-if class $targetName")
      appendLine("-keepclassmembers class $targetName {")
      val allParams = targetConstructorParams.toMutableList()
      val maskCount = if (targetConstructorParams.isEmpty()) {
        0
      } else {
        (targetConstructorParams.size + 31) / 32
      }
      repeat(maskCount) {
        allParams += "int"
      }
      allParams += "kotlin.jvm.internal.DefaultConstructorMarker"
      val params = allParams.joinToString(",")
      appendLine("    public synthetic <init>($params);")
      appendLine("}")
    }
  }
}

/**
 * Represents a qualified property with its [name] in the adapter fields and list of [qualifiers]
 * associated with it.
 */
@InternalMoshiCodegenApi
public data class QualifierAdapterProperty(val name: String, val qualifiers: Set<ClassName>)
