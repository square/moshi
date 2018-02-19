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
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.IN
import com.squareup.kotlinpoet.KModifier.OUT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
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
import org.jetbrains.kotlin.serialization.ProtoBuf.Type.Argument.Projection
import org.jetbrains.kotlin.serialization.ProtoBuf.TypeParameter.Variance
import org.jetbrains.kotlin.serialization.ProtoBuf.Visibility
import org.jetbrains.kotlin.serialization.ProtoBuf.Visibility.INTERNAL
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import java.io.File
import java.lang.reflect.Type
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
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
 * If you don't want this though, you can use the runtime [MoshiSerializable] factory implementation.
 */
@AutoService(Processor::class)
class MoshiKotlinCodeGenProcessor : KotlinAbstractProcessor(), KotlinMetadataUtils {

  private val annotationName = MoshiSerializable::class.java.canonicalName

  override fun getSupportedAnnotationTypes() = setOf(annotationName)

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val annotationElement = elementUtils.getTypeElement(annotationName)
    return if (roundEnv.getElementsAnnotatedWith(annotationElement)
            .asSequence()
            .mapNotNull { processElement(it) }
            .any { !it.generateAndWrite() }) true else true
  }

  private fun processElement(element: Element): Adapter? {
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
        .mapNotNull { it.takeIf { it.kind == ElementKind.CONSTRUCTOR }?.let { it as ExecutableElement } }
        .first()
    // TODO Temporary until jvm method signature matching is better
//        .single { it.jvmMethodSignature == constructorJvmSignature }
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

          Property(
              name = paramName,
              fqClassName = paramFqcn,
              serializedName = serializedName,
              hasDefault = valueParameter.declaresDefaultValue,
              nullable = nullable,
              typeName = valueParameter.type.asTypeName(nameResolver, classProto::getTypeParameter),
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
          TypeVariableName.invoke(
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

    return Adapter(
        fqClassName = fqClassName,
        packageName = packageName,
        propertyList = parameters,
        originalElement = element,
        hasCompanionObject = hasCompanionObject,
        visibility = classProto.visibility!!,
        genericTypeNames = genericTypeNames,
        elementUtils = elementUtils)
  }

  private fun errorMustBeDataClass(element: Element) {
    messager.printMessage(ERROR,
        "@${MoshiSerializable::class.java.simpleName} can't be applied to $element: must be a Kotlin data class",
        element)
  }

  private fun Adapter.generateAndWrite(): Boolean {
    val adapterName = "${name}JsonAdapter"
    val outputDir = generatedDir ?: mavenGeneratedDir(adapterName)
    val fileBuilder = FileSpec.builder(packageName, adapterName)
    generate(adapterName, fileBuilder)
    fileBuilder
        .build()
        .writeTo(outputDir)
    return true
  }

  private fun mavenGeneratedDir(adapterName: String): File {
    // Hack since the maven plugin doesn't supply `kapt.kotlin.generated` option
    // Bug filed at https://youtrack.jetbrains.com/issue/KT-22783
    val file = filer.createSourceFile(adapterName).toUri().let(::File)
    return file.parentFile.also { file.delete() }
  }
}

/**
 * Creates a joined string representation of simplified typename names.
 */
private fun List<TypeName>.simplifiedNames(): String {
  return joinToString("_") { it.simplifiedName() }
}

private fun TypeName.resolveRawType(): ClassName {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> rawType
    else -> throw UnsupportedOperationException("Cannot get raw type from $this")
  }
}

/**
 * Creates a simplified string representation of a TypeName's name
 */
private fun TypeName.simplifiedName(): String {
  return when (this) {
    is ClassName -> simpleName().decapitalize()
    is ParameterizedTypeName -> {
      rawType.simpleName().decapitalize() + if (typeArguments.isEmpty()) "" else "__" + typeArguments.simplifiedNames()
    }
    is WildcardTypeName -> "wildcard__" + (lowerBounds + upperBounds).simplifiedNames()
    is TypeVariableName -> name.decapitalize() + if (bounds.isEmpty()) "" else "__" + bounds.simplifiedNames()
  // Shouldn't happen
    else -> toString().decapitalize()
  }.let { if (nullable) "${it}_nullable" else it }
}

private fun ClassName.isClass(elementUtils: Elements): Boolean {
  val fqcn = toString()
  if (fqcn.startsWith("kotlin.collections.")) {
    // These are special kotlin interfaces are only visible in kotlin, because they're replaced by
    // the compiler with concrete java classes
    return false
  } else if (this == ARRAY) {
    // This is a "fake" class and not visible to Elements
    return true
  }
  return elementUtils.getTypeElement(fqcn).kind == ElementKind.INTERFACE
}

private fun TypeName.objectType(): TypeName {
  return when (this) {
    BOOLEAN -> Boolean::class.javaObjectType.asTypeName()
    BYTE -> Byte::class.javaObjectType.asTypeName()
    SHORT -> Short::class.javaObjectType.asTypeName()
    INT -> Integer::class.javaObjectType.asTypeName()
    LONG -> Long::class.javaObjectType.asTypeName()
    CHAR -> Character::class.javaObjectType.asTypeName()
    FLOAT -> Float::class.javaObjectType.asTypeName()
    DOUBLE -> Double::class.javaObjectType.asTypeName()
    else -> this
  }
}

private fun TypeName.makeType(
    elementUtils: Elements,
    typesArray: ParameterSpec,
    genericTypeNames: List<TypeVariableName>): CodeBlock {
  if (nullable) {
    return asNonNullable().makeType(elementUtils, typesArray, genericTypeNames)
  }
  return when (this) {
    is ClassName -> CodeBlock.of("%T::class.java", this)
    is ParameterizedTypeName -> {
      // If it's an Array type, we shortcut this to return Types.arrayOf()
      if (rawType == ARRAY) {
        return CodeBlock.of("%T.arrayOf(%L)",
            Types::class.asTypeName(),
            typeArguments[0].objectType().makeType(elementUtils, typesArray, genericTypeNames))
      }
      // If it's a Class type, we have to specify the generics.
      val rawTypeParameters = if (rawType.isClass(elementUtils)) {
        CodeBlock.of(
            typeArguments.joinTo(
                buffer = StringBuilder(),
                separator = ", ",
                prefix = "<",
                postfix = ">") { "%T" }
                .toString(),
            *(typeArguments.map { objectType() }.toTypedArray())
        )
      } else {
        CodeBlock.of("")
      }
      CodeBlock.of(
          "%T.newParameterizedType(%T%L::class.java, ${typeArguments
              .joinToString(", ") { "%L" }})",
          Types::class.asTypeName(),
          rawType.objectType(),
          rawTypeParameters,
          *(typeArguments.map {
            it.objectType().makeType(elementUtils, typesArray, genericTypeNames)
          }.toTypedArray()))
    }
    is WildcardTypeName -> {
      val target: TypeName
      val method: String
      when {
        lowerBounds.size == 1 -> {
          target = lowerBounds[0]
          method = "supertypeOf"
        }
        upperBounds.size == 1 -> {
          target = upperBounds[0]
          method = "subtypeOf"
        }
        else -> throw IllegalArgumentException(
            "Unrepresentable wildcard type. Cannot have more than one bound: " + this)
      }
      CodeBlock.of("%T.%L(%T::class.java)", Types::class.asTypeName(), method, target)
    }
    is TypeVariableName -> {
      CodeBlock.of("%N[%L]", typesArray, genericTypeNames.indexOfFirst { it == this })
    }
  // Shouldn't happen
    else -> throw IllegalArgumentException("Unrepresentable type: " + this)
  }
}

private data class Property(
    val name: String,
    val fqClassName: String,
    val serializedName: String,
    val hasDefault: Boolean,
    val nullable: Boolean,
    val typeName: TypeName,
    val jsonQualifiers: Set<AnnotationMirror>) {

  val isRequired = !nullable && !hasDefault
}

private data class Adapter(
    val fqClassName: String,
    val packageName: String,
    val propertyList: List<Property>,
    val originalElement: Element,
    val name: String = fqClassName.substringAfter(packageName)
        .replace('.', '_')
        .removePrefix("_"),
    val hasCompanionObject: Boolean,
    val visibility: Visibility,
    val elementUtils: Elements,
    val genericTypeNames: List<TypeVariableName>?) {

  fun generate(adapterName: String, fileSpecBuilder: FileSpec.Builder) {
    val nameAllocator = NameAllocator()
    fun String.allocate() = nameAllocator.newName(this)

    val originalTypeName = originalElement.asType().asTypeName()
    val moshiName = "moshi".allocate()
    val moshiParam = ParameterSpec.builder(moshiName, Moshi::class.asClassName()).build()
    val typesParam = ParameterSpec.builder("types".allocate(),
        ParameterizedTypeName.get(ARRAY, Type::class.asTypeName())).build()
    val reader = ParameterSpec.builder("reader".allocate(),
        JsonReader::class.asClassName()).build()
    val writer = ParameterSpec.builder("writer".allocate(),
        JsonWriter::class.asClassName()).build()
    val value = ParameterSpec.builder("value".allocate(),
        originalTypeName.asNullable()).build()
    val jsonAdapterTypeName = ParameterizedTypeName.get(JsonAdapter::class.asClassName(),
        originalTypeName)

    // Create fields
    val adapterProperties = propertyList
        .distinctBy { it.typeName to it.jsonQualifiers }
        .associate { prop ->
          val typeName = prop.typeName
          val qualifierNames = prop.jsonQualifiers.joinToString("_") {
            it.annotationType.asElement().simpleName.toString()
          }
          val propertyName = "${typeName.simplifiedName().allocate()}_Adapter".let {
            if (qualifierNames.isBlank()) {
              it
            } else {
              "${it}_for_$qualifierNames"
            }
          }.let { "${it}_Adapter" }
          val adapterTypeName = ParameterizedTypeName.get(JsonAdapter::class.asTypeName(), typeName)
          val key = typeName to prop.jsonQualifiers
          return@associate key to PropertySpec.builder(propertyName, adapterTypeName, PRIVATE)
              .apply {
                val qualifiers = prop.jsonQualifiers.toList()
                val standardArgs = arrayOf(moshiParam,
                    if (typeName is ClassName && qualifiers.isEmpty()) {
                      ""
                    } else {
                      CodeBlock.of("<%T>",
                          typeName)
                    },
                    typeName.makeType(elementUtils, typesParam, genericTypeNames ?: emptyList()))
                val standardArgsSize = standardArgs.size + 1
                val (initializerString, args) = when {
                  qualifiers.isEmpty() -> "" to emptyArray()
                  qualifiers.size == 1 -> {
                    ", %${standardArgsSize}T::class.java" to arrayOf(
                        qualifiers.first().annotationType.asTypeName())
                  }
                  else -> {
                    val initString = qualifiers
                        .mapIndexed { index, _ ->
                          val annoClassIndex = standardArgsSize + index
                          return@mapIndexed "%${annoClassIndex}T::class.java"
                        }
                        .joinToString()
                    val initArgs = qualifiers
                        .map { it.annotationType.asTypeName() }
                        .toTypedArray()
                    ", $initString" to initArgs
                  }
                }
                val finalArgs = arrayOf(*standardArgs, *args)
                try {
                  initializer("%1N.adapter%2L(%3L$initializerString).nullSafe()", *finalArgs)
                } catch (e: IllegalArgumentException) {
                  throw RuntimeException(
                      "$e " + "InitString is " + "%1N.adapter%2L(%3L$initializerString).nullSafe()" + " and args are " + finalArgs.joinToString())
                }
              }
              .build()
        }

    val localProperties =
        propertyList.associate { prop ->
          prop to PropertySpec.builder(prop.name.allocate(), prop.typeName.asNullable())
              .mutable(true)
              .initializer("null")
              .build()
        }
    val optionsByIndex = propertyList
        .associateBy { it.serializedName }.entries.withIndex()

    // selectName() API setup
    val namesArray = PropertySpec.builder("NAMES".allocate(),
        ParameterizedTypeName.get(ARRAY, String::class.asTypeName()), PRIVATE)
        .initializer("arrayOf(${optionsByIndex.map { it.value.key }
            .joinToString(", ") { "\"$it\"" }})")
        .build()
    val optionsCN = JsonReader.Options::class.asTypeName()
    val optionsProperty = PropertySpec.builder(
        "OPTIONS".allocate(),
        optionsCN,
        PRIVATE)
        .initializer("%T.of(*%N)",
            optionsCN,
            namesArray)
        .build()
    val companionObject = TypeSpec.companionObjectBuilder("SelectOptions")
        .addModifiers(PRIVATE)
        .addProperty(namesArray)
        .addProperty(optionsProperty)
        .build()

    val adapter = TypeSpec.classBuilder(adapterName)
        .superclass(jsonAdapterTypeName)
        .apply {
          genericTypeNames?.let {
            addTypeVariables(genericTypeNames)
          }
        }
        .apply {
          // TODO make this configurable. Right now it just matches the source model
          if (visibility == INTERNAL) {
            addModifiers(KModifier.INTERNAL)
          }
        }
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter(moshiParam)
            .apply {
              genericTypeNames?.let {
                addParameter(typesParam)
              }
            }
            .build())
        .addType(companionObject)
        .addProperties(adapterProperties.values)
        .addFunction(FunSpec.builder("toString")
            .addModifiers(OVERRIDE)
            .returns(String::class)
            .addStatement("return %S",
                "GeneratedJsonAdapter(${originalTypeName.resolveRawType()
                    .simpleNames()
                    .joinToString(".")})")
            .build())
        .addFunction(FunSpec.builder("fromJson")
            .addModifiers(OVERRIDE)
            .addParameter(reader)
            .returns(originalTypeName.asNullable())
            .apply {
              localProperties.values.forEach {
                addCode("%L", it)
              }
            }
            .addStatement("%N.beginObject()", reader)
            .beginControlFlow("while (%N.hasNext())", reader)
            .beginControlFlow("when (%N.selectName(%N))", reader, optionsProperty)
            .apply {
              optionsByIndex.map { (index, entry) -> index to entry.value }
                  .forEach { (index, prop) ->
                    val spec = localProperties[prop]!!
                    addStatement("%L -> %N = %N.fromJson(%N)",
                        index,
                        spec,
                        adapterProperties[prop.typeName to prop.jsonQualifiers]!!,
                        reader)
                  }
            }
            .beginControlFlow("-1 ->")
            .addCode("// Unknown name, skip it\n")
            .addStatement("%N.nextName()", reader)
            .addStatement("%N.skipValue()", reader)
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
            .addStatement("%N.endObject()", reader)
            .apply {
              if (localProperties.keys.any { it.isRequired }) {
                val indexParam = ParameterSpec.builder("index".allocate(), INT)
                    .build()
                val lambdaType = LambdaTypeName.get(
                    returnType = JsonDataException::class.asTypeName()
                )
                val missingLambda = PropertySpec.builder("missingArguments".allocate(), lambdaType)
                    .initializer(CodeBlock.builder()
                        .add("{\n")
                        .add("%N.filterIndexed { %N, _ ->", namesArray, indexParam)
                        .add("%>")
                        // Begin controlflow
                        .add("\nwhen (%N) {", indexParam)
                        .add("%>")
                        .apply {
                          optionsByIndex
                              .map { (index, entry) -> index to entry.value }
                              .filter { (_, prop) -> prop.isRequired }
                              .forEach { (index, prop) ->
                                add("\n%L -> %N == null",
                                    index,
                                    localProperties[prop]!!)
                              }
                        }
                        .add("\nelse -> false")
                        // End controlflow
                        .add("%<")
                        .add("\n}")
                        .add("%<")
                        .add("\n}")
                        .add(
                            ".let { %T(\"The following required properties were missing: \${%L}\") }",
                            JsonDataException::class,
                            CodeBlock.of("it.joinToString()"))
                        .add("\n}")
                        .build())
                    .build()
                addCode("%L", missingLambda)
                localProperties.forEach { (property, spec) ->
                  if (property.isRequired) {
                    beginControlFlow("if (%N == null)", spec)
                    addStatement("throw %N()", missingLambda)
                    endControlFlow()
                  }
                }
              }
              val propertiesWithDefaults = localProperties.entries.filter { it.key.hasDefault }
              if (propertiesWithDefaults.isEmpty()) {
                addStatement("return %T(%L)",
                    originalTypeName,
                    localProperties.entries.joinToString(",\n") { (property, spec) ->
                      "${property.name} = ${spec.name}"
                    })
              } else {
                addStatement("return %T(%L).let {\n  it.copy(%L)\n}",
                    originalTypeName,
                    localProperties.entries
                        .filter { !it.key.hasDefault }
                        .joinToString(",\n") { (property, spec) ->
                          "${property.name} = ${spec.name}"
                        },
                    propertiesWithDefaults
                        .joinToString(",\n      ") { (property, spec) ->
                          "${property.name} = ${spec.name} ?: it.${property.name}"
                        })
              }
            }
            .build())
        .addFunction(FunSpec.builder("toJson")
            .addModifiers(OVERRIDE)
            .addParameter(writer)
            .addParameter(value)
            .addStatement("%N.beginObject()", writer)
            .apply {
              propertyList.forEachIndexed { index, prop ->
                addStatement("%N.name(%S)",
                    writer,
                    prop.serializedName)
                addStatement("%N.toJson(%N, %N${if (index == 0) "!!" else ""}.%L)",
                    adapterProperties[prop.typeName to prop.jsonQualifiers]!!,
                    writer,
                    value,
                    prop.name)
              }
            }
            .addStatement("%N.endObject()", writer)
            .build())
        .build()

    if (hasCompanionObject) {
      val rawType = when (originalTypeName) {
        is TypeVariableName -> throw IllegalArgumentException(
            "Cannot get raw type of TypeVariable!")
        is ParameterizedTypeName -> originalTypeName.rawType
        else -> originalTypeName as ClassName
      }
      fileSpecBuilder.addFunction(FunSpec.builder("jsonAdapter")
          .apply {
            // TODO make this configurable. Right now it just matches the source model
            if (visibility == INTERNAL) {
              addModifiers(KModifier.INTERNAL)
            }
          }
          .receiver(rawType.nestedClass("Companion"))
          .returns(jsonAdapterTypeName)
          .addParameter(moshiParam)
          .apply {
            genericTypeNames?.let {
              addParameter(typesParam)
              addTypeVariables(it)
            }
          }
          .apply {
            if (genericTypeNames != null) {
              addStatement("return %N(%N, %N)", adapter, moshiParam, typesParam)
            } else {
              addStatement("return %N(%N)", adapter, moshiParam)
            }
          }
          .build())
    }
    fileSpecBuilder.addType(adapter)
  }
}

private fun ProtoBuf.TypeParameter.asTypeName(
    nameResolver: NameResolver,
    getTypeParameter: (index: Int) -> ProtoBuf.TypeParameter): TypeName {
  return TypeVariableName(
      name = nameResolver.getString(name),
      bounds = *(upperBoundList.map { it.asTypeName(nameResolver, getTypeParameter) }
          .toTypedArray()),
      variance = variance.asKModifier()
  )
}

private fun ProtoBuf.TypeParameter.Variance.asKModifier(): KModifier? {
  return when (this) {
    Variance.IN -> IN
    Variance.OUT -> OUT
    Variance.INV -> null
  }
}

/**
 * Returns the TypeName of this type as it would be seen in the source code,
 * including nullability and generic type parameters.
 *
 * @param [nameResolver] a [NameResolver] instance from the source proto
 * @param [getTypeParameter]
 * A function that returns the type parameter for the given index.
 * **Only called if [ProtoBuf.Type.hasTypeParameter] is `true`!**
 */
private fun ProtoBuf.Type.asTypeName(
    nameResolver: NameResolver,
    getTypeParameter: (index: Int) -> ProtoBuf.TypeParameter
): TypeName {

  val argumentList = when {
    hasAbbreviatedType() -> abbreviatedType.argumentList
    else -> argumentList
  }

  if (hasFlexibleUpperBound()) {
    return WildcardTypeName.subtypeOf(
        flexibleUpperBound.asTypeName(nameResolver, getTypeParameter))
  } else if (hasOuterType()) {
    return WildcardTypeName.supertypeOf(outerType.asTypeName(nameResolver, getTypeParameter))
  }

  val realType = when {
    hasTypeParameter() -> return getTypeParameter(typeParameter)
        .asTypeName(nameResolver, getTypeParameter)
    hasTypeParameterName() -> typeParameterName
    hasAbbreviatedType() -> abbreviatedType.typeAliasName
    else -> className
  }

  var typeName: TypeName = ClassName.bestGuess(nameResolver.getString(realType)
      .replace("/", "."))

  if (argumentList.isNotEmpty()) {
    val remappedArgs: Array<TypeName> = argumentList.map {
      val projection = if (it.hasProjection()) {
        it.projection
      } else null
      if (it.hasType()) {
        it.type.asTypeName(nameResolver, getTypeParameter)
            .let { typeName ->
              projection?.let {
                when (it) {
                  Projection.IN -> WildcardTypeName.supertypeOf(typeName)
                  Projection.OUT -> {
                    if (typeName == ANY) {
                      // This becomes a *, which we actually don't want here.
                      // List<Any> works with List<*>, but List<*> doesn't work with List<Any>
                      typeName
                    } else {
                      WildcardTypeName.subtypeOf(typeName)
                    }
                  }
                  Projection.STAR -> WildcardTypeName.subtypeOf(ANY)
                  Projection.INV -> TODO("INV projection is unsupported")
                }
              } ?: typeName
            }
      } else {
        WildcardTypeName.subtypeOf(ANY)
      }
    }.toTypedArray()
    typeName = ParameterizedTypeName.get(typeName as ClassName, *remappedArgs)
  }

  if (nullable) {
    typeName = typeName.asNullable()
  }

  return typeName
}
