/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.kotlin.codegen

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This test explicitly tests mask generation for classes with more than 32 parameters. Each mask
 * can only indicate up to 32 parameters, so constructors with more than 32 parameters have to use
 * multiple masks.
 *
 * This covers a few cases of this:
 * - Ensuring values from json are matched to properties correctly
 * - Some `@Transient` parameters (which participate in the constructor signature and mask indices)
 * - This example has 3 total masks generated.
 *
 * Regression test for https://github.com/square/moshi/issues/977
 */
class MultipleMasksTest {
  @Test fun testMultipleMasks() {

    // Set some arbitrary values to make sure offsets are aligning correctly
    @Language("JSON")
    val json = """{"arg50":500,"arg3":34,"arg11":11,"arg65":67}"""

    val instance = Moshi.Builder().build().adapter(MultipleMasks::class.java)
        .fromJson(json)!!

    assertEquals(instance.arg2, 2)
    assertEquals(instance.arg3, 34)
    assertEquals(instance.arg11, 11)
    assertEquals(instance.arg49, 49)
    assertEquals(instance.arg50, 500)
    assertEquals(instance.arg65, 67)
    assertEquals(instance.arg64, 64)
  }
}

@JsonClass(generateAdapter = true)
class MultipleMasks(
    val arg0: Long = 0,
    val arg1: Long = 1,
    val arg2: Long = 2,
    val arg3: Long = 3,
    val arg4: Long = 4,
    val arg5: Long = 5,
    val arg6: Long = 6,
    val arg7: Long = 7,
    val arg8: Long = 8,
    val arg9: Long = 9,
    val arg10: Long = 10,
    val arg11: Long,
    val arg12: Long = 12,
    val arg13: Long = 13,
    val arg14: Long = 14,
    val arg15: Long = 15,
    val arg16: Long = 16,
    val arg17: Long = 17,
    val arg18: Long = 18,
    val arg19: Long = 19,
    @Suppress("UNUSED_PARAMETER") arg20: Long = 20,
    val arg21: Long = 21,
    val arg22: Long = 22,
    val arg23: Long = 23,
    val arg24: Long = 24,
    val arg25: Long = 25,
    val arg26: Long = 26,
    val arg27: Long = 27,
    val arg28: Long = 28,
    val arg29: Long = 29,
    val arg30: Long = 30,
    val arg31: Long = 31,
    val arg32: Long = 32,
    val arg33: Long = 33,
    val arg34: Long = 34,
    val arg35: Long = 35,
    val arg36: Long = 36,
    val arg37: Long = 37,
    val arg38: Long = 38,
    @Transient val arg39: Long = 39,
    val arg40: Long = 40,
    val arg41: Long = 41,
    val arg42: Long = 42,
    val arg43: Long = 43,
    val arg44: Long = 44,
    val arg45: Long = 45,
    val arg46: Long = 46,
    val arg47: Long = 47,
    val arg48: Long = 48,
    val arg49: Long = 49,
    val arg50: Long = 50,
    val arg51: Long = 51,
    val arg52: Long = 52,
    @Transient val arg53: Long = 53,
    val arg54: Long = 54,
    val arg55: Long = 55,
    val arg56: Long = 56,
    val arg57: Long = 57,
    val arg58: Long = 58,
    val arg59: Long = 59,
    val arg60: Long = 60,
    val arg61: Long = 61,
    val arg62: Long = 62,
    val arg63: Long = 63,
    val arg64: Long = 64,
    val arg65: Long = 65
)
