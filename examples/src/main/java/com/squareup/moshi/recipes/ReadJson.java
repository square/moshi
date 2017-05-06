/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.recipes.models.BlackjackHand;

public final class ReadJson {
  public void run() throws Exception {
    String json = ""
        + "{\n"
        + "  \"hidden_card\": {\n"
        + "    \"rank\": \"6\",\n"
        + "    \"suit\": \"SPADES\"\n"
        + "  },\n"
        + "  \"visible_cards\": [\n"
        + "    {\n"
        + "      \"rank\": \"4\",\n"
        + "      \"suit\": \"CLUBS\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"rank\": \"A\",\n"
        + "      \"suit\": \"HEARTS\"\n"
        + "    }\n"
        + "  ]\n"
        + "}\n";

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<BlackjackHand> jsonAdapter = moshi.adapter(BlackjackHand.class);

    BlackjackHand blackjackHand = jsonAdapter.fromJson(json);
    System.out.println(blackjackHand);
  }

  public static void main(String[] args) throws Exception {
    new ReadJson().run();
  }
}
