package com.squareup.moshi.kotlin.codgen

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.codgen.GeneratedAdaptersTest.CustomGeneratedClass

class GeneratedAdaptersTest_CustomGeneratedClassJsonAdapter(moshi: Moshi) : JsonAdapter<CustomGeneratedClass>() {
  override fun fromJson(reader: JsonReader): CustomGeneratedClass? {
    TODO()
  }

  override fun toJson(writer: JsonWriter, value: CustomGeneratedClass?) {
    TODO()
  }
}
