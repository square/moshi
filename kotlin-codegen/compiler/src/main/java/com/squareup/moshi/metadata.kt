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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import org.jetbrains.kotlin.serialization.ProtoBuf.Type
import org.jetbrains.kotlin.serialization.ProtoBuf.TypeParameter
import org.jetbrains.kotlin.serialization.ProtoBuf.TypeParameter.Variance
import org.jetbrains.kotlin.serialization.deserialization.NameResolver

internal fun TypeParameter.asTypeName(
  nameResolver: NameResolver,
  getTypeParameter: (index: Int) -> TypeParameter,
  resolveAliases: Boolean = false
): TypeName {
  return TypeVariableName(
      name = nameResolver.getString(name),
      bounds = *(upperBoundList.map {
        it.asTypeName(nameResolver, getTypeParameter, resolveAliases)
      }
          .toTypedArray()),
      variance = variance.asKModifier()
  )
}

internal fun TypeParameter.Variance.asKModifier(): KModifier? {
  return when (this) {
    Variance.IN -> KModifier.IN
    Variance.OUT -> KModifier.OUT
    Variance.INV -> null
  }
}

/**
 * Returns the TypeName of this type as it would be seen in the source code, including nullability
 * and generic type parameters.
 *
 * @param [nameResolver] a [NameResolver] instance from the source proto
 * @param [getTypeParameter] a function that returns the type parameter for the given index. **Only
 *     called if [ProtoBuf.Type.hasTypeParameter] is true!**
 */
internal fun Type.asTypeName(
  nameResolver: NameResolver,
  getTypeParameter: (index: Int) -> TypeParameter,
  resolveAliases: Boolean = false
): TypeName {

  val argumentList = when {
    hasAbbreviatedType() -> abbreviatedType.argumentList
    else -> argumentList
  }

  if (hasFlexibleUpperBound()) {
    return WildcardTypeName.subtypeOf(
        flexibleUpperBound.asTypeName(nameResolver, getTypeParameter, resolveAliases))
  } else if (hasOuterType()) {
    return WildcardTypeName.supertypeOf(
        outerType.asTypeName(nameResolver, getTypeParameter, resolveAliases))
  }

  val realType = when {
    hasTypeParameter() -> return getTypeParameter(typeParameter)
        .asTypeName(nameResolver, getTypeParameter, resolveAliases)
    hasTypeParameterName() -> typeParameterName
    hasAbbreviatedType() && !resolveAliases -> abbreviatedType.typeAliasName
    else -> className
  }

  var typeName: TypeName =
      ClassName.bestGuess(nameResolver.getString(realType)
          .replace("/", "."))

  if (argumentList.isNotEmpty()) {
    val remappedArgs: Array<TypeName> = argumentList.map {
      val projection = if (it.hasProjection()) {
        it.projection
      } else null
      if (it.hasType()) {
        it.type.asTypeName(nameResolver, getTypeParameter, resolveAliases)
            .let { typeName ->
              projection?.let {
                when (it) {
                  Type.Argument.Projection.IN -> WildcardTypeName.supertypeOf(
                      typeName)
                  Type.Argument.Projection.OUT -> {
                    if (typeName == ANY) {
                      // This becomes a *, which we actually don't want here.
                      // List<Any> works with List<*>, but List<*> doesn't work with List<Any>
                      typeName
                    } else {
                      WildcardTypeName.subtypeOf(typeName)
                    }
                  }
                  Type.Argument.Projection.STAR -> WildcardTypeName.subtypeOf(
                      ANY)
                  Type.Argument.Projection.INV -> TODO("INV projection is unsupported")
                }
              } ?: typeName
            }
      } else {
        WildcardTypeName.subtypeOf(ANY)
      }
    }.toTypedArray()
    typeName = ParameterizedTypeName.get(
        typeName as ClassName, *remappedArgs)
  }

  if (nullable) {
    typeName = typeName.asNullable()
  }

  return typeName
}