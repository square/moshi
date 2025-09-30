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
package com.squareup.moshi.kotlin.codegen.api

import com.squareup.kotlinpoet.ClassName

@InternalMoshiCodegenApi
public object Options {
  /**
   * This processing option can be specified to have a `@Generated` annotation
   * included in the generated code. It is not encouraged unless you need it for static analysis
   * reasons and not enabled by default.
   *
   * Note that this can only be one of the following values:
   *   * `"javax.annotation.processing.Generated"` (JRE 9+)
   *   * `"javax.annotation.Generated"` (JRE <9)
   */
  public const val OPTION_GENERATED: String = "moshi.generated"

  /**
   * This boolean processing option can disable proguard rule generation.
   * Normally, this is not recommended unless end-users build their own JsonAdapter look-up tool.
   * This is enabled by default.
   */
  public const val OPTION_GENERATE_PROGUARD_RULES: String = "moshi.generateProguardRules"

  /**
   * This boolean processing option can enable omission of `-keepnames` rules for target classes.
   * Moshi's reflective lookup of adapters by class name will fail if obfuscation is applied
   * to the target class. Only enable this if you are sure you won't be using Moshi's
   * reflective adapter lookup.
   * If you set [OPTION_GENERATE_DEFAULT_ADAPTER_REGISTRY] to `true` and decide to
   * manually set the generated registry, you can also enable this option to
   * avoid keeping target class names. (LET THEM BE OBFUSCATION, I DON'T WANT THE CLASS NAME TO BE RETAINED!)
   * This option is automatically invalid if [OPTION_GENERATE_PROGUARD_RULES] is set to `false`.
   * This is disabled by default for backward compatibility.
   */
  public const val OPTION_PROGUARD_RULES_DONT_KEEP_CLASS_NAMES: String = "moshi.proguardRulesDontKeepClassNames"

  /**
   * This boolean processing option can enable generation of a default adapter registry that
   * a compile-time adapter registry that doesn't depend on class names for lookup.
   * The generated registry content is a class annotated with `@JsonClass(generateAdapter = true)`.
   * This is disabled by default for backward compatibility.
   */
  public const val OPTION_GENERATE_DEFAULT_ADAPTER_REGISTRY: String = "moshi.generateDefaultAdapterRegistry"

  /**
   * This boolean processing option controls whether or not Moshi will directly instantiate
   * JsonQualifier annotations in Kotlin 1.6+. Note that this is enabled by default in Kotlin 1.6
   * but can be disabled to restore the legacy behavior of storing annotations on generated adapter
   * fields and looking them up reflectively.
   */
  public const val OPTION_INSTANTIATE_ANNOTATIONS: String = "moshi.instantiateAnnotations"

  public val POSSIBLE_GENERATED_NAMES: Map<String, ClassName> = arrayOf(
    ClassName("javax.annotation.processing", "Generated"),
    ClassName("javax.annotation", "Generated"),
  ).associateBy { it.canonicalName }
}
