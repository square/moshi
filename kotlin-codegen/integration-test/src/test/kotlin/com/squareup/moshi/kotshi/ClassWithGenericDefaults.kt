package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable

@MoshiSerializable
data class ClassWithGenericDefaults(
    val generic2: Generic2<String?, Int?>
) {

  open class Generic2<out T1, out T2>(val t1: T1, val t2: T2)
  class Generic1<out T>(t1: T, t2: String?) : Generic2<T, String?>(t1, t2)

}
