package com.squareup.moshi.internal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlin.reflect.KType
import kotlin.reflect.javaType

internal class InteropKFactory(val delegate: JsonAdapter.Factory) : JsonAdapter.KFactory {
  @OptIn(ExperimentalStdlibApi::class)
  override fun create(type: KType, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? =
    delegate.create(type.javaType, annotations, moshi)

  override fun toString(): String = "KFactory($delegate)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InteropKFactory

    if (delegate != other.delegate) return false

    return true
  }

  override fun hashCode(): Int = delegate.hashCode()
}
