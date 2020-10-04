/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
