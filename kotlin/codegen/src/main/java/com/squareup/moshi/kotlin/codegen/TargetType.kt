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
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadata
import me.eugeniomarletti.kotlin.metadata.classKind
import me.eugeniomarletti.kotlin.metadata.getPropertyOrNull
import me.eugeniomarletti.kotlin.metadata.isInnerClass
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.modality
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Class
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Modality.ABSTRACT
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.TypeParameter
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Visibility.INTERNAL
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Visibility.LOCAL
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Visibility.PUBLIC
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.deserialization.NameResolver
import me.eugeniomarletti.kotlin.metadata.shadow.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import me.eugeniomarletti.kotlin.metadata.visibility
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
  val proto: Class,
  val element: TypeElement,
  val constructor: TargetConstructor,
  val properties: Map<String, TargetProperty>,
  val typeVariables: List<TypeVariableName>
) {
  val name = element.className

  companion object {
    private val OBJECT_CLASS = ClassName("java.lang", "Object")

    /** Returns a target type for `element`, or null if it cannot be used with code gen. */
    fun get(messager: Messager, elements: Elements, types: Types, element: Element): TargetType? {
      val typeMetadata: KotlinMetadata? = element.kotlinMetadata
      if (element !is TypeElement || typeMetadata !is KotlinClassMetadata) {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class", element)
        return null
      }

      val proto = typeMetadata.data.classProto
      when {
        proto.classKind == Class.Kind.ENUM_CLASS -> {
          messager.printMessage(
              ERROR, "@JsonClass with 'generateAdapter = \"true\"' can't be applied to $element: code gen for enums is not supported or necessary", element)
          return null
        }
        proto.classKind != Class.Kind.CLASS -> {
          messager.printMessage(
              ERROR, "@JsonClass can't be applied to $element: must be a Kotlin class", element)
          return null
        }
        proto.isInnerClass -> {
          messager.printMessage(
              ERROR, "@JsonClass can't be applied to $element: must not be an inner class", element)
          return null
        }
        proto.modality == ABSTRACT -> {
          messager.printMessage(
              ERROR, "@JsonClass can't be applied to $element: must not be abstract", element)
          return null
        }
        proto.visibility == LOCAL -> {
          messager.printMessage(
              ERROR, "@JsonClass can't be applied to $element: must not be local", element)
          return null
        }
      }

      val typeVariables = genericTypeNames(proto, typeMetadata.data.nameResolver)
      val appliedType = AppliedType.get(element)

      val constructor = TargetConstructor.primary(typeMetadata, elements)
      if (constructor.proto.visibility != INTERNAL && constructor.proto.visibility != PUBLIC) {
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
        if (supertype.element.kotlinMetadata == null) {
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
      return TargetType(proto, element, constructor, properties, typeVariables)
    }

    /** Returns the properties declared by `typeElement`. */
    private fun declaredProperties(
      typeElement: TypeElement,
      typeResolver: TypeResolver,
      constructor: TargetConstructor
    ): Map<String, TargetProperty> {
      val typeMetadata: KotlinClassMetadata = typeElement.kotlinMetadata as KotlinClassMetadata
      val nameResolver = typeMetadata.data.nameResolver
      val classProto = typeMetadata.data.classProto

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

          val propertyProto = typeMetadata.data.getPropertyOrNull(element)
          if (propertyProto != null) {
            val name = nameResolver.getString(propertyProto.name)
            annotationHolders[name] = element
          }
        }
      }

      val result = mutableMapOf<String, TargetProperty>()
      for (property in classProto.propertyList) {
        val name = nameResolver.getString(property.name)
        val type = typeResolver.resolve(property.returnType.asTypeName(
            nameResolver, classProto::getTypeParameter, false))
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

    private fun genericTypeNames(proto: Class, nameResolver: NameResolver): List<TypeVariableName> {
      return proto.typeParameterList.map {
        val possibleBounds = it.upperBoundList
            .map { it.asTypeName(nameResolver, proto::getTypeParameter, false) }
        val typeVar = if (possibleBounds.isEmpty()) {
          TypeVariableName(
              name = nameResolver.getString(it.name),
              variance = it.varianceModifier)
        } else {
        TypeVariableName(
            name = nameResolver.getString(it.name),
            bounds = *possibleBounds.toTypedArray(),
            variance = it.varianceModifier)
        }
        return@map typeVar.reified(it.reified)
      }
    }

    private val TypeParameter.varianceModifier: KModifier?
      get() {
        return variance.asKModifier().let {
          // We don't redeclare out variance here
          if (it == KModifier.OUT) {
            null
          } else {
            it
          }
        }
      }
  }
}
