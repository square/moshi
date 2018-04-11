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
package com.squareup.moshi.recipes;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import com.squareup.moshi.recipes.models.Card;
import com.squareup.moshi.recipes.models.Suit;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class MultipleFormats {
  public void run() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new MultipleFormatsCardAdapter())
        .add(new CardStringAdapter())
        .build();

    JsonAdapter<Card> cardAdapter = moshi.adapter(Card.class);

    // Decode cards from one format or the other.
    System.out.println(cardAdapter.fromJson("\"5D\""));
    System.out.println(cardAdapter.fromJson("{\"suit\": \"SPADES\", \"rank\": 5}"));

    // Cards are always encoded as strings.
    System.out.println(cardAdapter.toJson(new Card('5', Suit.CLUBS)));
  }

  /** Handles cards either as strings "5D" or as objects {"suit": "SPADES", "rank": 5}. */
  public final class MultipleFormatsCardAdapter {
    @ToJson void toJson(JsonWriter writer, Card value,
        @CardString JsonAdapter<Card> stringAdapter) throws IOException {
      stringAdapter.toJson(writer, value);
    }

    @FromJson Card fromJson(JsonReader reader, @CardString JsonAdapter<Card> stringAdapter,
        JsonAdapter<Card> defaultAdapter) throws IOException {
      if (reader.peek() == JsonReader.Token.STRING) {
        return stringAdapter.fromJson(reader);
      } else {
        return defaultAdapter.fromJson(reader);
      }
    }
  }

  /** Handles cards as strings only. */
  public final class CardStringAdapter {
    @ToJson String toJson(@CardString Card card) {
      return card.rank + card.suit.name().substring(0, 1);
    }

    @FromJson @CardString Card fromJson(String card) {
      if (card.length() != 2) throw new JsonDataException("Unknown card: " + card);

      char rank = card.charAt(0);
      switch (card.charAt(1)) {
        case 'C': return new Card(rank, Suit.CLUBS);
        case 'D': return new Card(rank, Suit.DIAMONDS);
        case 'H': return new Card(rank, Suit.HEARTS);
        case 'S': return new Card(rank, Suit.SPADES);
        default: throw new JsonDataException("unknown suit: " + card);
      }
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @JsonQualifier
  @interface CardString {
  }

  public static void main(String[] args) throws Exception {
    new MultipleFormats().run();
  }
}
