package com.squareup.moshi.kotshi

import com.squareup.moshi.Json
import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class ClassWithJavaKeyword(
    @Json(name = "default")
    val default: Boolean,
    @Json(name = "int")
    val int: Int,
    @Json(name = "case")
    @get:JvmName("someCase")
    val case: Int
)
