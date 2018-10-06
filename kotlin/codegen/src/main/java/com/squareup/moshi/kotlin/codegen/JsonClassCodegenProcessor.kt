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

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.moshi.JsonClass
import me.eugeniomarletti.kotlin.metadata.KotlinMetadataUtils
import me.eugeniomarletti.kotlin.metadata.declaresDefaultValue
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
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
class JsonClassCodegenProcessor : KotlinAbstractProcessor(), KotlinMetadataUtils {

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
        throw IllegalArgumentException("Invalid option value for $OPTION_GENERATED. Found $it, " +
            "allowable values are $POSSIBLE_GENERATED_NAMES.")
      }
      processingEnv.elementUtils.getTypeElement(it)
    }
  }

  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    for (type in roundEnv.getElementsAnnotatedWith(annotation)) {
      val jsonClass = type.getAnnotation(annotation)
      if (jsonClass.generateAdapter) {
        val generator = adapterGenerator(type) ?: continue
        generator.generateAndWrite(generatedType)
      }
    }

    return false
  }

  private fun adapterGenerator(element: Element): AdapterGenerator? {
    val type = TargetType.get(messager, elementUtils, typeUtils, element) ?: return null

    val properties = mutableMapOf<String, PropertyGenerator>()
    for (property in type.properties.values) {
      val generator = property.generator(messager)
      if (generator != null) {
        properties[property.name] = generator
      }
    }

    for ((name, parameter) in type.constructor.parameters) {
      if (type.properties[parameter.name] == null && !parameter.proto.declaresDefaultValue) {
        messager.printMessage(
            ERROR, "No property for required constructor parameter $name", parameter.element)
        return null
      }
    }

    // Sort properties so that those with constructor parameters come first.
    val sortedProperties = properties.values.sortedBy {
      if (it.hasConstructorParameter) {
        it.target.parameterIndex
      } else {
        Integer.MAX_VALUE
      }
    }

    return AdapterGenerator(type, sortedProperties)
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
}
