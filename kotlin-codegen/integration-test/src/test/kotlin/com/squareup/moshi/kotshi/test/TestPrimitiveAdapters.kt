package com.squareup.moshi.kotshi.test

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotshi.*
import junit.framework.Assert.assertEquals
import okio.Buffer
import org.junit.Before
import org.junit.Test

class TestPrimitiveAdapters {
    private lateinit var moshi: Moshi
    private lateinit var stringAdapter: DelegateAdapter<String>
    private lateinit var booleanAdapter: DelegateAdapter<Boolean>
    private lateinit var byteAdapter: DelegateAdapter<Byte>
    private lateinit var charAdapter: DelegateAdapter<Char>
    private lateinit var shortAdapter: DelegateAdapter<Short>
    private lateinit var intAdapter: DelegateAdapter<Int>
    private lateinit var longAdapter: DelegateAdapter<Long>
    private lateinit var floatAdapter: DelegateAdapter<Float>
    private lateinit var doubleAdapter: DelegateAdapter<Double>

    @Before
    fun setup() {
        val basicMoshi = Moshi.Builder().build()
        stringAdapter = DelegateAdapter(basicMoshi.adapter(String::class.java))
        booleanAdapter = DelegateAdapter(basicMoshi.adapter(Boolean::class.java))
        byteAdapter = DelegateAdapter(basicMoshi.adapter(Byte::class.java))
        charAdapter = DelegateAdapter(basicMoshi.adapter(Char::class.java))
        shortAdapter = DelegateAdapter(basicMoshi.adapter(Short::class.java))
        intAdapter = DelegateAdapter(basicMoshi.adapter(Int::class.java))
        longAdapter = DelegateAdapter(basicMoshi.adapter(Long::class.java))
        floatAdapter = DelegateAdapter(basicMoshi.adapter(Float::class.java))
        doubleAdapter = DelegateAdapter(basicMoshi.adapter(Double::class.java))
        moshi = Moshi.Builder()
                .add(TestFactory)
                .add(String::class.java, stringAdapter)
                .add(Boolean::class.javaPrimitiveType!!, booleanAdapter)
                .add(Boolean::class.javaObjectType, booleanAdapter)
                .add(Byte::class.javaPrimitiveType!!, byteAdapter)
                .add(Byte::class.javaObjectType, byteAdapter)
                .add(Char::class.javaPrimitiveType!!, charAdapter)
                .add(Char::class.javaObjectType, charAdapter)
                .add(Short::class.javaPrimitiveType!!, shortAdapter)
                .add(Short::class.javaObjectType, shortAdapter)
                .add(Int::class.javaPrimitiveType!!, intAdapter)
                .add(Int::class.javaObjectType, intAdapter)
                .add(Long::class.javaPrimitiveType!!, longAdapter)
                .add(Long::class.javaObjectType, longAdapter)
                .add(Float::class.javaPrimitiveType!!, floatAdapter)
                .add(Float::class.javaObjectType, floatAdapter)
                .add(Double::class.javaPrimitiveType!!, doubleAdapter)
                .add(Double::class.javaObjectType, doubleAdapter)
                .add(Int::class.javaPrimitiveType!!, Hello::class.java, intAdapter)
                .build()
    }

    @Test
    fun testDoesntCallAdapter() {
        testFormatting(json, NotUsingPrimitiveAdapterTestClass(
                aString = "hello",
                aBoolean = true,
                aNullableBoolean = false,
                aByte = -1,
                nullableByte = Byte.MIN_VALUE,
                aChar = 'c',
                nullableChar = 'n',
                aShort = 32767,
                nullableShort = -32768,
                integer = 4711,
                nullableInteger = 1337,
                aLong = 4711,
                nullableLong = 1337,
                aFloat = 4711.5f,
                nullableFloat = 1337.5f,
                aDouble = 4711.5,
                nullableDouble = 1337.5))
        assertEquals(0, stringAdapter.readCount)
        assertEquals(0, stringAdapter.writeCount)
        assertEquals(0, booleanAdapter.readCount)
        assertEquals(0, booleanAdapter.writeCount)
        assertEquals(0, byteAdapter.readCount)
        assertEquals(0, byteAdapter.writeCount)
        assertEquals(0, charAdapter.readCount)
        assertEquals(0, charAdapter.writeCount)
        assertEquals(0, shortAdapter.readCount)
        assertEquals(0, shortAdapter.writeCount)
        assertEquals(0, intAdapter.readCount)
        assertEquals(0, intAdapter.writeCount)
        assertEquals(0, longAdapter.readCount)
        assertEquals(0, longAdapter.writeCount)
        assertEquals(0, floatAdapter.readCount)
        assertEquals(0, floatAdapter.writeCount)
        assertEquals(0, doubleAdapter.readCount)
        assertEquals(0, doubleAdapter.writeCount)
    }

    @Test
    fun testCallsAdapter() {
        testFormatting(json, UsingPrimitiveAdapterTestClass(
                aString = "hello",
                aBoolean = true,
                aNullableBoolean = false,
                aByte = -1,
                nullableByte = Byte.MIN_VALUE,
                aChar = 'c',
                nullableChar = 'n',
                aShort = 32767,
                nullableShort = -32768,
                integer = 4711,
                nullableInteger = 1337,
                aLong = 4711,
                nullableLong = 1337,
                aFloat = 4711.5f,
                nullableFloat = 1337.5f,
                aDouble = 4711.5,
                nullableDouble = 1337.5))
        assertEquals(1, stringAdapter.readCount)
        assertEquals(1, stringAdapter.writeCount)
        assertEquals(2, booleanAdapter.readCount)
        assertEquals(2, booleanAdapter.writeCount)
        assertEquals(2, byteAdapter.readCount)
        assertEquals(2, byteAdapter.writeCount)
        assertEquals(2, charAdapter.readCount)
        assertEquals(2, charAdapter.writeCount)
        assertEquals(2, shortAdapter.readCount)
        assertEquals(2, shortAdapter.writeCount)
        assertEquals(2, intAdapter.readCount)
        assertEquals(2, intAdapter.writeCount)
        assertEquals(2, longAdapter.readCount)
        assertEquals(2, longAdapter.writeCount)
        assertEquals(2, floatAdapter.readCount)
        assertEquals(2, floatAdapter.writeCount)
        assertEquals(2, doubleAdapter.readCount)
        assertEquals(2, doubleAdapter.writeCount)
    }

    @Test
    fun callsAdapterWhenQualifiersPresent() {
        testFormatting("""{
          |  "greetingInt": 1
          |}""".trimMargin(), PrimitiveWithJsonQualifierTestClass(1))
        assertEquals(1, intAdapter.readCount)
        assertEquals(1, intAdapter.writeCount)
    }

    private inline fun <reified T> testFormatting(json: String, expected: T) {
        val adapter = moshi.adapter(T::class.java)
        val actual = adapter.fromJson(json)
        assertEquals(expected, actual)
        assertEquals(json, Buffer()
                .apply {
                    JsonWriter.of(this).run {
                        indent = "  "
                        adapter.toJson(this, actual)
                    }
                }
                .readUtf8())
    }

    companion object {
        val json = """{
            |  "aString": "hello",
            |  "aBoolean": true,
            |  "aNullableBoolean": false,
            |  "aByte": 255,
            |  "nullableByte": 128,
            |  "aChar": "c",
            |  "nullableChar": "n",
            |  "aShort": 32767,
            |  "nullableShort": -32768,
            |  "integer": 4711,
            |  "nullableInteger": 1337,
            |  "aLong": 4711,
            |  "nullableLong": 1337,
            |  "aFloat": 4711.5,
            |  "nullableFloat": 1337.5,
            |  "aDouble": 4711.5,
            |  "nullableDouble": 1337.5
            |}""".trimMargin()

    }

    private class DelegateAdapter<T>(private val delegate: JsonAdapter<T>) : JsonAdapter<T>() {

        var writeCount: Int = 0
            private set

        var readCount: Int = 0
            private set

        override fun toJson(writer: JsonWriter, value: T?) {
            writeCount += 1
            delegate.toJson(writer, value)
        }

        override fun fromJson(reader: JsonReader): T? {
            readCount += 1
            return delegate.fromJson(reader)
        }
    }
}
