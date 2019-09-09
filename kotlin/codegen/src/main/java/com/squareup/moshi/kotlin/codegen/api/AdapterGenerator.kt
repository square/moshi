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
package com.squareup.moshi.kotlin.codegen.api

import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.internal.Util
import java.lang.reflect.Constructor
import java.lang.reflect.Type

private val MOSHI_UTIL = Util::class.asClassName()

/** Generates a JSON adapter for a target type. */
internal class AdapterGenerator(
    target: TargetType,
    private val propertyList: List<PropertyGenerator>
) {
  private val nonTransientProperties = propertyList.filterNot { it.isTransient }
  private val className = target.typeName.rawType()
  private val visibility = target.visibility
  private val typeVariables = target.typeVariables

  private val nameAllocator = NameAllocator()
  private val adapterName = "${className.simpleNames.joinToString(separator = "_")}JsonAdapter"
  private val originalTypeName = target.typeName

  private val moshiParam = ParameterSpec.builder(
      nameAllocator.newName("moshi"),
      Moshi::class).build()
  private val typesParam = ParameterSpec.builder(
      nameAllocator.newName("types"),
      ARRAY.parameterizedBy(Type::class.asTypeName()))
      .build()
  private val readerParam = ParameterSpec.builder(
      nameAllocator.newName("reader"),
      JsonReader::class)
      .build()
  private val writerParam = ParameterSpec.builder(
      nameAllocator.newName("writer"),
      JsonWriter::class)
      .build()
  private val valueParam = ParameterSpec.builder(
      nameAllocator.newName("value"),
      originalTypeName.copy(nullable = true))
      .build()
  private val jsonAdapterTypeName = JsonAdapter::class.asClassName().parameterizedBy(
      originalTypeName)

  // selectName() API setup
  private val optionsProperty = PropertySpec.builder(
      nameAllocator.newName("options"), JsonReader.Options::class.asTypeName(),
      KModifier.PRIVATE)
      .initializer("%T.of(${nonTransientProperties.joinToString(", ") {
        CodeBlock.of("%S", it.jsonName).toString()
      }})", JsonReader.Options::class.asTypeName())
      .build()

  private val constructorProperty = PropertySpec.builder(
      nameAllocator.newName("constructorRef"),
      Constructor::class.asClassName().parameterizedBy(originalTypeName).copy(nullable = true),
      KModifier.PRIVATE)
      .addAnnotation(Volatile::class)
      .mutable(true)
      .initializer("null")
      .build()

  fun generateFile(typeHook: (TypeSpec) -> TypeSpec = { it }): FileSpec {
    for (property in nonTransientProperties) {
      property.allocateNames(nameAllocator)
    }

    val result = FileSpec.builder(className.packageName, adapterName)
    result.addComment("Code generated by moshi-kotlin-codegen. Do not edit.")
    result.addType(generateType().let(typeHook))
    return result.build()
  }

  private fun generateType(): TypeSpec {
    val result = TypeSpec.classBuilder(adapterName)

    result.superclass(jsonAdapterTypeName)

    if (typeVariables.isNotEmpty()) {
      result.addTypeVariables(typeVariables)
    }

    // TODO make this configurable. Right now it just matches the source model
    if (visibility == KModifier.INTERNAL) {
      result.addModifiers(KModifier.INTERNAL)
    }

    result.primaryConstructor(generateConstructor())

    val typeRenderer: TypeRenderer = object : TypeRenderer() {
      override fun renderTypeVariable(typeVariable: TypeVariableName): CodeBlock {
        val index = typeVariables.indexOfFirst { it == typeVariable }
        check(index != -1) { "Unexpected type variable $typeVariable" }
        return CodeBlock.of("%N[%L]", typesParam, index)
      }
    }

    result.addProperty(optionsProperty)
    for (uniqueAdapter in nonTransientProperties.distinctBy { it.delegateKey }) {
      result.addProperty(uniqueAdapter.delegateKey.generateProperty(
          nameAllocator, typeRenderer, moshiParam, uniqueAdapter.name))
    }

    result.addFunction(generateToStringFun())
    result.addFunction(generateFromJsonFun(result))
    result.addFunction(generateToJsonFun())

    return result.build()
  }

  private fun generateConstructor(): FunSpec {
    val result = FunSpec.constructorBuilder()
    result.addParameter(moshiParam)

    if (typeVariables.isNotEmpty()) {
      result.addParameter(typesParam)
    }

    return result.build()
  }

  private fun generateToStringFun(): FunSpec {
    return FunSpec.builder("toString")
        .addModifiers(KModifier.OVERRIDE)
        .returns(String::class)
        .addStatement("return %S",
            "GeneratedJsonAdapter(${originalTypeName.rawType().simpleNames.joinToString(".")})")
        .build()
  }

  private fun generateFromJsonFun(classBuilder: TypeSpec.Builder): FunSpec {
    val result = FunSpec.builder("fromJson")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter(readerParam)
        .returns(originalTypeName)

    for (property in nonTransientProperties) {
      result.addCode("%L", property.generateLocalProperty())
      if (property.hasLocalIsPresentName) {
        result.addCode("%L", property.generateLocalIsPresentProperty())
      }
    }

    val maskName = nameAllocator.newName("mask")
    val useConstructorDefaults = nonTransientProperties.any { it.hasConstructorDefault }
    if (useConstructorDefaults) {
      result.addStatement("var %L = -1", maskName)
    }
    result.addStatement("%N.beginObject()", readerParam)
    result.beginControlFlow("while (%N.hasNext())", readerParam)
    result.beginControlFlow("when (%N.selectName(%N))", readerParam, optionsProperty)

    // We track property index and mask index separately, because mask index is based on _all_
    // constructor arguments, while property index is only based on the index passed into
    // JsonReader.Options.
    var propertyIndex = 0
    var maskIndex = 0
    for (property in propertyList) {
      if (property.isTransient) {
        if (property.hasConstructorParameter) {
          maskIndex++
        }
        continue
      }
      if (property.hasLocalIsPresentName || property.hasConstructorDefault) {
        result.beginControlFlow("%L ->", propertyIndex)
        if (property.delegateKey.nullable) {
          result.addStatement("%N = %N.fromJson(%N)",
              property.localName, nameAllocator[property.delegateKey], readerParam)
        } else {
          val exception = unexpectedNull(property.localName, readerParam)
          result.addStatement("%N = %N.fromJson(%N) ?: throw·%L",
              property.localName, nameAllocator[property.delegateKey], readerParam, exception)
        }
        if (property.hasConstructorDefault) {
          val inverted = (1 shl maskIndex).inv()
          result.addComment("\$mask = \$mask and (1 shl %L).inv()", maskIndex)
          result.addStatement("%1L = %1L and %2L", maskName, inverted)
        } else {
          // Presence tracker for a mutable property
          result.addStatement("%N = true", property.localIsPresentName)
        }
        result.endControlFlow()
      } else {
        if (property.delegateKey.nullable) {
          result.addStatement("%L -> %N = %N.fromJson(%N)",
              propertyIndex, property.localName, nameAllocator[property.delegateKey], readerParam)
        } else {
          val exception = unexpectedNull(property.localName, readerParam)
          result.addStatement("%L -> %N = %N.fromJson(%N) ?: throw·%L",
              propertyIndex, property.localName, nameAllocator[property.delegateKey], readerParam,
              exception)
        }
      }
      propertyIndex++
      maskIndex++
    }

    result.beginControlFlow("-1 ->")
    result.addComment("Unknown name, skip it.")
    result.addStatement("%N.skipName()", readerParam)
    result.addStatement("%N.skipValue()", readerParam)
    result.endControlFlow()

    result.endControlFlow() // when
    result.endControlFlow() // while
    result.addStatement("%N.endObject()", readerParam)

    var separator = "\n"
    val parameterProperties = propertyList.filter { it.hasConstructorParameter }
    val useDefaultsConstructor = parameterProperties.any { it.hasDefault }

    val resultName = nameAllocator.newName("result")
    val hasNonConstructorProperties = nonTransientProperties.any { !it.hasConstructorParameter }
    val returnOrResultAssignment = if (hasNonConstructorProperties) {
      // Save the result var for reuse
      CodeBlock.of("val %N = ", resultName)
    } else {
      CodeBlock.of("return·")
    }
    if (useDefaultsConstructor) {
      classBuilder.addProperty(constructorProperty)
      // Dynamic default constructor call
      val rawOriginalTypeName = originalTypeName.rawType()
      val nonNullConstructorType = constructorProperty.type.copy(nullable = false)
      val coreLookupBlock = CodeBlock.of(
          "%T.lookupDefaultsConstructor(%T::class.java)",
          MOSHI_UTIL,
          rawOriginalTypeName
      )
      val lookupBlock = if (originalTypeName is ParameterizedTypeName) {
        CodeBlock.of("(%L·as·%T)", coreLookupBlock, nonNullConstructorType)
      } else {
        coreLookupBlock
      }
      val initializerBlock = CodeBlock.of(
          "this.%1N ?:·%2L.also·{ this.%1N·= it }",
          constructorProperty,
          lookupBlock
      )
      val localConstructorProperty = PropertySpec.builder(
          nameAllocator.newName("localConstructor"),
          nonNullConstructorType)
          .addAnnotation(AnnotationSpec.builder(Suppress::class)
              .addMember("%S", "UNCHECKED_CAST")
              .build())
          .initializer(initializerBlock)
          .build()
      result.addCode("%L", localConstructorProperty)
      result.addCode(
          "«%L%N.newInstance(",
          returnOrResultAssignment,
          localConstructorProperty
      )
    } else {
      // Standard constructor call
      result.addCode("«%L%T(", returnOrResultAssignment, originalTypeName)
    }

    for (property in parameterProperties) {
      result.addCode(separator)
      if (useDefaultsConstructor) {
        if (property.isTransient) {
          // We have to use the default primitive for the available type in order for
          // invokeDefaultConstructor to properly invoke it. Just using "null" isn't safe because
          // the transient type may be a primitive type.
          result.addCode(property.target.type.defaultPrimitiveValue())
        } else {
          result.addCode("%N", property.localName)
        }
      } else {
        result.addCode("%N = %N", property.name, property.localName)
      }
      if (!property.isTransient && property.isRequired) {
        val missingPropertyBlock =
            CodeBlock.of("%T.missingProperty(%S, %N)", Util::class, property.localName, readerParam)
        result.addCode(" ?: throw·%L", missingPropertyBlock)
      }
      separator = ",\n"
    }

    if (useDefaultsConstructor) {
      // Add the mask and a null instance for the trailing default marker instance
      result.addCode(",\n%L,\nnull", maskName)
    }

    result.addCode("\n»)\n")

    // Assign properties not present in the constructor.
    for (property in nonTransientProperties) {
      if (property.hasConstructorParameter) {
        continue // Property already handled.
      }
      if (property.hasLocalIsPresentName) {
        result.addStatement("%1N.%2N = if (%3N) %4N else %1N.%2N",
            resultName, property.name, property.localIsPresentName, property.localName)
      } else {
        result.addStatement("%1N.%2N = %3N ?: %1N.%2N",
            resultName, property.name, property.localName)
      }
    }

    if (hasNonConstructorProperties) {
      result.addStatement("return·%1N", resultName)
    }
    return result.build()
  }

  private fun unexpectedNull(identifier: String, reader: ParameterSpec): CodeBlock {
    return CodeBlock.of("%T.unexpectedNull(%S, %N)", Util::class, identifier, reader)
  }

  private fun generateToJsonFun(): FunSpec {
    val result = FunSpec.builder("toJson")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter(writerParam)
        .addParameter(valueParam)

    result.beginControlFlow("if (%N == null)", valueParam)
    result.addStatement("throw·%T(%S)", NullPointerException::class,
        "${valueParam.name} was null! Wrap in .nullSafe() to write nullable values.")
    result.endControlFlow()

    result.addStatement("%N.beginObject()", writerParam)
    nonTransientProperties.forEach { property ->
      result.addStatement("%N.name(%S)", writerParam, property.jsonName)
      result.addStatement("%N.toJson(%N, %N.%N)",
          nameAllocator[property.delegateKey], writerParam, valueParam, property.name)
    }
    result.addStatement("%N.endObject()", writerParam)

    return result.build()
  }
}
