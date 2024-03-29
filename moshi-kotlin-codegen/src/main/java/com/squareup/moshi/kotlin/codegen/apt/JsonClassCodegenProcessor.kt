/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.kotlin.codegen.apt

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.metadata.classinspectors.ElementsClassInspector
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.codegen.api.AdapterGenerator
import com.squareup.moshi.kotlin.codegen.api.Options.OPTION_GENERATED
import com.squareup.moshi.kotlin.codegen.api.Options.OPTION_GENERATE_PROGUARD_RULES
import com.squareup.moshi.kotlin.codegen.api.Options.OPTION_INSTANTIATE_ANNOTATIONS
import com.squareup.moshi.kotlin.codegen.api.Options.POSSIBLE_GENERATED_NAMES
import com.squareup.moshi.kotlin.codegen.api.ProguardConfig
import com.squareup.moshi.kotlin.codegen.api.PropertyGenerator
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * An annotation processor that reads Kotlin data classes and generates Moshi JsonAdapters for them.
 * This generates Kotlin code, and understands basic Kotlin language features like default values
 * and companion objects.
 *
 * The generated class will match the visibility of the given data class (i.e. if it's internal, the
 * adapter will also be internal).
 */
@AutoService(Processor::class)
public class JsonClassCodegenProcessor : AbstractProcessor() {

  private lateinit var types: Types
  private lateinit var elements: Elements
  private lateinit var filer: Filer
  private lateinit var messager: Messager
  private lateinit var cachedClassInspector: MoshiCachedClassInspector
  private val annotation = JsonClass::class.java
  private var generatedType: ClassName? = null
  private var generateProguardRules: Boolean = true
  private var instantiateAnnotations: Boolean = true

  override fun getSupportedAnnotationTypes(): Set<String> = setOf(annotation.canonicalName)

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

  override fun getSupportedOptions(): Set<String> = setOf(OPTION_GENERATED)

  override fun init(processingEnv: ProcessingEnvironment) {
    super.init(processingEnv)
    generatedType = processingEnv.options[OPTION_GENERATED]?.let {
      POSSIBLE_GENERATED_NAMES[it] ?: error(
        "Invalid option value for $OPTION_GENERATED. Found $it, " +
          "allowable values are $POSSIBLE_GENERATED_NAMES.",
      )
    }

    generateProguardRules = processingEnv.options[OPTION_GENERATE_PROGUARD_RULES]?.toBooleanStrictOrNull() ?: true
    instantiateAnnotations = processingEnv.options[OPTION_INSTANTIATE_ANNOTATIONS]?.toBooleanStrictOrNull() ?: true

    this.types = processingEnv.typeUtils
    this.elements = processingEnv.elementUtils
    this.filer = processingEnv.filer
    this.messager = processingEnv.messager
    cachedClassInspector = MoshiCachedClassInspector(ElementsClassInspector.create(elements, types))
  }

  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    if (roundEnv.errorRaised()) {
      // An error was raised in the previous round. Don't try anything for now to avoid adding
      // possible more noise.
      return false
    }
    for (type in roundEnv.getElementsAnnotatedWith(annotation)) {
      if (type !is TypeElement) {
        messager.printMessage(
          Diagnostic.Kind.ERROR,
          "@JsonClass can't be applied to $type: must be a Kotlin class",
          type,
        )
        continue
      }
      val jsonClass = type.getAnnotation(annotation)
      if (jsonClass.generateAdapter && jsonClass.generator.isEmpty()) {
        val generator = adapterGenerator(type, cachedClassInspector) ?: continue
        val preparedAdapter = generator
          .prepare(generateProguardRules) { spec ->
            spec.toBuilder()
              .apply {
                @Suppress("DEPRECATION") // This is a Java type
                generatedType?.let { generatedClassName ->
                  addAnnotation(
                    AnnotationSpec.builder(generatedClassName)
                      .addMember(
                        "value = [%S]",
                        JsonClassCodegenProcessor::class.java.canonicalName,
                      )
                      .addMember("comments = %S", "https://github.com/square/moshi")
                      .build(),
                  )
                }
              }
              .addOriginatingElement(type)
              .build()
          }

        preparedAdapter.spec.writeTo(filer)
        preparedAdapter.proguardConfig?.writeTo(filer, type)
      }
    }

    return false
  }

  private fun adapterGenerator(
    element: TypeElement,
    cachedClassInspector: MoshiCachedClassInspector,
  ): AdapterGenerator? {
    val type = targetType(
      messager,
      elements,
      types,
      element,
      cachedClassInspector,
    ) ?: return null

    val properties = mutableMapOf<String, PropertyGenerator>()
    for (property in type.properties.values) {
      val generator = property.generator(messager, element, elements)
      if (generator != null) {
        properties[property.name] = generator
      }
    }

    for ((name, parameter) in type.constructor.parameters) {
      if (type.properties[parameter.name] == null && !parameter.hasDefault) {
        messager.printMessage(
          Diagnostic.Kind.ERROR,
          "No property for required constructor parameter $name",
          element,
        )
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
}

/** Writes this config to a [filer]. */
private fun ProguardConfig.writeTo(filer: Filer, vararg originatingElements: Element) {
  filer.createResource(StandardLocation.CLASS_OUTPUT, "", "${outputFilePathWithoutExtension(targetClass.canonicalName)}.pro", *originatingElements)
    .openWriter()
    .use(::writeTo)
}
