package com.squareup.moshi.kotshi.test

import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.MoshiSerializableFactory
import com.squareup.moshi.kotshi.ClassWithPrimitiveDefaults
import junit.framework.Assert.assertEquals
import okio.Buffer
import org.junit.Before
import org.junit.Test

class TestPrimitiveDefaultValues {
    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
                .add(MoshiSerializableFactory.getInstance())
                .build()
    }

    @Test
    fun withValues() {
        val json = """{
             |  "someString": "someString",
             |  "someBoolean": false,
             |  "someByte": 255,
             |  "someChar": "X",
             |  "someShort": 1337,
             |  "someInt": 1337,
             |  "someLong": 1337,
             |  "someFloat": 0.0,
             |  "someDouble": 0.0
             |}""".trimMargin()

        val expected = ClassWithPrimitiveDefaults(
                someString = "someString",
                someBoolean = false,
                someByte = -1,
                someChar = 'X',
                someShort = 1337,
                someInt = 1337,
                someLong = 1337,
                someFloat = 0f,
                someDouble = 0.0)

        expected.testFormatting(json)
    }

    @Test
    fun withNullValues() {
        val expected = ClassWithPrimitiveDefaults(
                someString = "default",
                someBoolean = true,
                someByte = 66,
                someChar = 'N',
                someShort = 4711,
                someInt = 4711,
                someLong = 4711,
                someFloat = 0.4711f,
                someDouble = 0.4711)

        val actual = moshi.adapter(ClassWithPrimitiveDefaults::class.java).fromJson("""{
             |  "someString": null,
             |  "someBoolean": null,
             |  "someByte": null,
             |  "someChar": null,
             |  "someShort": null,
             |  "someInt": null,
             |  "someLong": null,
             |  "someFloat": null,
             |  "someDouble": null
             |}""".trimMargin())

        assertEquals(expected, actual)
    }

    @Test
    fun withAbsentValues() {
        val expected = ClassWithPrimitiveDefaults(
                someString = "default",
                someBoolean = true,
                someByte = 66,
                someChar = 'N',
                someShort = 4711,
                someInt = 4711,
                someLong = 4711,
                someFloat = 0.4711f,
                someDouble = 0.4711)

        val actual = moshi.adapter(ClassWithPrimitiveDefaults::class.java).fromJson("{}")
        assertEquals(expected, actual)
    }

    private inline fun <reified T> T.testFormatting(json: String) {
        val adapter = moshi.adapter(T::class.java)
        val actual = adapter.fromJson(json)
        assertEquals(this, actual)
        assertEquals(json, Buffer()
                .apply {
                    JsonWriter.of(this).run {
                        indent = "  "
                        adapter.toJson(this, actual)
                    }
                }
                .readUtf8())
    }
}
