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

import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.isPrimary
import me.eugeniomarletti.kotlin.metadata.jvm.getJvmConstructorSignature
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Constructor
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.util.Elements

/** A constructor in user code that should be called by generated code. */
internal data class TargetConstructor(
  val element: ExecutableElement,
  val proto: Constructor,
  val parameters: Map<String, TargetParameter>
) {
  companion object {
    fun primary(metadata: KotlinClassMetadata, elements: Elements): TargetConstructor {
      val (nameResolver, classProto) = metadata.data

      // todo allow custom constructor
      val proto = classProto.constructorList
          .single { it.isPrimary }
      val constructorJvmSignature = proto.getJvmConstructorSignature(
          nameResolver, classProto.typeTable)
      val element = classProto.fqName
          .let(nameResolver::getString)
          .replace('/', '.')
          .let(elements::getTypeElement)
          .enclosedElements
          .mapNotNull {
            it.takeIf { it.kind == ElementKind.CONSTRUCTOR }?.let { it as ExecutableElement }
          }
          .first()
      // TODO Temporary until JVM method signature matching is better
      //  .single { it.jvmMethodSignature == constructorJvmSignature }

      val parameters = mutableMapOf<String, TargetParameter>()
      for (parameter in proto.valueParameterList) {
        val name = nameResolver.getString(parameter.name)
        val index = proto.valueParameterList.indexOf(parameter)
        parameters[name] = TargetParameter(name, parameter, index, element.parameters[index])
      }

      return TargetConstructor(element, proto, parameters)
    }
  }
}
