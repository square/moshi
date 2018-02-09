package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class ClassWithGenericDefaults(
        @JsonDefaultValue
        val generic2: Generic2<String?, Int?>
) {

    open class Generic2<out T1, out T2>(val t1: T1, val t2: T2)
    class Generic1<out T>(t1: T, t2: String?) : Generic2<T, String?>(t1, t2)

    companion object {
        @JsonDefaultValue
        fun <T> provideGeneric1Default(): Generic1<T?> = Generic1(null, null)

        @JsonDefaultValue
        fun <T1, T2> provideGeneric2Default(): Generic2<T1?, T2?> = Generic2(null, null)
    }

}
