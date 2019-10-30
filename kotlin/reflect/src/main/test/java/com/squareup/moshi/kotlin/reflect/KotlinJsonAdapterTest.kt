package com.squareup.moshi.kotlin.reflect

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class KotlinJsonAdapterTest {
  @JsonClass(generateAdapter = true)
  class Data

  @ExperimentalStdlibApi
  @Test
  fun fallsBackToReflectiveAdapterWithoutCodegen() {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    val adapter = moshi.adapter(Data::class.java)
    assertThat(adapter.toString()).isEqualTo(
        "KotlinJsonAdapter(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterTest.Data).nullSafe()"
    )
  }
}
