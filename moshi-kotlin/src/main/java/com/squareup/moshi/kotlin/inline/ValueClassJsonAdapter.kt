package com.squareup.moshi.kotlin.inline

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import java.lang.reflect.Constructor
import java.lang.reflect.Type

internal class ValueClassJsonAdapter<InlineT : Any, ValueT : Any> private constructor(
  private val constructor: Constructor<out InlineT>,
  private val adapter: JsonAdapter<ValueT>,
) : JsonAdapter<InlineT>() {
  @Suppress("UNCHECKED_CAST")
  private fun <T : Any, ValueT> T.declaredProperty(): ValueT = with(this::class.java.declaredFields.first()) {
    isAccessible = true
    get(this@declaredProperty) as ValueT
  }

  override fun toJson(
    writer: JsonWriter,
    value: InlineT?,
  ) {
    value?.let { writer.jsonValue(adapter.toJsonValue(it.declaredProperty())) }
  }

  @Suppress("TooGenericExceptionCaught")
  override fun fromJson(reader: JsonReader): InlineT =
    reader.readJsonValue().let { jsonValue -> constructor.newInstance(adapter.fromJsonValue(jsonValue)) }

  object Factory : JsonAdapter.Factory {
    private val unsignedTypes = listOf(
      ULong::class.java,
      UInt::class.java,
      UShort::class.java,
      UByte::class.java,
    )

    override fun create(
      type: Type,
      annotations: Set<Annotation>,
      moshi: Moshi,
    ): JsonAdapter<Any>? = if (type.rawType.kotlin.isValue && !unsignedTypes.contains(type)) {
      val constructor = (type.rawType.declaredConstructors.first { it.parameterCount == 1 } as Constructor<*>)
        .also { it.isAccessible = true }
      val valueType = type.rawType.declaredFields[0].genericType
      ValueClassJsonAdapter(constructor = constructor, adapter = moshi.adapter(valueType))
    } else null
  }
}
