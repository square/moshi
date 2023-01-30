/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.moshi

import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Customizes how a field is encoded as JSON.
 *
 * Although this annotation doesn't declare a [Target], it is only honored in the following
 * elements:
 * - **Java class fields**
 * - **Kotlin properties** for use with `moshi-kotlin` or `moshi-kotlin-codegen`. This includes both properties
 * declared in the constructor and properties declared as members.
 *
 * Users of the [AutoValue: Moshi Extension](https://github.com/rharter/auto-value-moshi) may also use this
 * annotation on abstract getters.
 */
@Retention(RUNTIME)
@MustBeDocumented
public annotation class Json(
  /** The name of the field when encoded as JSON. */
  val name: String = UNSET_NAME,
  /**
   * If true, this field/property will be ignored. This is semantically similar to use of `transient` on the JVM.
   *
   * **Note:** this has no effect in `enum` or `record` classes.
   */
  val ignore: Boolean = false,
) {
  public companion object {
    /** The default value of [name]. Should only be used to check if it's been set. */
    public const val UNSET_NAME: String = "\u0000"
  }
}
