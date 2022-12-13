package com.squareup.moshi.kotlin.inline

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Token
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import java.lang.reflect.Type

internal class UnsignedNumberJsonAdapter<UnsignedT : Any> private constructor(
  private val toUnsignedT: ULong.() -> UnsignedT,
) : JsonAdapter<UnsignedT>() {
  override fun toJson(writer: JsonWriter, value: UnsignedT?) {
    when (value) {
      null -> writer.nullValue()
      else -> writer.valueSink().use { it.writeUtf8(value.toString()) }
    }
  }

  override fun fromJson(reader: JsonReader): UnsignedT? = when (val next = reader.peek()) {
    Token.NUMBER -> {
      try {
        reader.nextString().toULong().toUnsignedT()
      } catch (numberFormatException: NumberFormatException) {
        throw JsonDataException(
          "${numberFormatException.message} for unsigned number at ${reader.path}"
        )
      }
    }

    Token.NULL -> reader.nextNull()
    else -> throw JsonDataException(
      "Expected an unsigned number but was ${reader.readJsonValue()}, " +
        "a $next, at path ${reader.path}",
      IllegalArgumentException(next.name)
    )
  }

  object Factory : JsonAdapter.Factory {
    private val unsignedTypesMapperMap: Map<Class<*>, ULong.() -> Any> = mapOf(
      ULong::class.java to { this },
      UInt::class.java to {
        if (this > UInt.MAX_VALUE) throw NumberFormatException("Invalid number format: '$this'") else toUInt()
      },
      UShort::class.java to {
        if (this > UShort.MAX_VALUE) throw NumberFormatException("Invalid number format: '$this'") else toUShort()
      },
      UByte::class.java to {
        if (this > UByte.MAX_VALUE) throw NumberFormatException("Invalid number format: '$this'") else toUByte()
      }
    )

    private val Type.isUnsignedType: Boolean
      get() = unsignedTypesMapperMap.keys.contains(rawType)

    private val Type.mapper: ULong.() -> Any
      get() = unsignedTypesMapperMap[rawType]!!

    override fun create(
      type: Type,
      annotations: Set<Annotation>,
      moshi: Moshi,
    ): JsonAdapter<*>? = if (type.isUnsignedType) UnsignedNumberJsonAdapter(type.mapper) else null
  }
}
