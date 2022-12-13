package com.squareup.moshi.kotlin.inline

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.startsWith
import assertk.fail
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private val moshi: Moshi = Moshi.Builder()
  .add(UnsignedNumberJsonAdapter.Factory)
  .addLast(KotlinJsonAdapterFactory())
  .build()

@Language("JSON")
private val unsignedToStringRepresentation: Map<Any, String> = mapOf(
  dataClassWithULong to """{"uLong":${dataClassWithULong.uLong}}""",
  dataClassWithUInt to """{"uInt":${dataClassWithUInt.uInt}}""",
  dataClassWithUShort to """{"uShort":${dataClassWithUShort.uShort}}""",
  dataClassWithUByte to """{"uByte":${dataClassWithUByte.uByte}}""",
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnsignedNumberJsonAdapterTest {
  private fun assertSerializedDeserialized(original: Any) =
    when (val stringRepresentation = unsignedToStringRepresentation[original]) {
      null -> fail("Missing string representation of $original")
      else -> {
        val actual = moshi.adapter(original::class.java).fromJson(stringRepresentation)!!
        assertThat(actual).isEqualTo(original)
        actual::class.java.declaredFields.map { field ->
          assertThat(field.genericType).isEqualTo(
            original::class.java.declaredFields
              .find { it.name == field.name }?.genericType
          )
        }
      }
    }

  @Suppress("unused")
  private fun unsignedNumbers(): Stream<Arguments> = Stream.of(
    Arguments.of(dataClassWithULong),
    Arguments.of(dataClassWithUInt),
    Arguments.of(dataClassWithUShort),
    Arguments.of(dataClassWithUByte),
  )

  @Suppress("unused")
  private fun dataClassPropertyNames(): Stream<Arguments> = Stream.of(
    Arguments.of(DataClassWithULong::class.java, "uLong"),
    Arguments.of(DataClassWithUInt::class.java, "uInt"),
    Arguments.of(DataClassWithUShort::class.java, "uShort"),
    Arguments.of(DataClassWithUByte::class.java, "uByte"),
  )

  @ParameterizedTest
  @MethodSource("unsignedNumbers")
  fun `When an unsigned value would be negative if it was signed, then it's positivity is properly retained`(
    value: Any,
  ) {
    assertSerializedDeserialized(value)
  }

  @ParameterizedTest
  @MethodSource("dataClassPropertyNames")
  fun `When a negative value as attempted to be deserialized, then an exception is thrown`(
    type: Class<*>,
    propertyName: String,
  ) {
    val negativeValue = -1

    @Language("JSON")
    val stringRepresentation = """{"$propertyName":$negativeValue}"""
    assertThat(
      requireNotNull(
        assertThrows<JsonDataException> {
          moshi.adapter(type).fromJson(stringRepresentation)
        }.message
      )
    ).startsWith("Invalid number format: '-1' for unsigned number at $.")
  }

  @Test
  fun `When a unsigned value is larger than it's max, then an exception is thrown`() {
    @Language("JSON")
    val overflowingMap = mapOf(
      DataClassWithULong::class.java to """{"uLong": 18446744100000000000}""",
      DataClassWithUInt::class.java to """{"uInt": 4295032828}""",
      DataClassWithUShort::class.java to """{"uShort": 65788}""",
      DataClassWithUByte::class.java to """{"uByte": 274}"""
    )

    overflowingMap.forEach { (type, string) ->
      assertThat(
        requireNotNull(
          assertThrows<JsonDataException> {
            moshi.deserialize(string, type)
          }.message
        )
      ).startsWith("Invalid number format:")
    }
  }

  @Test
  fun `When a unsigned field is not null nullable, and the value is null, then an exception is thrown`() {
    @Language("JSON")
    val stringRepresentation = """{"uLong": "10"}"""
    assertThat(
      requireNotNull(
        assertThrows<JsonDataException> {
          moshi.deserialize(stringRepresentation, DataClassWithULong::class.java)
        }.message
      )
    ).isEqualTo("Expected an unsigned number but was 10, a STRING, at path $.uLong")
  }

  @Test
  fun `When a unsigned field gets a not null not number token, then an exception is thrown`() {
    @Language("JSON")
    val stringRepresentation = """{"uLong": null}"""
    assertThat(
      requireNotNull(
        assertThrows<JsonDataException> {
          moshi.deserialize(stringRepresentation, DataClassWithULong::class.java)
        }.message
      )
    ).isEqualTo("Non-null value 'uLong' was null at $.uLong")
  }

  @Test
  fun `When a unsigned field is nullable, and the value is not null, it is deserialized properly`() {
    @Language("JSON")
    val stringRepresentation = """{"nullableULong": ${dataClassWithULong.uLong}}"""
    assertThat(
      moshi.deserialize(stringRepresentation, DataClassWithNullableULong::class.java)
        .nullableULong
    ).isEqualTo(dataClassWithULong.uLong)
  }

  @Test
  fun `When a unsigned field is nullable, and the value is null, it is deserialized properly`() {
    @Language("JSON")
    val stringRepresentation = """{"nullableULong": null}"""
    assertThat(
      moshi.deserialize(stringRepresentation, DataClassWithNullableULong::class.java)
        .nullableULong
    ).isNull()
  }
}
