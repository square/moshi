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
import com.squareup.kotlinpoet.KModifier.LATEINIT
import com.squareup.kotlinpoet.KModifier.OUT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LONG
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

          val jsonQualifiers = actualElement.annotationMirrors
              .filter { it.annotationType.getAnnotation(JsonQualifier::class.java) != null }

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
    val adapterName = "${name}_JsonAdapter"
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

private val TypeName.isPrimitive: Boolean
  get() = !nullable && this in PRIMITIVE_TYPES

private val PRIMITIVE_TYPES = setOf(
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    CHAR,
    FLOAT,
    DOUBLE
)

private fun primitiveDefaultFor(typeName: TypeName): String {
  return when (typeName) {
    BOOLEAN -> "false"
    BYTE -> "0 as Byte"
    SHORT -> "0 as Short"
    INT -> "0"
    LONG -> "0L"
    CHAR -> "'0'"
    FLOAT -> "0.0f"
    DOUBLE -> "0.0"
    else -> throw IllegalArgumentException("Non-primitive type! $typeName")
  }
}

/**
 * Creates a joined string representation of simplified typename names.
 */
private fun List<TypeName>.simplifiedNames(): String {
  return joinToString("_") { it.simplifiedName() }
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
  }
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
            typeArguments[0].makeType(elementUtils, typesArray, genericTypeNames))
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
            *(typeArguments.toTypedArray())
        )
      } else {
        CodeBlock.of("")
      }
      CodeBlock.of(
          "%T.newParameterizedType(%T%L::class.java, ${typeArguments
              .joinToString(", ") { "%L" }})",
          Types::class.asTypeName(),
          rawType,
          rawTypeParameters,
          *(typeArguments.map {
            it.makeType(elementUtils, typesArray, genericTypeNames)
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
    val jsonQualifiers: List<AnnotationMirror>) {

  val isNullablyBoundedTypeVariable = typeName is TypeVariableName
      && with(typeName.bounds) { isEmpty() || any { it.nullable } }
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
        .map { it.typeName.asNonNullable() }
        .distinct()
        .associate { typeName ->
          val propertyName = "${typeName.simplifiedName().allocate()}_Adapter"
          val adapterTypeName = ParameterizedTypeName.get(JsonAdapter::class.asTypeName(), typeName)
          typeName to PropertySpec.builder(propertyName, adapterTypeName, PRIVATE)
              .initializer("%N.adapter%L(%L)",
                  moshiParam,
                  if (typeName is ClassName) "" else CodeBlock.of("<%T>", typeName),
                  typeName.makeType(elementUtils, typesParam, genericTypeNames ?: emptyList()))
              .build()
        }

    val localProperties =
        propertyList.associate { prop ->
          val name = prop.name.allocate()
          prop to when {
            prop.nullable -> {
              PropertySpec.builder(name, prop.typeName.asNullable())
                  .mutable(true)
                  .initializer("null")
                  .build()
            }
            prop.isNullablyBoundedTypeVariable -> {
              PropertySpec.builder(name, prop.typeName.asNullable())
                  .mutable(true)
                  .initializer("null")
                  .build()
            }
            prop.hasDefault -> {
              PropertySpec.builder(name, prop.typeName.asNullable())
                  .mutable(true)
                  .initializer("null")
                  .build()
            }
            prop.typeName.isPrimitive -> {
              PropertySpec.builder(name, prop.typeName)
                  .mutable(true)
                  .initializer("%L", primitiveDefaultFor(prop.typeName))
                  .build()
            }
            else ->  {
              PropertySpec.builder(name, prop.typeName)
                  .mutable(true)
                  .addModifiers(LATEINIT)
                  .build()
            }
          }
        }
    val optionsByIndex = propertyList
        .associateBy { it.serializedName }.entries.withIndex()

    // selectName() API setup
    val optionsCN = JsonReader.Options::class.asTypeName()
    val optionsProperty = PropertySpec.builder(
        "OPTIONS".allocate(),
        optionsCN,
        PRIVATE)
        .initializer("%T.of(${optionsByIndex.map { it.value.key }
                .joinToString(", ") { "\"$it\"" }})",
            optionsCN)
        .build()
    val companionObject = TypeSpec.companionObjectBuilder("SelectOptions")
        .addModifiers(PRIVATE)
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
        .addFunction(FunSpec.builder("fromJson")
            .addModifiers(OVERRIDE)
            .addParameter(reader)
            .returns(originalTypeName.asNullable())
            .beginControlFlow("if (%N.peek() == %T.NULL)", reader,
                JsonReader.Token.NULL.declaringClass.asTypeName())
            .addStatement("%N.nextNull<%T>()", reader, ANY)
            .endControlFlow()
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
                    val possibleBangs = if (prop.nullable) "" else "!!"
                    addStatement("%L -> %N = %N.fromJson(%N)$possibleBangs",
                        index,
                        localProperties[prop]!!,
                        adapterProperties[prop.typeName.asNonNullable()]!!,
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
              localProperties.forEach { (property, spec) ->
                if (property.isNullablyBoundedTypeVariable) {
                  beginControlFlow("if (%N == null)", spec)
                  addStatement("throw %T(\"%N was null\")", NullPointerException::class, spec)
                  endControlFlow()
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
            .beginControlFlow("if (%N == null)", value)
            .addStatement("%N.nullValue()", writer)
            .addStatement("return")
            .endControlFlow()
            .addStatement("%N.beginObject()", writer)
            .apply {
              propertyList.forEach { prop ->
                if (prop.nullable) {
                  beginControlFlow("if (%N.%L != null)", value, prop.name)
                }
                addStatement("%N.name(%S)", writer, prop.serializedName)
                addStatement("%N.toJson(%N, %N.%L)",
                    adapterProperties[prop.typeName.asNonNullable()]!!,
                    writer,
                    value,
                    prop.name)
                if (prop.nullable) {
                  endControlFlow()
                }
              }
            }
            .addStatement("%N.endObject()", writer)
            .build())
        .build()

    if (hasCompanionObject) {
      val rawType = when (originalTypeName) {
        is TypeVariableName -> throw IllegalArgumentException("Cannot get raw type of TypeVariable!")
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
