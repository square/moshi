package com.squareup.moshi.kotshi

import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.MoshiSerializable

@JsonQualifier
annotation class WrappedInObject

@JsonQualifier
annotation class WrappedInArray

@MoshiSerializable
data class MultipleJsonQualifiers(@WrappedInObject @WrappedInArray val string: String)
