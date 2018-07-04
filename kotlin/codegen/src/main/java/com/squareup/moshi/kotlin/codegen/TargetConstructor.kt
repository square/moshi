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
package com.squareup.moshi.kotlin.codegen

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/** A constructor in user code that should be called by generated code. */
internal data class TargetConstructor(
    val element: ExecutableElement,
    val data: ConstructorData,
    val parameters: Map<String, TargetParameter>
) {
  companion object {
    fun primary(constructorData: ConstructorData, typeElement: TypeElement): TargetConstructor {
      val element = typeElement
          .enclosedElements
          .mapNotNull {
            it.takeIf { it.kind == ElementKind.CONSTRUCTOR }?.let { it as ExecutableElement }
          }
          .first()

      val parameters = mutableMapOf<String, TargetParameter>()
      for ((index, parameter) in constructorData.parameters.withIndex()) {
        val name = parameter.name
        parameters[name] = TargetParameter(name, parameter, index, element.parameters[index])
      }

      return TargetConstructor(element, constructorData, parameters)
    }
  }
}
