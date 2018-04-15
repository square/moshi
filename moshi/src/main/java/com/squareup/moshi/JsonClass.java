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
package com.squareup.moshi;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Customizes how a type is encoded as JSON.
 */
@Retention(RUNTIME)
@Documented
public @interface JsonClass {
  /**
   * True to trigger the annotation processor to generate an adapter for this type.
   *
   * There are currently some restrictions on which types that can be used with generated adapters:
   *
   *  * The class must be implemented in Kotlin.
   *  * The class may not be an abstract class, an inner class, or a local class.
   *  * All superclasses must be implemented in Kotlin.
   *  * All properties must be public, protected, or internal.
   *  * All properties must be either non-transient or have a default value.
   */
  boolean generateAdapter();
}
