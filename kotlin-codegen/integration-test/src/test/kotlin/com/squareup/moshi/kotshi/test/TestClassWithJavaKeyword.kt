package com.squareup.moshi.kotshi.test

import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.MoshiSerializableFactory
import com.squareup.moshi.kotshi.ClassWithJavaKeyword
import junit.framework.Assert.assertEquals
import okio.Buffer
import org.junit.Test

class TestClassWithJavaKeyword {
  private val adapter = Moshi.Builder()
      .add(MoshiSerializableFactory())
      .build()
      .adapter(ClassWithJavaKeyword::class.java)

  @Test
  fun reading() {
    val json = """{
            |  "default": true,
            |  "int": 4711,
            |  "case": 1337
            |}""".trimMargin()

    assertEquals(ClassWithJavaKeyword(true, 4711, 1337), adapter.fromJson(json))
  }

  @Test
  fun writing() {
    val expected = """{
            |  "default": true,
            |  "int": 4711,
            |  "case": 1337
            |}""".trimMargin()

    val buffer = Buffer()
    val writer = JsonWriter.of(buffer).apply { indent = "  " }
    adapter.toJson(writer, ClassWithJavaKeyword(true, 4711, 1337))
    val actual = buffer.readUtf8()
    assertEquals(expected, actual)
  }

}
