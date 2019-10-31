package com.squareup.moshi.kotlin

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

class SkipBadElementsListAdapter(private val elementAdapter: JsonAdapter<Any?>) :
    JsonAdapter<List<Any?>>() {
  object Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (annotations.isNotEmpty() || Types.getRawType(type) != List::class.java) {
        return null
      }
      val elementType = Types.collectionElementType(type, List::class.java)
      val elementAdapter = moshi.adapter<Any?>(elementType)
      return SkipBadElementsListAdapter(elementAdapter)
    }
  }

  override fun fromJson(reader: JsonReader): List<Any?>? {
    val result = mutableListOf<Any?>()
    reader.beginArray()
    while (reader.hasNext()) {
      try {
        val peeked = reader.peekJson()
        result += elementAdapter.fromJson(peeked)
      } catch (ignored: JsonDataException) {
      }
      reader.skipValue()
    }
    reader.endArray()
    return result

  }

  override fun toJson(writer: JsonWriter, value: List<Any?>?) {
    if (value == null) {
      throw NullPointerException("value was null! Wrap in .nullSafe() to write nullable values.")
    }
    writer.beginArray()
    for (i in value.indices) {
      elementAdapter.toJson(writer, value[i])
    }
    writer.endArray()
  }
}
