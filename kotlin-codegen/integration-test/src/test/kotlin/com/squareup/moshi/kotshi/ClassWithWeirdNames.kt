package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable(useAdaptersForPrimitives = PrimitiveAdapters.ENABLED)
data class ClassWithWeirdNames(
        val OPTIONS: Int,
        val writer: Boolean,
        val value: String,
        val reader: Char,
        val adapter1: Byte,
        val adapter2: Int?,
        val types: List<String>,
        val moshi: String
)
