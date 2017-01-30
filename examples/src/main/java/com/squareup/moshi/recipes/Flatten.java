/*
 * Copyright (C) 2017 Square, Inc.
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
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.recipes.models.Card;
import com.squareup.moshi.recipes.models.Suit;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

final class Flatten {
  public static void main(String[] args) throws Exception {
    String json = ""
        + "{\n"
        + "  \"rank\": 4,\n"
        + "  \"suit_info\": {\n"
        + "    \"suit\": \"CLUBS\"\n"
        + "  }\n"
        + "}";
    Moshi moshi = new Moshi.Builder().add(CardJsonAdapter.FACTORY).build();
    JsonAdapter<Card> adapter = moshi.adapter(Card.class, Collections.<Annotation>emptySet());
    Card out = adapter.fromJson(json);
    System.out.println(out);
  }

  public static final class CardJsonAdapter extends JsonAdapter<Card> {
    public static final JsonAdapter.Factory FACTORY = new Factory() {
      @Override
      public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if (type != Card.class) {
          return null;
        }
        JsonAdapter<CardIntermediate> delegate =
            moshi.nextAdapter(this, CardIntermediate.class, annotations);
        return new CardJsonAdapter(delegate);
      }
    };

    private final JsonAdapter<CardIntermediate> delegate;

    CardJsonAdapter(JsonAdapter<CardIntermediate> delegate) {
      this.delegate = delegate;
    }

    @Override public Card fromJson(JsonReader reader) throws IOException {
      CardIntermediate intermediate = delegate.fromJson(reader);
      return new Card(intermediate.rank, intermediate.suit_info.suit);
    }

    @Override public void toJson(JsonWriter writer, Card value) throws IOException {
      delegate.toJson(writer,
          new CardIntermediate(value.rank, new CardIntermediate.SuitInfo(value.suit)));
    }

    private static final class CardIntermediate {
      final char rank;
      final SuitInfo suit_info;

      CardIntermediate(char rank, SuitInfo suit_info) {
        this.rank = rank;
        this.suit_info = suit_info;
      }

      static final class SuitInfo {
        final Suit suit;

        SuitInfo(Suit suit) {
          this.suit = suit;
        }
      }
    }
  }
}
