/*
 * Copyright (C) 2018 Square, Inc.
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
package com.squareup.moshi

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.annotation.processing.Processor

/** Execute kotlinc to confirm that either files are generated or errors are printed. */
class CompilerTest {
  @Rule @JvmField var temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun test() {
    val call = KotlinCompilerCall(temporaryFolder.root)
    call.inheritClasspath = true
    call.addService(Processor::class, JsonClassCodeGenProcessor::class)
    call.addKt("source.kt", """
        |import com.squareup.moshi.JsonClass
        |
        |@JsonClass(generateAdapter = true)
        |class PrivateConstructorParameter(private var a: Int)
        |""".trimMargin())

    val result = call.execute()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.systemErr).contains("property a is not visible")
  }
}