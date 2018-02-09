package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class ClassWithBoundGeneric<out Data>(val data: List<Data>)

@MoshiSerializable
data class ParameterizedModel<out Data>(val data: Data)

@MoshiSerializable
data class TripleParameterizedModel<out Data, out T : CharSequence, out E : Any>(
        val e: E,
        val data: Data,
        val t: T
)

@MoshiSerializable
data class TypeCeption<out D : Any>(
        val tpm: TripleParameterizedModel<ParameterizedModel<IntArray>, String, D>,
        val pm: ParameterizedModel<List<D>>
)
