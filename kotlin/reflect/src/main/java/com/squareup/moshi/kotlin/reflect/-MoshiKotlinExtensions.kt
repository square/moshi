package com.squareup.moshi.kotlin.reflect

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

/**
 * @return a [JsonAdapter] for [T], creating it if necessary. Note that while nullability of [T]
 *         itself is handled, nested types (such as in generics) are not resolved.
 */
@ExperimentalStdlibApi
inline fun <reified T> Moshi.adapter(): JsonAdapter<T> {
  return adapter(typeOf<T>())
}

@ExperimentalStdlibApi
inline fun <reified T> Moshi.Builder.addAdapter(adapter: JsonAdapter<T>) = add(typeOf<T>().javaType, adapter)

/**
 * @return a [JsonAdapter] for [ktype], creating it if necessary. Note that while nullability of
 *         [ktype] itself is handled, nested types (such as in generics) are not resolved.
 */
fun <T> Moshi.adapter(ktype: KType): JsonAdapter<T> {
  val adapter = adapter<T>(ktype.toType())
  return if (ktype.isMarkedNullable) {
    adapter.nullSafe()
  } else {
    adapter.nonNull()
  }
}

internal fun KType.toType(): Type {
  classifier?.let {
    when (it) {
      is KTypeParameter -> throw IllegalArgumentException("Type parameters are not supported")
      is KClass<*> -> {
        val javaType = it.java
        if (javaType.isArray) {
          return Types.arrayOf(javaType.componentType)
        }

        return if (arguments.isEmpty()) {
          javaType
        } else {
          val typeArguments = arguments.toTypedArray { it.toType() }
          val enclosingClass = javaType.enclosingClass
          return if (enclosingClass != null) {
            Types.newParameterizedTypeWithOwner(enclosingClass, javaType, *typeArguments)
          } else {
            Types.newParameterizedType(javaType, *typeArguments)
          }
        }
      }
      else -> throw IllegalArgumentException("Unsupported classifier: $this")
    }
  }

  // Can happen for intersection types
  throw IllegalArgumentException("Unrepresentable type: $this")
}

internal fun KTypeProjection.toType(): Type {
  val javaType = type?.toType() ?: return Any::class.java
  return when (variance) {
    null -> Any::class.java
    KVariance.INVARIANT -> javaType
    KVariance.IN -> Types.subtypeOf(javaType)
    KVariance.OUT -> Types.supertypeOf(javaType)
  }
}

private inline fun <T, reified R> List<T>.toTypedArray(mapper: (T) -> R): Array<R> {
  return Array(size) {
    mapper.invoke(get(it))
  }
}
