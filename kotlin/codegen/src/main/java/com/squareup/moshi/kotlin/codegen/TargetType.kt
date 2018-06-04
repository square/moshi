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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.metadata.Flag
import kotlinx.metadata.jvm.KotlinClassMetadata
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic.Kind.ERROR

/** A user type that should be decoded and encoded by generated code. */
internal data class TargetType(
    val data: ClassData,
    val element: TypeElement,
    val constructor: TargetConstructor,
    val properties: Map<String, TargetProperty>,
    val typeVariables: List<TypeVariableName>,
    val companionObjectName: String?
) {
  val name = element.className

  companion object {
    private val OBJECT_CLASS = ClassName("java.lang", "Object")

    /** Returns a target type for `element`, or null if it cannot be used with code gen. */
    fun get(messager: Messager, elements: Elements, types: Types, element: Element): TargetType? {
      val classMetadata = element.readMetadata()?.readKotlinClassMetadata()
      if (element !is TypeElement || classMetadata !is KotlinClassMetadata) {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class", element)
        return null
      }

      val typeMetadata = classMetadata as KotlinClassMetadata.Class
      val classData = typeMetadata.readClassData()
      if (Flag.Class.IS_ENUM_CLASS(classData.flags)) {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $element: must not be an enum class", element)
        return null
      } else if (!Flag.Class.IS_CLASS(classData.flags)) {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class", element)
        return null
      } else if (Flag.IS_ABSTRACT(classData.flags)) {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $element: must not be abstract", element)
        return null
      } else if (Flag.IS_LOCAL(classData.flags)) {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $element: must not be local", element)
        return null
      } else if (Flag.Class.IS_INNER(classData.flags)) {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $element: must not be an inner class", element)
        return null
      }

      val typeVariables = classData.typeVariables
      val appliedType = AppliedType.get(element)

      val constructor = TargetConstructor.primary(classData.constructorData!!,
          elements.getTypeElement(classData.name))
      if (!Flag.IS_INTERNAL(constructor.data.flags) && !Flag.IS_PUBLIC(constructor.data.flags)) {
        messager.printMessage(ERROR, "@JsonClass can't be applied to $element: " +
            "primary constructor is not internal or public", element)
        return null
      }

      val properties = mutableMapOf<String, TargetProperty>()
      for (supertype in appliedType.supertypes(types)) {
        if (supertype.element.asClassName() == OBJECT_CLASS) {
          continue // Don't load properties for java.lang.Object.
        }
        if (supertype.element.kind != ElementKind.CLASS) {
          continue // Don't load properties for interface types.
        }
        if (supertype.element.readMetadata() == null) {
          messager.printMessage(ERROR,
              "@JsonClass can't be applied to $element: supertype $supertype is not a Kotlin type",
              element)
          return null
        }
        val supertypeProperties = declaredProperties(
            supertype.element, supertype.resolver, constructor)
        for ((name, property) in supertypeProperties) {
          properties.putIfAbsent(name, property)
        }
      }
      return TargetType(classData, element, constructor, properties, typeVariables,
          classData.companionObjectName)
    }

    /** Returns the properties declared by `typeElement`. */
    private fun declaredProperties(
        typeElement: TypeElement,
        typeResolver: TypeResolver,
        constructor: TargetConstructor
    ): Map<String, TargetProperty> {
      val classMetadata = typeElement.readMetadata()?.readKotlinClassMetadata()
      val typeMetadata = classMetadata as KotlinClassMetadata.Class
      val classData = typeMetadata.readClassData()

      val annotationHolders = mutableMapOf<String, ExecutableElement>()
      val fields = mutableMapOf<String, VariableElement>()
      val setters = mutableMapOf<String, ExecutableElement>()
      val getters = mutableMapOf<String, ExecutableElement>()
      for (element in typeElement.enclosedElements) {
        if (element is VariableElement) {
          fields[element.name] = element
        } else if (element is ExecutableElement) {
          when {
            element.name.startsWith("get") -> {
              val name = element.name.substring("get".length).decapitalizeAsciiOnly()
              getters[name] = element
            }
            element.name.startsWith("is") -> {
              val name = element.name.substring("is".length).decapitalizeAsciiOnly()
              getters[name] = element
            }
            element.name.startsWith("set") -> {
              val name = element.name.substring("set".length).decapitalizeAsciiOnly()
              setters[name] = element
            }
          }

          val propertyData = classData.getPropertyOrNull(element)
          if (propertyData != null) {
            annotationHolders[propertyData.name] = element
          }
        }
      }

      val result = mutableMapOf<String, TargetProperty>()
      for (property in classData.properties) {
        val name = property.name
        val type = typeResolver.resolve(property.type)
        result[name] = TargetProperty(name, type, property, constructor.parameters[name],
            annotationHolders[name], fields[name], setters[name], getters[name])
      }

      return result
    }

    private val Element.className: ClassName
      get() {
        val typeName = asType().asTypeName()
        return when (typeName) {
          is ClassName -> typeName
          is ParameterizedTypeName -> typeName.rawType
          else -> throw IllegalStateException("unexpected TypeName: ${typeName::class}")
        }
      }

    private val Element.name get() = simpleName.toString()

  }
}

private fun String.decapitalizeAsciiOnly(): String {
  if (isEmpty()) return this
  val c = this[0]
  return if (c in 'A'..'Z')
    c.toLowerCase() + substring(1)
  else
    this
}
