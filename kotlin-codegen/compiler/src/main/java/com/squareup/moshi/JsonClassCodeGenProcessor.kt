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
import me.eugeniomarletti.kotlin.metadata.hasSetter
import me.eugeniomarletti.kotlin.metadata.isDataClass
import me.eugeniomarletti.kotlin.metadata.isInnerClass
import me.eugeniomarletti.kotlin.metadata.isPrimary
import me.eugeniomarletti.kotlin.metadata.jvm.getJvmConstructorSignature
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.modality
import me.eugeniomarletti.kotlin.metadata.visibility
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import org.jetbrains.kotlin.serialization.ProtoBuf.Class
import org.jetbrains.kotlin.serialization.ProtoBuf.Modality
import org.jetbrains.kotlin.serialization.ProtoBuf.Property
import org.jetbrains.kotlin.serialization.ProtoBuf.ValueParameter
import org.jetbrains.kotlin.serialization.ProtoBuf.Visibility
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
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

  private fun processElement(model: Element): AdapterGenerator? {
    val metadata = model.kotlinMetadata

    if (metadata !is KotlinClassMetadata) {
      messager.printMessage(
          ERROR, "@JsonClass can't be applied to $model: must be a Kotlin class", model)
      return null
    }

    val classData = metadata.data
    val (nameResolver, classProto) = classData

    when {
      classProto.classKind != Class.Kind.CLASS -> {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $model: must be a Kotlin class", model)
        return null
      }
      classProto.isInnerClass -> {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $model: must not be an inner class",
            model)
        return null
      }
      classProto.modality == Modality.ABSTRACT -> {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $model: must not be abstract", model)
        return null
      }
      classProto.visibility == Visibility.LOCAL -> {
        messager.printMessage(
            ERROR, "@JsonClass can't be applied to $model: must not be local", model)
        return null
      }
    }

    val typeName = model.asType().asTypeName()
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

    val annotationHolders = mutableMapOf<Property, ExecutableElement>()
    val fields = mutableMapOf<String, VariableElement>()
    val setters = mutableMapOf<String, ExecutableElement>()
    val getters = mutableMapOf<String, ExecutableElement>()
    for (element in model.enclosedElements) {
      if (element is VariableElement) {
        fields[element.name] = element
      } else if (element is ExecutableElement) {
        when {
          element.name.startsWith("get") -> {
            getters[element.name.substring("get".length).decapitalizeAsciiOnly()] = element
          }
          element.name.startsWith("is") -> {
            getters[element.name.substring("is".length).decapitalizeAsciiOnly()] = element
          }
          element.name.startsWith("set") -> {
            setters[element.name.substring("set".length).decapitalizeAsciiOnly()] = element
          }
        }

        val property = classData.getPropertyOrNull(element)
        if (property != null) {
          annotationHolders[property] = element
        }
      }
    }

    val propertiesByName = mutableMapOf<String, PropertyGenerator>()
    for (property in properties.values) {
      val name = nameResolver.getString(property.name)

      val fieldElement = fields[name]
      val setterElement = setters[name]
      val getterElement = getters[name]
      val element = fieldElement ?: setterElement ?: getterElement!!

      val parameter = parameters[name]
      var parameterIndex: Int = -1
      var parameterElement: VariableElement? = null
      if (parameter != null) {
        parameterIndex = protoConstructor.valueParameterList.indexOf(parameter)
        parameterElement = constructor.parameters[parameterIndex]
      }

      val annotationHolder = annotationHolders[property]

      if (property.visibility != Visibility.INTERNAL
          && property.visibility != Visibility.PROTECTED
          && property.visibility != Visibility.PUBLIC) {
        messager.printMessage(ERROR, "property $name is not visible", element)
        return null
      }

      val hasDefault = parameter?.declaresDefaultValue ?: true

      if (Modifier.TRANSIENT in element.modifiers) {
        if (!hasDefault) {
          messager.printMessage(
              ERROR, "No default value for transient property $name", element)
          return null
        }
        continue // This property is transient and has a default value. Ignore it.
      }

      if (!property.hasSetter && parameter == null) {
        continue // This property is not settable. Ignore it.
      }

      val delegateKey = DelegateKey(
          property.returnType.asTypeName(nameResolver, classProto::getTypeParameter, true),
          jsonQualifiers(element, annotationHolder, parameterElement))

      propertiesByName[name] = PropertyGenerator(
          delegateKey,
          name,
          jsonName(name, element, annotationHolder, parameterElement),
          parameterIndex,
          hasDefault,
          property.returnType.asTypeName(nameResolver, classProto::getTypeParameter))
    }

    for (parameterElement in constructor.parameters) {
      val name = parameterElement.name
      val valueParameter = parameters[name]!!
      if (properties[name] == null && !valueParameter.declaresDefaultValue) {
        messager.printMessage(
            ERROR, "No property for required constructor parameter $name", parameterElement)
        return null
      }
    }

    // Sort properties so that those with constructor parameters come first.
    val propertyGenerators = propertiesByName.values.toMutableList()
    propertyGenerators.sortBy {
      if (it.hasConstructorParameter) {
        it.parameterIndex
      } else {
        Integer.MAX_VALUE
      }
    }

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
        className = className,
        propertyList = propertyGenerators,
        originalElement = model,
        hasCompanionObject = hasCompanionObject,
        visibility = classProto.visibility!!,
        genericTypeNames = genericTypeNames,
        elements = elementUtils,
        isDataClass = classProto.isDataClass)
  }

  /** Returns the JsonQualifiers on the field and parameter of a property. */
  private fun jsonQualifiers(
    element: Element,
    annotationHolder: ExecutableElement?,
    parameter: VariableElement?
  ): Set<AnnotationMirror> {
    val elementQualifiers = element.qualifiers
    val annotationHolderQualifiers = annotationHolder.qualifiers
    val parameterQualifiers = parameter.qualifiers

    // TODO(jwilson): union the qualifiers somehow?
    return when {
      elementQualifiers.isNotEmpty() -> elementQualifiers
      annotationHolderQualifiers.isNotEmpty() -> annotationHolderQualifiers
      parameterQualifiers.isNotEmpty() -> parameterQualifiers
      else -> setOf()
    }
  }

  /** Returns the @Json name of a property, or `propertyName` if none is provided. */
  private fun jsonName(
    propertyName: String,
    element: Element,
    annotationHolder: ExecutableElement?,
    parameter: VariableElement?
  ): String {
    val fieldJsonName = element.jsonName
    val annotationHolderJsonName = annotationHolder.jsonName
    val parameterJsonName = parameter.jsonName

    return when {
      fieldJsonName != null -> fieldJsonName
      annotationHolderJsonName != null -> annotationHolderJsonName
      parameterJsonName != null -> parameterJsonName
      else -> propertyName
    }
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

private val Element.name: String
  get() {
    return simpleName.toString()
  }