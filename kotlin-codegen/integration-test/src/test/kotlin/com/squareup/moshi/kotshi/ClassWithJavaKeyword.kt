package com.squareup.moshi.kotshi

import com.squareup.moshi.Json
import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class ClassWithJavaKeyword(
//        @GetterName("getDefault")
    @Json(name = "default")
    val default: Boolean,
//        @GetterName("getInt")
    @Json(name = "int")
    val int: Int,
//        @GetterName("someCase")
    @Json(name = "case")
    @get:JvmName("someCase")
    val case: Int
)
