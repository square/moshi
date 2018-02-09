package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class GenericClassWithQualifier<out T>(@Hello val value: T)
