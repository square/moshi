package com.squareup.moshi.kotlin.inline

@JvmInline
value class JvmInlineNullableString(val value: String?)

@JvmInline
value class JvmInlineString(val value: String) {
  constructor(
    baseString: String,
    appendedString: String,
  ) : this(baseString + appendedString)

  val secondValue: Char
    get() = value.first()
}

@JvmInline
value class JvmInlineInt(val value: Int)

@JvmInline
value class JvmInlineUInt(val unsignedValue: UInt)

data class DataClassWithUIntAndString(
  val stringValue: String,
  val unsignedValue: UInt,
)

@JvmInline
value class JvmInlineDouble(val value: Double)

@JvmInline
value class JvmInlineComplexClass(
  val value: ExampleNestedClass,
) {
  data class ExampleNestedClass(
    val stringValue: String,
    val intValue: Int,
  )
}

@JvmInline
value class JvmInlineListInt(val list: List<Int>)

@JvmInline
value class JvmInlineMapStringNullableInt(val map: Map<String, Int?>)

@JvmInline
value class JvmInlineMapComplexClass(val parameterizedValue: Map<String, JvmInlineComplexClass>)

@JvmInline
value class JvmInlineComplexClassWithParameterizedField(
  val value: ExampleNestedClassWithParameterizedField,
) {
  data class ExampleNestedClassWithParameterizedField(
    val strings: List<String>,
    val ints: List<Int>,
  )
}

data class DataClassWithUInt(val uInt: UInt)

data class DataClassWithULong(val uLong: ULong)

data class DataClassWithUShort(val uShort: UShort)

data class DataClassWithUByte(val uByte: UByte)

data class DataClassWithNullableULong(val nullableULong: ULong?)
