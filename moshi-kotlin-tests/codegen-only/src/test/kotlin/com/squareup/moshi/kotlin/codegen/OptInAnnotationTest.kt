package com.squareup.moshi.kotlin.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import org.junit.Test

class OptInAnnotationTest {

  @RequiresOptIn
  @Retention(AnnotationRetention.RUNTIME)
  annotation class ExperimentalThings

  @ExperimentalThings
  @JsonClass(generateAdapter = true)
  data class ExperimentalClass(val a: Int)

  @Test
  fun optInAnnotationIsPropagatedToAdapter() {
    val adapterClass = Class.forName("com.squareup.moshi.kotlin.codegen.OptInAnnotationTest_ExperimentalClassJsonAdapter")
    
    val annotation = adapterClass.getAnnotation(ExperimentalThings::class.java)
    assertThat(annotation).isNotNull()
  }
}
