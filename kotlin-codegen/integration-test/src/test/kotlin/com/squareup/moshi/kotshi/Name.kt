package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class Name @KotshiConstructor constructor(val firstName: String, val lastName: String) {
    constructor(fullName: String) : this(fullName.substringBefore(" "), fullName.substringAfter(" "))
}
