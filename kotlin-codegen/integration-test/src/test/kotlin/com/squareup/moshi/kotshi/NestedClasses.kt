package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class NestedClasses(val inner: Inner) {
    @MoshiSerializable
    data class Inner(val prop: String)
}
