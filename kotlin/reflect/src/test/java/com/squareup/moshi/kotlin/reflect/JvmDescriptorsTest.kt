package com.squareup.moshi.kotlin.reflect

import com.google.common.truth.Truth.assertThat
import kotlinx.metadata.jvm.JvmMethodSignature
import org.junit.Test

class JvmDescriptorsTest {

  private fun getTypesOf(desc: String): List<Class<*>> {
    return JvmMethodSignature("test", desc).decodeParameterTypes()
  }

  @Test
  fun readTypes_primitives() {
    assertThat(getTypesOf("(BCDFIJSZV)V"))
      .containsExactly(
        Byte::class.javaPrimitiveType,
        Char::class.javaPrimitiveType,
        Double::class.javaPrimitiveType,
        Float::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        Short::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Void::class.javaPrimitiveType
      )
      .inOrder()
  }

  @Test
  fun readTypes_arrays() {
    assertThat(getTypesOf("([B)V"))
      .containsExactly(ByteArray::class.java)
    assertThat(getTypesOf("([[B)V"))
      .containsExactly(Array<ByteArray>::class.java)
    assertThat(getTypesOf("([[[B)V"))
      .containsExactly(Array<Array<ByteArray>>::class.java)
    assertThat(getTypesOf("([[[Ljava/lang/Byte;)V"))
      .containsExactly(Array<Array<Array<Byte>>>::class.java)
  }

  @Test
  fun empty() {
    assertThat(getTypesOf("()V")).isEmpty()
  }

  @Test
  fun single() {
    assertThat(getTypesOf("(B)V")).containsExactly(Byte::class.javaPrimitiveType)
  }
}
