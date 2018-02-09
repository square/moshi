package com.squareup.moshi.kotshi

import com.squareup.moshi.Json
import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class CustomNames(
        @get:JvmName("prop1") /*@GetterName("prop1") */ @Json(name = "jsonProp1") val prop1: String,
        @get:JvmName("prop2") /*@GetterName("prop2") */ @field:Json(name = "jsonProp2") val prop2: String
)
