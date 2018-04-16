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
package com.squareup.moshi

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
import me.eugeniomarletti.kotlin.metadata.visibility
import org.jetbrains.kotlin.serialization.ProtoBuf.Class
import org.jetbrains.kotlin.serialization.ProtoBuf.Modality.ABSTRACT
import org.jetbrains.kotlin.serialization.ProtoBuf.TypeParameter
import org.jetbrains.kotlin.serialization.ProtoBuf.Visibility.LOCAL
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic.Kind.ERROR

/** A user type that should be decoded and encoded by generated code. */
internal data class TargetType(
  val proto: Class,
  val element: TypeElement,
  val constructor: TargetConstructor,
  val properties: Map<String, TargetProperty>,
  val genericTypeNames: List<TypeVariableName>
) {
  val name = element.className
  val hasCompanionObject = proto.hasCompanionObjectName()

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

      val constructor = TargetConstructor.primary(typeMetadata, elements)
      val properties = mutableMapOf<String, TargetProperty>()
      for (supertype in element.supertypes(types)) {
        if (supertype.asClassName() == OBJECT_CLASS) {
          continue // Don't load properties for java.lang.Object.
        }
        if (supertype.kind != ElementKind.CLASS) {
          continue // Don't load properties for interface types.
        }
        if (supertype.kotlinMetadata == null) {
          messager.printMessage(ERROR,
              "@JsonClass can't be applied to $element: supertype $supertype is not a Kotlin type",
              element)
        }
        for ((name, property) in declaredProperties(supertype, constructor)) {
          properties.putIfAbsent(name, property)
        }
      }
      val genericTypeNames = genericTypeNames(proto, typeMetadata.data.nameResolver)
      return TargetType(proto, element, constructor, properties, genericTypeNames)
    }

    /** Returns the properties declared by `typeElement`. */
    private fun declaredProperties(
      typeElement: TypeElement,
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
        val type = property.returnType.asTypeName(
            nameResolver, classProto::getTypeParameter, false)
        val typeWithResolvedAliases = property.returnType.asTypeName(
            nameResolver, classProto::getTypeParameter, true)
        result[name] = TargetProperty(name, type, typeWithResolvedAliases, property,
            constructor.parameters[name], annotationHolders[name], fields[name],
            setters[name], getters[name])
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

    /** Returns all supertypes of this, recursively. Includes interface and class supertypes. */
    private fun TypeElement.supertypes(
      types: Types,
      result: MutableSet<TypeElement> = mutableSetOf()
    ): Set<TypeElement> {
      result.add(this)
      for (supertype in types.directSupertypes(asType())) {
        val supertypeElement = (supertype as DeclaredType).asElement() as TypeElement
        supertypeElement.supertypes(types, result)
      }
      return result
    }

    private val Element.name get() = simpleName.toString()

    private fun genericTypeNames(proto: Class, nameResolver: NameResolver): List<TypeVariableName> {
      return proto.typeParameterList.map {
        TypeVariableName(
            name = nameResolver.getString(it.name),
            bounds = *(it.upperBoundList
                .map { it.asTypeName(nameResolver, proto::getTypeParameter) }
                .toTypedArray()),
            variance = it.varianceModifier)
            .reified(it.reified)
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