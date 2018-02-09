package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class ClassWithPrimitiveDefaults(
        @JsonDefaultValueString("default")
        val someString: String,
        @JsonDefaultValueBoolean(true)
        val someBoolean: Boolean,
        @JsonDefaultValueByte(0x42)
        val someByte: Byte,
        @JsonDefaultValueChar('N')
        val someChar: Char,
        @JsonDefaultValueShort(4711)
        val someShort: Short,
        @JsonDefaultValueInt(4711)
        val someInt: Int,
        @JsonDefaultValueLong(4711)
        val someLong: Long,
        @JsonDefaultValueFloat(0.4711f)
        val someFloat: Float,
        @JsonDefaultValueDouble(0.4711)
        val someDouble: Double
)
