/*
 * Copyright 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.recipes.models.Card;
import java.lang.reflect.Type;
import java.util.List;

public final class ReadJsonList {
  public void run() throws Exception {
    String json = ""
        + "[\n"
        + "  {\n"
        + "    \"rank\": \"4\",\n"
        + "    \"suit\": \"CLUBS\"\n"
        + "  },\n"
        + "  {\n"
        + "    \"rank\": \"A\",\n"
        + "    \"suit\": \"HEARTS\"\n"
        + "  },\n"
        + "  {\n"
        + "    \"rank\": \"J\",\n"
        + "    \"suit\": \"SPADES\"\n"
        + "  }\n"
        + "]";

    Moshi moshi = new Moshi.Builder().build();

    Type listOfCardsType = Types.newParameterizedType(List.class, Card.class);
    JsonAdapter<List<Card>> jsonAdapter = moshi.adapter(listOfCardsType);

    List<Card> cards = jsonAdapter.fromJson(json);
    System.out.println(cards);
  }

  public static void main(String[] args) throws Exception {
    new ReadJsonList().run();
  }
}
