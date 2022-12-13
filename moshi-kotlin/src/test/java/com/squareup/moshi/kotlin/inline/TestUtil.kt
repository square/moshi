package com.squareup.moshi.kotlin.inline

import com.squareup.moshi.Moshi

inline fun <reified T : Any> Moshi.serialize(value: T): String = adapter(T::class.java).toJson(value)

fun <T : Any> Moshi.deserialize(value: String, type: Class<T>): T = adapter(type).fromJson(value)!!
