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
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.CodeBlock.Companion
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.internal.Util
import com.squareup.moshi.kotlin.codegen.api.FromJsonComponent.ParameterOnly
import com.squareup.moshi.kotlin.codegen.api.FromJsonComponent.ParameterProperty
import com.squareup.moshi.kotlin.codegen.api.FromJsonComponent.PropertyOnly
import java.lang.reflect.Constructor
import java.lang.reflect.Type
import org.objectweb.asm.Type as AsmType

private val MOSHI_UTIL = Util::class.asClassName()
private const val TO_STRING_PREFIX = "GeneratedJsonAdapter("
private const val TO_STRING_SIZE_BASE = TO_STRING_PREFIX.length + 1 // 1 is the closing paren

/** Generates a JSON adapter for a target type. */
internal class AdapterGenerator(
    private val target: TargetType,
    private val propertyList: List<PropertyGenerator>
) {

  companion object {
    private val INT_TYPE_BLOCK = CodeBlock.of("%T::class.javaPrimitiveType", INT)
    private val DEFAULT_CONSTRUCTOR_MARKER_TYPE_BLOCK = CodeBlock.of(
        "%T.DEFAULT_CONSTRUCTOR_MARKER", Util::class)
    private val CN_MOSHI = Moshi::class.asClassName()
    private val CN_TYPE = Type::class.asClassName()

    private val COMMON_SUPPRESS = arrayOf(
        // https://github.com/square/moshi/issues/1023
        "DEPRECATION",
        // Because we look it up reflectively
        "unused",
        // Because we include underscores
        "ClassName",
        // Because we generate redundant `out` variance for some generics and there's no way
        // for us to know when it's redundant.
        "REDUNDANT_PROJECTION",
        // NameAllocator will just add underscores to differentiate names, which Kotlin doesn't
        // like for stylistic reasons.
        "LocalVariableName"
    ).let { suppressions ->
      AnnotationSpec.builder(Suppress::class)
          .addMember(
              suppressions.indices.joinToString { "%S" },
              *suppressions
          )
          .build()
    }
  }

  private val nonTransientProperties = propertyList.filterNot { it.isTransient }
  private val className = target.typeName.rawType()
  private val visibility = target.visibility
  private val typeVariables = target.typeVariables
  private val targetConstructorParams = target.constructor.parameters
      .mapKeys { (_, param) -> param.index }

  private val nameAllocator = NameAllocator()
  private val adapterName = "${className.simpleNames.joinToString(separator = "_")}JsonAdapter"
  private val originalTypeName = target.typeName.stripTypeVarVariance()
  private val originalRawTypeName = originalTypeName.rawType()

  private val moshiParam = ParameterSpec.builder(
      nameAllocator.newName("moshi"),
      CN_MOSHI).build()
  private val typesParam = ParameterSpec.builder(
      nameAllocator.newName("types"),
      ARRAY.parameterizedBy(CN_TYPE))
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
      .initializer(
          "%T.of(%L)",
          JsonReader.Options::class.asTypeName(),
          nonTransientProperties
              .map { CodeBlock.of("%S", it.jsonName) }
              .joinToCode(", ")
      )
      .build()

  private val constructorProperty = PropertySpec.builder(
      nameAllocator.newName("constructorRef"),
      Constructor::class.asClassName().parameterizedBy(originalTypeName).copy(nullable = true),
      KModifier.PRIVATE)
      .addAnnotation(Volatile::class)
      .mutable(true)
      .initializer("null")
      .build()

  fun prepare(typeHook: (TypeSpec) -> TypeSpec = { it }): PreparedAdapter {
    for (property in nonTransientProperties) {
      property.allocateNames(nameAllocator)
    }

    val generatedAdapter = generateType().let(typeHook)
    val result = FileSpec.builder(className.packageName, adapterName)
    result.addComment("Code generated by moshi-kotlin-codegen. Do not edit.")
    result.addType(generatedAdapter)
    return PreparedAdapter(result.build(), generatedAdapter.createProguardRule())
  }

  private fun TypeSpec.createProguardRule(): ProguardConfig {
    val adapterProperties = propertySpecs
        .asSequence()
        .filter { prop ->
          prop.type.rawType() == JsonAdapter::class.asClassName()
        }
        .filter { prop -> prop.annotations.isNotEmpty() }
        .mapTo(mutableSetOf()) { prop ->
          QualifierAdapterProperty(
              name = prop.name,
              qualifiers = prop.annotations.mapTo(mutableSetOf()) { it.className }
          )
        }

    val adapterConstructorParams = when (requireNotNull(primaryConstructor).parameters.size) {
      1 -> listOf(CN_MOSHI.reflectionName())
      2 -> listOf(CN_MOSHI.reflectionName(), "${CN_TYPE.reflectionName()}[]")
      // Should never happen
      else -> error("Unexpected number of arguments on primary constructor: $primaryConstructor")
    }

    var hasDefaultProperties = false
    var parameterTypes = emptyList<String>()
    target.constructor.signature?.let { constructorSignature ->
      if (constructorSignature.startsWith("constructor-impl")) {
        // Inline class, we don't support this yet.
        // This is a static method with signature like 'constructor-impl(I)I'
        return@let
      }
      hasDefaultProperties = propertyList.any { it.hasDefault }
      parameterTypes = AsmType.getArgumentTypes(constructorSignature.removePrefix("<init>"))
          .map { it.toReflectionString() }
    }
    return ProguardConfig(
        targetClass = className,
        adapterName = adapterName,
        adapterConstructorParams = adapterConstructorParams,
        targetConstructorHasDefaults = hasDefaultProperties,
        targetConstructorParams = parameterTypes,
        qualifierProperties = adapterProperties
    )
  }

  private fun generateType(): TypeSpec {
    val result = TypeSpec.classBuilder(adapterName)
        .addAnnotation(COMMON_SUPPRESS)

    result.superclass(jsonAdapterTypeName)

    if (typeVariables.isNotEmpty()) {
      result.addTypeVariables(typeVariables.map { it.stripTypeVarVariance() as TypeVariableName })
      // require(types.size == 1) {
      //   "TypeVariable mismatch: Expecting 1 type(s) for generic type variables [T], but received ${types.size} with values $types"
      // }
      result.addInitializerBlock(CodeBlock.builder()
          .beginControlFlow("require(types.size == %L)", typeVariables.size)
          .addStatement(
              "buildString·{·append(%S).append(%L).append(%S).append(%S).append(%S).append(%L)·}",
              "TypeVariable mismatch: Expecting ",
              typeVariables.size,
              " ${if (typeVariables.size == 1) "type" else "types"} for generic type variables [",
              typeVariables.joinToString(", ") { it.name },
              "], but received ",
              "${typesParam.name}.size"
          )
          .endControlFlow()
          .build())
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
    val name = originalRawTypeName.simpleNames.joinToString(".")
    val size = TO_STRING_SIZE_BASE + name.length
    return FunSpec.builder("toString")
        .addModifiers(KModifier.OVERRIDE)
        .returns(String::class)
        .addStatement(
            "return %M(%L)·{ append(%S).append(%S).append('%L') }",
            MemberName("kotlin.text", "buildString"),
            size,
            TO_STRING_PREFIX,
            name,
            ")"
        )
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

    val propertiesByIndex = propertyList.asSequence()
        .filter { it.hasConstructorParameter }
        .associateBy { it.target.parameterIndex }
    val components = mutableListOf<FromJsonComponent>()

    // Add parameters (± properties) first, their index matters
    for ((index, parameter) in targetConstructorParams) {
      val property = propertiesByIndex[index]
      if (property == null) {
        components += ParameterOnly(parameter)
      } else {
        components += ParameterProperty(parameter, property)
      }
    }

    // Now add the remaining properties that aren't parameters
    for (property in propertyList) {
      if (property.target.parameterIndex in targetConstructorParams) {
        continue // Already handled
      }
      if (property.isTransient) {
        continue // We don't care about these outside of constructor parameters
      }
      components += PropertyOnly(property)
    }

    // Calculate how many masks we'll need. Round up if it's not evenly divisible by 32
    val propertyCount = targetConstructorParams.size
    val maskCount = if (propertyCount == 0) {
      0
    } else {
      (propertyCount + 31) / 32
    }
    // Allocate mask names
    val maskNames = Array(maskCount) { index ->
      nameAllocator.newName("mask$index")
    }
    val useDefaultsConstructor = components.filterIsInstance<ParameterComponent>()
        .any { it.parameter.hasDefault }
    if (useDefaultsConstructor) {
      // Initialize all our masks, defaulting to fully unset (-1)
      for (maskName in maskNames) {
        result.addStatement("var %L = -1", maskName)
      }
    }
    result.addStatement("%N.beginObject()", readerParam)
    result.beginControlFlow("while (%N.hasNext())", readerParam)
    result.beginControlFlow("when (%N.selectName(%N))", readerParam, optionsProperty)

    // We track property index and mask index separately, because mask index is based on _all_
    // constructor arguments, while property index is only based on the index passed into
    // JsonReader.Options.
    var propertyIndex = 0
    val constructorPropertyTypes = mutableListOf<CodeBlock>()

    //
    // Track important indices for masks. Masks generally increment with each parameter (including
    // transient).
    //
    // Mask name index is an index into the maskNames array we initialized above.
    //
    // Once the maskIndex reaches 32, we've filled up that mask and have to move to the next mask
    // name. Reset the maskIndex relative to here and continue incrementing.
    //
    var maskIndex = 0
    var maskNameIndex = 0
    val updateMaskIndexes = {
      maskIndex++
      if (maskIndex == 32) {
        // Move to the next mask
        maskIndex = 0
        maskNameIndex++
      }
    }

    for (input in components) {
      if (input is ParameterOnly ||
          (input is ParameterProperty && input.property.isTransient)) {
        updateMaskIndexes()
        constructorPropertyTypes += input.type.asTypeBlock()
        continue
      } else if (input is PropertyOnly && input.property.isTransient) {
        continue
      }

      // We've removed all parameter-only types by this point
      val property = (input as PropertyComponent).property

      // Proceed as usual
      if (property.hasLocalIsPresentName || property.hasConstructorDefault) {
        result.beginControlFlow("%L ->", propertyIndex)
        if (property.delegateKey.nullable) {
          result.addStatement("%N = %N.fromJson(%N)",
              property.localName, nameAllocator[property.delegateKey], readerParam)
        } else {
          val exception = unexpectedNull(property, readerParam)
          result.addStatement("%N = %N.fromJson(%N) ?: throw·%L",
              property.localName, nameAllocator[property.delegateKey], readerParam, exception)
        }
        if (property.hasConstructorDefault) {
          val inverted = (1 shl maskIndex).inv()
          result.addComment("\$mask = \$mask and (1 shl %L).inv()", maskIndex)
          result.addStatement("%1L = %1L and 0x%2L.toInt()", maskNames[maskNameIndex],
              Integer.toHexString(inverted))
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
          val exception = unexpectedNull(property, readerParam)
          result.addStatement("%L -> %N = %N.fromJson(%N) ?: throw·%L",
              propertyIndex, property.localName, nameAllocator[property.delegateKey], readerParam,
              exception)
        }
      }
      if (property.hasConstructorParameter) {
        constructorPropertyTypes += property.target.type.asTypeBlock()
      }
      propertyIndex++
      updateMaskIndexes()
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
      val nonNullConstructorType = constructorProperty.type.copy(nullable = false)
      val args = constructorPropertyTypes
          .plus(0.until(maskCount).map { INT_TYPE_BLOCK }) // Masks, one every 32 params
          .plus(DEFAULT_CONSTRUCTOR_MARKER_TYPE_BLOCK) // Default constructor marker is always last
          .joinToCode(", ")
      val coreLookupBlock = CodeBlock.of(
          "%T::class.java.getDeclaredConstructor(%L)",
          originalRawTypeName,
          args
      )
      val lookupBlock = if (originalTypeName is ParameterizedTypeName) {
        CodeBlock.of("(%L·as·%T)", coreLookupBlock, nonNullConstructorType)
      } else {
        coreLookupBlock
      }
      val initializerBlock = CodeBlock.of(
          "this.%1N·?: %2L.also·{ this.%1N·= it }",
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
      // Standard constructor call. Can omit generics as they're inferred
      result.addCode("«%L%T(", returnOrResultAssignment, originalTypeName.rawType())
    }

    for (input in components.filterIsInstance<ParameterComponent>()) {
      result.addCode(separator)
      if (useDefaultsConstructor) {
        if (input is ParameterOnly || (input is ParameterProperty && input.property.isTransient)) {
          // We have to use the default primitive for the available type in order for
          // invokeDefaultConstructor to properly invoke it. Just using "null" isn't safe because
          // the transient type may be a primitive type.
          result.addCode(input.type.rawType().defaultPrimitiveValue())
        } else {
          result.addCode("%N", (input as ParameterProperty).property.localName)
        }
      } else if (input !is ParameterOnly) {
        val property = (input as ParameterProperty).property
        result.addCode("%N = %N", property.name, property.localName)
      }
      if (input is PropertyComponent) {
        val property = input.property
        if (!property.isTransient && property.isRequired) {
          val missingPropertyBlock =
              CodeBlock.of("%T.missingProperty(%S, %S, %N)",
                  MOSHI_UTIL, property.localName, property.jsonName, readerParam)
          result.addCode(" ?: throw·%L", missingPropertyBlock)
        }
      }
      separator = ",\n"
    }

    if (useDefaultsConstructor) {
      // Add the masks and a null instance for the trailing default marker instance
      result.addCode(",\n%L,\nnull", maskNames.map { CodeBlock.of("%L", it) }.joinToCode(", "))
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

  private fun unexpectedNull(property: PropertyGenerator, reader: ParameterSpec): CodeBlock {
    return CodeBlock.of("%T.unexpectedNull(%S, %S, %N)",
        MOSHI_UTIL, property.localName, property.jsonName, reader)
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
      // We manually put in quotes because we know the jsonName is already escaped
      result.addStatement("%N.name(%S)", writerParam, property.jsonName)
      result.addStatement("%N.toJson(%N, %N.%N)",
          nameAllocator[property.delegateKey], writerParam, valueParam, property.name)
    }
    result.addStatement("%N.endObject()", writerParam)

    return result.build()
  }
}

/** Represents a prepared adapter with its [spec] and optional associated [proguardConfig]. */
internal data class PreparedAdapter(val spec: FileSpec, val proguardConfig: ProguardConfig?)

private fun AsmType.toReflectionString(): String {
  return when (this) {
    AsmType.VOID_TYPE -> "void"
    AsmType.BOOLEAN_TYPE -> "boolean"
    AsmType.CHAR_TYPE -> "char"
    AsmType.BYTE_TYPE -> "byte"
    AsmType.SHORT_TYPE -> "short"
    AsmType.INT_TYPE -> "int"
    AsmType.FLOAT_TYPE -> "float"
    AsmType.LONG_TYPE -> "long"
    AsmType.DOUBLE_TYPE -> "double"
    else -> when (sort) {
      AsmType.ARRAY -> "${elementType.toReflectionString()}[]"
      // Object type
      else -> className
    }
  }
}

private interface PropertyComponent {
  val property: PropertyGenerator
  val type: TypeName
}

private interface ParameterComponent {
  val parameter: TargetParameter
  val type: TypeName
}

/**
 * Type hierarchy for describing fromJson() components. Specifically - parameters, properties, and
 * parameter properties. All three of these scenarios participate in fromJson() parsing.
 */
private sealed class FromJsonComponent {

  abstract val type: TypeName

  data class ParameterOnly(
      override val parameter: TargetParameter
  ) : FromJsonComponent(), ParameterComponent {
    override val type: TypeName = parameter.type
  }

  data class PropertyOnly(
      override val property: PropertyGenerator
  ) : FromJsonComponent(), PropertyComponent {
    override val type: TypeName = property.target.type
  }

  data class ParameterProperty(
      override val parameter: TargetParameter,
      override val property: PropertyGenerator
  ) : FromJsonComponent(), ParameterComponent, PropertyComponent {
    override val type: TypeName = parameter.type
  }
}
