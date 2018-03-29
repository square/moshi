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
import com.squareup.kotlinpoet.KModifier.OUT
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadataUtils
import me.eugeniomarletti.kotlin.metadata.declaresDefaultValue
import me.eugeniomarletti.kotlin.metadata.extractFullName
import me.eugeniomarletti.kotlin.metadata.isDataClass
import me.eugeniomarletti.kotlin.metadata.isPrimary
import me.eugeniomarletti.kotlin.metadata.jvm.getJvmConstructorSignature
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.visibility
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import org.jetbrains.kotlin.serialization.ProtoBuf
import java.io.File
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
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

  private val annotation = JsonClass::class.java

  override fun getSupportedAnnotationTypes() = setOf(annotation.canonicalName)

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    for (type in roundEnv.getElementsAnnotatedWith(annotation)) {
      val jsonClass = type.getAnnotation(annotation)
      if (jsonClass.generateAdapter) {
        val adapterGenerator = processElement(type) ?: continue
        adapterGenerator.generateAndWrite()
      }
    }

    return true
  }

  private fun processElement(element: Element): AdapterGenerator? {
    val metadata = element.kotlinMetadata

    if (metadata !is KotlinClassMetadata) {
      errorMustBeDataClass(element)
      return null
    }

    val classData = metadata.data
    val (nameResolver, classProto) = classData

    fun ProtoBuf.Type.extractFullName() = extractFullName(classData)

    if (!classProto.isDataClass) {
      errorMustBeDataClass(element)
      return null
    }

    val fqClassName = nameResolver.getString(classProto.fqName).replace('/', '.')

    val packageName = nameResolver.getString(classProto.fqName).substringBeforeLast('/').replace(
        '/', '.')

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
    val parameters = protoConstructor
        .valueParameterList
        .mapIndexed { index, valueParameter ->
          val paramName = nameResolver.getString(valueParameter.name)

          val nullable = valueParameter.type.nullable
          val paramFqcn = valueParameter.type.extractFullName()
              .replace("`", "")
              .removeSuffix("?")

          val actualElement = constructor.parameters[index]

          val serializedName = actualElement.getAnnotation(Json::class.java)?.name
              ?: paramName

          val jsonQualifiers = AnnotationMirrors.getAnnotatedAnnotations(actualElement,
              JsonQualifier::class.java)

          PropertyGenerator(
              name = paramName,
              serializedName = serializedName,
              hasDefault = valueParameter.declaresDefaultValue,
              nullable = nullable,
              typeName = valueParameter.type.asTypeName(nameResolver, classProto::getTypeParameter),
              unaliasedName = valueParameter.type.asTypeName(nameResolver,
                  classProto::getTypeParameter, true),
              jsonQualifiers = jsonQualifiers)
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
        fqClassName = fqClassName,
        packageName = packageName,
        propertyList = parameters,
        originalElement = element,
        hasCompanionObject = hasCompanionObject,
        visibility = classProto.visibility!!,
        genericTypeNames = genericTypeNames,
        elements = elementUtils)
  }

  private fun errorMustBeDataClass(element: Element) {
    messager.printMessage(ERROR,
        "@${JsonClass::class.java.simpleName} can't be applied to $element: must be a Kotlin data class",
        element)
  }

  private fun AdapterGenerator.generateAndWrite() {
    val fileSpec = generateFile()
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
}

