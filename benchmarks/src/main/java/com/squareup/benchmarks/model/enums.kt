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

enum class EyeColor {
    @Json(name = "blue")
    @SerializedName("blue")
    @JsonProperty("blue")
    BLUE,

    @Json(name = "brown")
    @SerializedName("brown")
    @JsonProperty("brown")
    BROWN,

    @Json(name = "green")
    @SerializedName("green")
    @JsonProperty("green")
    GREEN,

    NOT_SELECTED
}

enum class Fruit {
    @Json(name = "apple")
    @SerializedName("apple")
    @JsonProperty("apple")
    APPLE,

    @Json(name = "banana")
    @SerializedName("banana")
    @JsonProperty("banana")
    BANANA,

    @Json(name = "strawberry")
    @SerializedName("strawberry")
    @JsonProperty("strawberry")
    STRAWBERRY,

    NOT_SELECTED
}
