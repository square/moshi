package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class ClassWithPrimitiveDefaults(
    val someString: String = "default",
    val someBoolean: Boolean = true,
    val someByte: Byte = 0x42,
    val someChar: Char = 'N',
    val someShort: Short = 4711,
    val someInt: Int = 4711,
    val someLong: Long = 4711,
    val someFloat: Float = 0.4711f,
    val someDouble: Double = 0.4711
)
