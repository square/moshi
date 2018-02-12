package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

annotation class StringWithNA

@MoshiSerializable
data class MyClass(
    val name: String = "",
    @StringWithNA
    val address: String, // = "N/A",
    val age: Int
)
