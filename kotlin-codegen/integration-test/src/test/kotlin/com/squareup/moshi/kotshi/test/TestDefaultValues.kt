package com.squareup.moshi.kotshi.test

import com.squareup.moshi.*
import com.squareup.moshi.kotshi.*
import junit.framework.Assert.assertEquals
import junit.framework.Assert.fail
import okio.Buffer
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TestDefaultValues {
    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
//                .add(TestFactory)
                .add(MoshiSerializableFactory.getInstance())
                .add(LocalDate::class.java, LocalDateAdapter)
                .add(LocalTime::class.java, LocalTimeAdapter)
                .add(LocalDateTime::class.java, LocalDateTimeAdapter)
                .build()
    }

    @Test
    fun withValues() {
        val json = """{
             |  "v1": {
             |    "v": "v1"
             |  },
             |  "v2": {
             |    "v": "v2"
             |  },
             |  "v3": {
             |    "v": "v3"
             |  },
             |  "v4": {
             |    "v": "v4"
             |  },
             |  "v5": {
             |    "v": "v5"
             |  },
             |  "v6": {
             |    "v": 6
             |  },
             |  "v7": "1989-07-03",
             |  "v8": "13:37",
             |  "v9": "1989-07-03T13:37",
             |  "v10": {
             |    "v": "v10"
             |  },
             |  "v11": {
             |    "v": "v11"
             |  },
             |  "v12": {
             |    "v": "v12"
             |  },
             |  "v13": {
             |    "v": "v13"
             |  },
             |  "v14": 14,
             |  "v15": "VALUE4",
             |  "v16": {
             |    "someKey": 4711
             |  }
             |}""".trimMargin()

        val expected = ClassWithDefaultValues(
                v1 = WithCompanionFunction("v1"),
                v2 = WithStaticFunction("v2"),
                v3 = WithCompanionProperty("v3"),
                v4 = WithStaticProperty("v4"),
                v5 = GenericClassWithDefault("v5"),
                v6 = GenericClassWithDefault(6),
                v7 = LocalDate.of(1989, 7, 3),
                v8 = LocalTime.of(13, 37),
                v9 = LocalDateTime.of(1989, 7, 3, 13, 37),
                v10 = WithCompanionFunction("v10"),
                v11 = WithCompanionFunction("v11"),
                v12 = ClassWithConstructorAsDefault("v12"),
                v13 = GenericClassWithConstructorAsDefault("v13"),
                v14 = 14,
                v15 = SomeEnum.VALUE4,
                v16 = mapOf("someKey" to 4711))

        expected.testFormatting(json)
    }

    @Test
    fun withNullValues() {
        val expected = ClassWithDefaultValues(
                v1 = WithCompanionFunction("WithCompanionFunction"),
                v2 = WithStaticFunction("WithStaticFunction"),
                v3 = WithCompanionProperty("WithCompanionProperty"),
                v4 = WithStaticProperty("WithStaticProperty"),
                v5 = GenericClassWithDefault(null),
                v6 = GenericClassWithDefault(4711),
                v7 = LocalDate.MIN,
                v8 = LocalTime.MIN,
                v9 = LocalDateTime.MIN,
                v10 = WithCompanionFunction("v10"),
                v11 = WithCompanionFunction("OtherJsonDefaultValue"),
                v12 = ClassWithConstructorAsDefault("ClassWithConstructorAsDefault"),
                v13 = GenericClassWithConstructorAsDefault(null),
                v14 = 4711,
                v15 = SomeEnum.VALUE3,
                v16 = emptyMap())

        val actual = moshi.adapter(ClassWithDefaultValues::class.java).fromJson("""{
             |  "v1": null,
             |  "v2": null,
             |  "v3": null,
             |  "v4": null,
             |  "v5": null,
             |  "v6": null,
             |  "v7": null,
             |  "v8": null,
             |  "v9": null,
             |  "v10": {
             |    "v": "v10"
             |  },
             |  "v11": null,
             |  "v12": null,
             |  "v13": null,
             |  "v14": null,
             |  "v15": null,
             |  "v16": null
             |}""".trimMargin())

        assertEquals(expected, actual)
    }

    @Test
    fun withAbsentValues() {
        val expected = ClassWithDefaultValues(
                v1 = WithCompanionFunction("WithCompanionFunction"),
                v2 = WithStaticFunction("WithStaticFunction"),
                v3 = WithCompanionProperty("WithCompanionProperty"),
                v4 = WithStaticProperty("WithStaticProperty"),
                v5 = GenericClassWithDefault(null),
                v6 = GenericClassWithDefault(4711),
                v7 = LocalDate.MIN,
                v8 = LocalTime.MIN,
                v9 = LocalDateTime.MIN,
                v10 = WithCompanionFunction("v10"),
                v11 = WithCompanionFunction("OtherJsonDefaultValue"),
                v12 = ClassWithConstructorAsDefault("ClassWithConstructorAsDefault"),
                v13 = GenericClassWithConstructorAsDefault(null),
                v14 = 4711,
                v15 = SomeEnum.VALUE3,
                v16 = emptyMap())

        val actual = moshi.adapter(ClassWithDefaultValues::class.java).fromJson("""{
             |  "v10": {
             |    "v": "v10"
             |  }
        |}""".trimMargin())
        assertEquals(expected, actual)
    }

    @Test
    fun throwsNPEWhenNotUsingDefaultValues() {
        try {
            moshi.adapter(ClassWithDefaultValues::class.java).fromJson("{}")
            fail()
        } catch (e: NullPointerException) {
            assertEquals("The following properties were null: v10", e.message)
        }
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

    object LocalDateAdapter : JsonAdapter<LocalDate>() {
        override fun fromJson(reader: JsonReader): LocalDate? =
                if (reader.peek() == JsonReader.Token.NULL) reader.nextNull() else LocalDate.parse(reader.nextString())

        override fun toJson(writer: JsonWriter, value: LocalDate?) {
            writer.value(value?.toString())
        }
    }

    object LocalTimeAdapter : JsonAdapter<LocalTime>() {
        override fun fromJson(reader: JsonReader): LocalTime? =
                if (reader.peek() == JsonReader.Token.NULL) reader.nextNull() else LocalTime.parse(reader.nextString())

        override fun toJson(writer: JsonWriter, value: LocalTime?) {
            writer.value(value?.toString())
        }
    }

    object LocalDateTimeAdapter : JsonAdapter<LocalDateTime>() {
        override fun fromJson(reader: JsonReader): LocalDateTime? =
                if (reader.peek() == JsonReader.Token.NULL) reader.nextNull() else LocalDateTime.parse(reader.nextString())

        override fun toJson(writer: JsonWriter, value: LocalDateTime?) {
            writer.value(value?.toString())
        }
    }
}
