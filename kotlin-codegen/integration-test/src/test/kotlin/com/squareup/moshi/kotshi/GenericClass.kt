package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class GenericClass<out T: CharSequence, out C: Collection<T>>(val collection: C, val value: T)
