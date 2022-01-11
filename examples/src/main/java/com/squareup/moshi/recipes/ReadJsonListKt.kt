/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.moshi.recipes

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.recipes.models.Card

class ReadJsonListKt {

  //language=JSON
  private val jsonString = """
    [{"rank": "4",
       "suit": "CLUBS"
     },
     {"rank": "A",
      "suit": "HEARTS"
      },
     {"rank": "J",
      "suit": "SPADES"
     }]
  """.trimIndent()

  fun readJsonList() {
    val jsonAdapter = Moshi.Builder().build().adapter<List<Card>>()
    val cards = jsonAdapter.fromJson(jsonString)!!
    println(cards)
    cards[0].run {
      println(rank)
      println(suit)
    }
  }
}

fun main() {
  ReadJsonListKt().readJsonList()
}
