package com.squareup.moshi.kotshi.test

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotshi.*
import okio.Buffer
import org.junit.Test

class TestAdapterGeneration {
    private val moshi: Moshi = Moshi.Builder()
            .add(TestFactory)
            .add(String::class.java, Hello::class.java, HelloJsonAdapter())
            .build()

    @Test
    fun testBasic() {
        val json = """{
        |  "string": "string",
        |  "nullableString": "nullableString",
        |  "integer": 4711,
        |  "nullableInt": 1337,
        |  "isBoolean": true,
        |  "isNullableBoolean": false,
        |  "aShort": 32767,
        |  "nullableShort": -32768,
        |  "aByte": 255,
        |  "nullableByte": 128,
        |  "aChar": "c",
        |  "nullableChar": "n",
        |  "list": [
        |    "String1",
        |    "String2"
        |  ],
        |  "nestedList": [
        |    {
        |      "key1": [
        |        "set1",
        |        "set2"
        |      ]
        |    },
        |    {
        |      "key2": [
        |        "set1",
        |        "set2"
        |      ],
        |      "key3": []
        |    }
        |  ],
        |  "abstractProperty": "abstract",
        |  "other_name": "other_value",
        |  "annotated": "World!",
        |  "anotherAnnotated": "Other World!",
        |  "genericClass": {
        |    "collection": [
        |      "val1",
        |      "val2"
        |    ],
        |    "value": "val3"
        |  }
        |}""".trimMargin()
        val adapter = moshi.adapter(TestClass::class.java)
        val actual = adapter.fromJson(json)

        val expected = TestClass(
                string = "string",
                nullableString = "nullableString",
                integer = 4711,
                nullableInt = 1337,
                isBoolean = true,
                isNullableBoolean = false,
                aShort = Short.MAX_VALUE,
                nullableShort = Short.MIN_VALUE,
                aByte = -1,
                nullableByte = Byte.MIN_VALUE,
                aChar = 'c',
                nullableChar = 'n',
                list = listOf("String1", "String2"),
                nestedList = listOf(
                        mapOf("key1" to setOf("set1", "set2")),
                        mapOf(
                                "key2" to setOf("set1", "set2"),
                                "key3" to setOf())),
                abstractProperty = "abstract",
                customName = "other_value",
                annotated = "Hello, World!",
                anotherAnnotated = "Hello, Other World!",
                genericClass = GenericClass(listOf("val1", "val2"), "val3"))

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

    @Test
    fun testNull() {
        try {
            moshi.adapter(TestClass::class.java).fromJson("{}")
        } catch (e: NullPointerException) {
            assertEquals("The following properties were null: " +
                    "string, " +
                    "integer, " +
                    "isBoolean, " +
                    "aShort, " +
                    "aByte, " +
                    "aChar, " +
                    "list, " +
                    "nestedList, " +
                    "abstractProperty, " +
                    "customName, " +
                    "annotated, " +
                    "anotherAnnotated, " +
                    "genericClass",
                    e.message)
        }
    }

    @Test
    fun testCustomNames() {
        val json = """{"jsonProp1":"value1","jsonProp2":"value2"}"""
        val adapter = moshi.adapter(CustomNames::class.java)
        val actual = adapter.fromJson(json)
        val expected = CustomNames("value1", "value2")
        assertEquals(expected, actual)
        assertEquals(json, adapter.toJson(actual))
    }

    @Test
    fun testExtraFields() {
        val adapter = moshi.adapter(Simple::class.java)
        val actual = adapter.fromJson("""{"prop":"value","extra_prop":"extra_value"}""")
        assertEquals(Simple("value"), actual)
        assertEquals("""{"prop":"value"}""", adapter.toJson(actual))
    }

    @Test
    fun testNestedClasses() {
        val adapter = moshi.adapter(NestedClasses::class.java)
        val json = """{"inner":{"prop":"value"}}"""
        val actual = adapter.fromJson(json)
        assertEquals(NestedClasses(NestedClasses.Inner("value")), actual)
        assertEquals(json, adapter.toJson(actual))
    }

    @Test
    fun testGenericTypeWithQualifier() {
        val adapter: JsonAdapter<GenericClassWithQualifier<String>> =
                moshi.adapter(Types.newParameterizedType(GenericClassWithQualifier::class.java, String::class.java))
        val json = """{"value":"world!"}"""
        val actual = adapter.fromJson(json)
        assertEquals(GenericClassWithQualifier("Hello, world!"), actual)
        assertEquals(json, adapter.toJson(actual))
    }

    @Test
    fun testMultipleJsonQualifiers() {
        val adapter = Moshi.Builder()
                .add(object : Any() {
                    @FromJson
                    @WrappedInObject
                    @WrappedInArray
                    fun fromJson(reader: JsonReader): String {
                        reader.beginObject()
                        reader.nextName()
                        reader.beginArray()
                        val value = reader.nextString()
                        reader.endArray()
                        reader.endObject()
                        return value
                    }

                    @ToJson
                    fun toJson(writer: JsonWriter, @WrappedInObject @WrappedInArray value: String) {
                        writer.beginObject()
                        writer.name("name")
                        writer.beginArray()
                        writer.value(value)
                        writer.endArray()
                        writer.endObject()
                    }
                })
                .add(TestFactory)
                .build()
                .adapter(MultipleJsonQualifiers::class.java)
        val json = """{"string":{"name":["Hello, world!"]}}"""
        val value = MultipleJsonQualifiers("Hello, world!")
        assertEquals(value, adapter.fromJson(json))
        assertEquals(json, adapter.toJson(value))
    }

    @Test
    fun testToString() {
        assertEquals("KotshiJsonAdapter(NestedClasses)", moshi.adapter(NestedClasses::class.java).toString())
        assertEquals("KotshiJsonAdapter(NestedClasses.Inner)", moshi.adapter(NestedClasses.Inner::class.java).toString())
    }
}
