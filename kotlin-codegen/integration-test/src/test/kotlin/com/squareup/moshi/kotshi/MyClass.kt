package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@JsonDefaultValue
annotation class StringWithNA

@MoshiSerializable
data class MyClass(
        @JsonDefaultValue
        val name: String,
        @StringWithNA
        val address: String,
        @JsonDefaultValueInt(-1)
        val age: Int
) {
    companion object {
        @JsonDefaultValue
        @JvmField
        val defaultString = ""

        @StringWithNA
        fun defaultStringWithNA() = "N/A"
    }
}
