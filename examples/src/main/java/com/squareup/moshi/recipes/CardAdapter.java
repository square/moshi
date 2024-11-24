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
package com.squareup.moshi.recipes;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.ToJson;
import com.squareup.moshi.recipes.models.Card;
import com.squareup.moshi.recipes.models.Suit;

import java.util.Map;

public final class CardAdapter {

  private static final Map<Character, Suit> SUIT_MAP = Map.of(
      'C', Suit.CLUBS,
      'D', Suit.DIAMONDS,
      'H', Suit.HEARTS,
      'S', Suit.SPADES);

  @ToJson
  String toJson(Card card) {
    if (card == null || card.suit == null || card.rank == 0) {
      throw new JsonDataException("Invalid card: " + card);
    }
    return card.rank + card.suit.name().substring(0, 1);
  }

  @FromJson
  Card fromJson(String card) {
    if (card == null || card.length() != 2 || !Character.isDigit(card.charAt(0))) {
      throw new JsonDataException("Invalid card format: " + card);
    }

    char rank = card.charAt(0);
    Suit suit = SUIT_MAP.get(card.charAt(1));
    if (suit == null) {
      throw new JsonDataException("Unknown suit: " + card.charAt(1));
    }

    return new Card(rank, suit);
  }
}
