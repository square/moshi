package com.squareup.moshi.kotlin.codegen

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test

class MixingReflectAndCodeGen {
  @Test
  fun mixingReflectionAndCodegen() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val generatedAdapter = moshi.adapter<UsesGeneratedAdapter>()
    val reflectionAdapter = moshi.adapter<UsesReflectionAdapter>()

    assertThat(generatedAdapter.toString())
      .isEqualTo("GeneratedJsonAdapter(KotlinJsonAdapterTest.UsesGeneratedAdapter).nullSafe()")
    assertThat(reflectionAdapter.toString())
      .isEqualTo(
        "KotlinJsonAdapter(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest" +
          ".UsesReflectionAdapter).nullSafe()"
      )
  }

  @JsonClass(generateAdapter = true)
  class UsesGeneratedAdapter(var a: Int, var b: Int)

  @JsonClass(generateAdapter = false)
  class UsesReflectionAdapter(var a: Int, var b: Int)
}
