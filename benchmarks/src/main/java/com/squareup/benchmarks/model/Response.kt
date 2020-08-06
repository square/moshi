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
package com.squareup.benchmarks.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Response @JvmOverloads constructor(
        @Json(name = "_id")
        @field:SerializedName("_id")
        @field:Json(name = "_id")
        @get:JsonProperty("_id")
        @set:JsonProperty("_id")
        var id: String = "",
        var index: Int = -1,
        var guid: String = "",
        @get:JsonProperty("isActive")
        @set:JsonProperty("isActive")
        var isActive: Boolean = false,
        var balance: String = "",
        var picture: String = "",
        var age: Int = -1,
        var eyeColor: EyeColor = EyeColor.NOT_SELECTED,
        var name: Name = Name("John", "Doe"),
        var company: String = "",
        var email: String = "",
        var phone: String = "",
        var address: String = "",
        var about: String = "",
        var registered: String = "",
        var latitude: Double = -1.0,
        var longitude: Double = -1.0,
        var tags: List<String> = emptyList(),
        var range: List<Int> = emptyList(),
        var friends: List<Friend> = emptyList(),
        var greeting: String = "",
        var favoriteFruit: Fruit = Fruit.NOT_SELECTED
)
