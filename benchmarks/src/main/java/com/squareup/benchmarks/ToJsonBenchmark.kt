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
package com.squareup.benchmarks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.benchmarks.model.Response
import com.squareup.moshi.JsonAdapter
import okio.BufferedSink
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

@Suppress("FunctionName")
@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class ToJsonBenchmark {

    private val gson = Gson()
    private val type = object : TypeToken<List<Response>>() {}.type

    private val moshiAdapter: JsonAdapter<List<Response>> = moshi.listAdapter()

    private lateinit var outputStreamWriter: OutputStreamWriter

    private val moshiResponse: List<Response> = json().toBufferedSource().use {
        moshiAdapter.fromJson(it)!!
    }

    private val gsonResponse: List<Response> = json().reader().use {
        gson.fromJson<List<Response>>(it, type)!!
    }

    private val objectMapper = ObjectMapper()
    private val typeReference = object: TypeReference<List<Response>>(){}

    private lateinit var outputStream: OutputStream

    private val response: List<Response> = json().use {
        objectMapper.readValue<List<Response>>(it, typeReference)!!
    }

    private lateinit var outputBufferedSink: BufferedSink

    @Setup(Level.Invocation)
    fun setup() {
        outputStream = ByteArrayOutputStream()
        outputBufferedSink = outputStream.toBufferedSink()
        outputStreamWriter = outputStream.writer()
    }

    @Benchmark
    fun moshi_toJson() {
        outputBufferedSink.use {
            moshiAdapter.toJson(it, moshiResponse)
        }
    }

    @Benchmark
    fun gson_toJson() {
        outputStreamWriter.use {
            gson.toJson(gsonResponse, type, it)
        }
    }

    @Benchmark
    fun jackson_toJson() {
        outputStream.use {
            objectMapper.writeValue(it, response)
        }
    }
}
