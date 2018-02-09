package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable(useAdaptersForPrimitives = PrimitiveAdapters.ENABLED)
data class UsingPrimitiveAdapterTestClass(
        val aString: String,
        val aBoolean: Boolean,
        val aNullableBoolean: Boolean?,
        val aByte: Byte,
        val nullableByte: Byte?,
        val aChar: Char,
        val nullableChar: Char?,
        val aShort: Short,
        val nullableShort: Short?,
        val integer: Int,
        val nullableInteger: Int?,
        val aLong: Long,
        val nullableLong: Long?,
        val aFloat: Float,
        val nullableFloat: Float?,
        val aDouble: Double,
        val nullableDouble: Double?
)

@MoshiSerializable(useAdaptersForPrimitives = PrimitiveAdapters.DISABLED)
data class NotUsingPrimitiveAdapterTestClass(
        val aString: String,
        val aBoolean: Boolean,
        val aNullableBoolean: Boolean?,
        val aByte: Byte,
        val nullableByte: Byte?,
        val aChar: Char,
        val nullableChar: Char?,
        val aShort: Short,
        val nullableShort: Short?,
        val integer: Int,
        val nullableInteger: Int?,
        val aLong: Long,
        val nullableLong: Long?,
        val aFloat: Float,
        val nullableFloat: Float?,
        val aDouble: Double,
        val nullableDouble: Double?
)

@MoshiSerializable(useAdaptersForPrimitives = PrimitiveAdapters.DISABLED)
data class PrimitiveWithJsonQualifierTestClass(
    @Hello val greetingInt: Int
)
