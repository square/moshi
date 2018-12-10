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

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.*

val moshi: Moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

/**
 * A little magic for kotlin to only have to provide the type we want instead of the class file.
 *
 * @param T the type the adapter is for.
 */
inline fun <reified T> Moshi.adapter(): JsonAdapter<T> = adapter<T>(T::class.java)

/**
 * Like [adapter] but for a List type.
 * Note: This will only work for a single generic type.
 *
 * @param T the type the [List] will contain.
 * @return JsonAdapter<List<T>>
 */
inline fun <reified T> Moshi.listAdapter(): JsonAdapter<List<T>> =
        adapter<List<T>>(Types.newParameterizedType(List::class.java, T::class.java))
