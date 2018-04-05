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

import com.google.auto.common.AnnotationMirrors
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier.OUT
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadataUtils
import me.eugeniomarletti.kotlin.metadata.classKind
import me.eugeniomarletti.kotlin.metadata.declaresDefaultValue
import me.eugeniomarletti.kotlin.metadata.getPropertyOrNull
import me.eugeniomarletti.kotlin.metadata.isDataClass
import me.eugeniomarletti.kotlin.metadata.isPrimary
import me.eugeniomarletti.kotlin.metadata.jvm.getJvmConstructorSignature
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.visibility
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.ProtoBuf.Property
import org.jetbrains.kotlin.serialization.ProtoBuf.ValueParameter
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic.Kind.ERROR

/**
 * An annotation processor that reads Kotlin data classes and generates Moshi JsonAdapters for them.
 * This generates Kotlin code, and understands basic Kotlin language features like default values
 * and companion objects.
 *
 * The generated class will match the visibility of the given data class (i.e. if it's internal, the
 * adapter will also be internal).
 *
 * If you define a companion object, a jsonAdapter() extension function will be generated onto it.
 * If you don't want this though, you can use the runtime [JsonClass] factory implementation.
 */
@AutoService(Processor::class)
class JsonClassCodeGenProcessor : KotlinAbstractProcessor(), KotlinMetadataUtils {

  companion object {
    /**
     * This annotation processing argument can be specified to have a `@Generated` annotation
     * included in the generated code. It is not encouraged unless you need it for static analysis
     * reasons and not enabled by default.
     *
     * Note that this can only be one of the following values:
     *   * `"javax.annotation.processing.Generated"` (JRE 9+)
     *   * `"javax.annotation.Generated"` (JRE <9)
     */
    const val OPTION_GENERATED = "moshi.generated"
    private val POSSIBLE_GENERATED_NAMES = setOf(
        "javax.annotation.processing.Generated",
        "javax.annotation.Generated"
    )
  }

  private val annotation = JsonClass::class.java
  private var generatedType: TypeElement? = null

  override fun getSupportedAnnotationTypes() = setOf(annotation.canonicalName)

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

  override fun getSupportedOptions() = setOf(OPTION_GENERATED)

  override fun init(processingEnv: ProcessingEnvironment) {
    super.init(processingEnv)
    generatedType = processingEnv.options[OPTION_GENERATED]?.let {
      if (it !in POSSIBLE_GENERATED_NAMES) {
        throw IllegalArgumentException(
            "Invalid option value for $OPTION_GENERATED. Found $it, allowable values are $POSSIBLE_GENERATED_NAMES.")
      }
      processingEnv.elementUtils.getTypeElement(it)
    }
  }

  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    for (type in roundEnv.getElementsAnnotatedWith(annotation)) {
      val jsonClass = type.getAnnotation(annotation)
      if (jsonClass.generateAdapter) {
        val adapterGenerator = processElement(type) ?: continue
        adapterGenerator.generateAndWrite(generatedType)
      }
    }

    return true
  }

  private fun processElement(element: Element): AdapterGenerator? {
    val metadata = element.kotlinMetadata

    if (metadata !is KotlinClassMetadata) {
      errorMustBeKotlinClass(element)
      return null
    }

    val classData = metadata.data
    val (nameResolver, classProto) = classData

    if (classProto.classKind != ProtoBuf.Class.Kind.CLASS) {
      errorMustBeKotlinClass(element)
      return null
    }

    val typeName = element.asType().asTypeName()
    val className = when (typeName) {
      is ClassName -> typeName
      is ParameterizedTypeName -> typeName.rawType
      else -> throw IllegalStateException("unexpected TypeName: ${typeName::class}")
    }

    val hasCompanionObject = classProto.hasCompanionObjectName()
    // todo allow custom constructor
    val protoConstructor = classProto.constructorList
        .single { it.isPrimary }
    val constructorJvmSignature = protoConstructor.getJvmConstructorSignature(nameResolver,
        classProto.typeTable)
    val constructor = classProto.fqName
        .let(nameResolver::getString)
        .replace('/', '.')
        .let(elementUtils::getTypeElement)
        .enclosedElements
        .mapNotNull {
          it.takeIf { it.kind == ElementKind.CONSTRUCTOR }?.let { it as ExecutableElement }
        }
        .first()
    // TODO Temporary until jvm method signature matching is better
    //  .single { it.jvmMethodSignature == constructorJvmSignature }
    val parameters: Map<String, ValueParameter> = protoConstructor.valueParameterList.associateBy {
      nameResolver.getString(it.name)
    }

    val properties = classData.classProto.propertyList.associateBy {
      nameResolver.getString(it.name)
    }

    // The compiler might emit methods just so it has a place to put annotations. Find these.
    val annotatedElements = mutableMapOf<Property, ExecutableElement>()
    for (enclosedElement in element.enclosedElements) {
      if (enclosedElement !is ExecutableElement) continue
      val property = classData.getPropertyOrNull(enclosedElement) ?: continue
      annotatedElements[property] = enclosedElement
    }

    val propertyGenerators = mutableListOf<PropertyGenerator>()
    for (enclosedElement in element.enclosedElements) {
      if (enclosedElement !is VariableElement) continue

      val name = enclosedElement.simpleName.toString()
      val property = properties[name] ?: continue
      val parameter = parameters[name]

      val parameterElement = if (parameter != null) {
        val parameterIndex = protoConstructor.valueParameterList.indexOf(parameter)
        constructor.parameters[parameterIndex]
      } else {
        null
      }

      val annotatedElement = annotatedElements[property]

      if (property.visibility != ProtoBuf.Visibility.INTERNAL
          && property.visibility != ProtoBuf.Visibility.PROTECTED
          && property.visibility != ProtoBuf.Visibility.PUBLIC) {
        messager.printMessage(ERROR, "property $name is not visible", enclosedElement)
        return null
      }

      val hasDefault = parameter?.declaresDefaultValue ?: true

      if (Modifier.TRANSIENT in enclosedElement.modifiers) {
        if (!hasDefault) {
          throw IllegalArgumentException("No default value for transient property $name")
        }
        continue
      }

      propertyGenerators += PropertyGenerator(
          name,
          jsonName(name, enclosedElement, annotatedElement, parameterElement),
          parameter != null,
          hasDefault,
          property.returnType.nullable,
          property.returnType.asTypeName(nameResolver, classProto::getTypeParameter),
          property.returnType.asTypeName(nameResolver, classProto::getTypeParameter, true),
          jsonQualifiers(enclosedElement, annotatedElement, parameterElement))
    }

    // Sort properties so that those with constructor parameters come first.
    propertyGenerators.sortBy { if (it.hasConstructorParameter) -1 else 1 }

    val genericTypeNames = classProto.typeParameterList
        .map {
          val variance = it.variance.asKModifier().let {
            // We don't redeclare out variance here
            if (it == OUT) {
              null
            } else {
              it
            }
          }
          TypeVariableName(
              name = nameResolver.getString(it.name),
              bounds = *(it.upperBoundList
                  .map { it.asTypeName(nameResolver, classProto::getTypeParameter) }
                  .toTypedArray()),
              variance = variance)
              .reified(it.reified)
        }.let {
          if (it.isEmpty()) {
            null
          } else {
            it
          }
        }

    return AdapterGenerator(
        className,
        propertyList = propertyGenerators,
        originalElement = element,
        hasCompanionObject = hasCompanionObject,
        visibility = classProto.visibility!!,
        genericTypeNames = genericTypeNames,
        elements = elementUtils,
        isDataClass = classProto.isDataClass)
  }

  /** Returns the JsonQualifiers on the field and parameter of a property. */
  private fun jsonQualifiers(
    field: VariableElement,
    method: ExecutableElement?,
    parameter: VariableElement?
  ): Set<AnnotationMirror> {
    val fieldQualifiers = field.qualifiers
    val methodQualifiers = method.qualifiers
    val parameterQualifiers = parameter.qualifiers

    // TODO(jwilson): union the qualifiers somehow?
    return when {
      fieldQualifiers.isNotEmpty() -> fieldQualifiers
      methodQualifiers.isNotEmpty() -> methodQualifiers
      parameterQualifiers.isNotEmpty() -> parameterQualifiers
      else -> setOf()
    }
  }

  /** Returns the @Json name of a property, or `propertyName` if none is provided. */
  private fun jsonName(
    propertyName: String,
    field: VariableElement,
    method: ExecutableElement?,
    parameter: VariableElement?
  ): String {
    val fieldJsonName = field.jsonName
    val methodJsonName = method.jsonName
    val parameterJsonName = parameter.jsonName

    return when {
      fieldJsonName != null -> fieldJsonName
      methodJsonName != null -> methodJsonName
      parameterJsonName != null -> parameterJsonName
      else -> propertyName
    }
  }

  private fun errorMustBeKotlinClass(element: Element) {
    messager.printMessage(ERROR,
        "@${JsonClass::class.java.simpleName} can't be applied to $element: must be a Kotlin class",
        element)
  }

  private fun AdapterGenerator.generateAndWrite(generatedOption: TypeElement?) {
    val fileSpec = generateFile(generatedOption)
    val adapterName = fileSpec.members.filterIsInstance<TypeSpec>().first().name!!
    val outputDir = generatedDir ?: mavenGeneratedDir(adapterName)
    fileSpec.writeTo(outputDir)
  }

  private fun mavenGeneratedDir(adapterName: String): File {
    // Hack since the maven plugin doesn't supply `kapt.kotlin.generated` option
    // Bug filed at https://youtrack.jetbrains.com/issue/KT-22783
    val file = filer.createSourceFile(adapterName).toUri().let(::File)
    return file.parentFile.also { file.delete() }
  }

  private val Element?.qualifiers: Set<AnnotationMirror>
    get() {
      if (this == null) return setOf()
      return AnnotationMirrors.getAnnotatedAnnotations(this, JsonQualifier::class.java)
    }

  private val Element?.jsonName: String?
    get() {
      if (this == null) return null
      return getAnnotation(Json::class.java)?.name
    }
}
