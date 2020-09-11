package com.squareup.moshi.recipes

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import okio.BufferedSource
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME

@JsonClass(generateAdapter = true)
data class ExampleClass(
  val type: Int,
  @JsonString val rawJson: String
)

@Retention(RUNTIME)
@JsonQualifier
annotation class JsonString

class JsonStringJsonAdapter : JsonAdapter<String>() {
  override fun fromJson(reader: JsonReader): String =
    reader.valueSource().use(BufferedSource::readUtf8)

  override fun toJson(writer: JsonWriter, value: String?) {
    writer.valueSink().use { sink -> sink.writeUtf8(checkNotNull(value)) }
  }

  companion object Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (type != String::class.java) return null
      Types.nextAnnotations(annotations, JsonString::class.java) ?: return null
      return JsonStringJsonAdapter().nullSafe()
    }
  }
}

fun main() {
  //language=JSON
  val json = "{\"type\":1,\"rawJson\":{\"a\":2,\"b\":3,\"c\":[1,2,3]}}"

  val moshi = Builder()
    .add(JsonStringJsonAdapter.Factory)
    .build()

  val example: ExampleClass = moshi.adapter(ExampleClass::class.java).fromJson(json)!!

  check(example.type == 1)

  //language=JSON
  check(example.rawJson == "{\"a\":2,\"b\":3,\"c\":[1,2,3]}")
}
