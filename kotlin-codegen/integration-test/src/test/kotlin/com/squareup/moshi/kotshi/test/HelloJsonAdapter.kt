package com.squareup.moshi.kotshi.test

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

class HelloJsonAdapter : JsonAdapter<String>() {
    override fun toJson(writer: JsonWriter, value: String?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.removePrefix(HELLO_PREFIX))
        }
    }

    override fun fromJson(reader: JsonReader): String? = when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        else -> HELLO_PREFIX + reader.nextString()
    }

    companion object {
        private const val HELLO_PREFIX = "Hello, "
    }
}
