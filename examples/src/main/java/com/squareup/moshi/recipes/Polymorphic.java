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
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.recipes.models.BlackjackHand;
import com.squareup.moshi.recipes.models.Card;
import com.squareup.moshi.recipes.models.Player;
import com.squareup.moshi.recipes.models.Suit;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

final class Polymorphic {
  public static void main(String[] args) throws Exception {
    Moshi moshi = new Moshi.Builder().add(PolymorphicBlackjackJsonAdapter.FACTORY)
        .add(new CardAdapter())
        .build();
    String handJson = "{\n"
        + "    \"kind\": \"blackjack-hand\",\n"
        + "    \"data\": {\n"
        + "      \"hidden_card\": \"6S\",\n"
        + "      \"visible_cards\": [\n"
        + "        \"4C\",\n"
        + "        \"AH\"\n"
        + "      ]\n"
        + "    }\n"
        + "  }";
    String playerJson = "{\n"
        + "    \"kind\": \"player\",\n"
        + "    \"data\": {\n"
        + "      \"username\": \"jesse\",\n"
        + "      \"lucky number\": 32\n"
        + "    }\n"
        + "  }";
    JsonAdapter<BlackjackHand> handAdapter = moshi.adapter(BlackjackHand.class);
    JsonAdapter<Player> playerAdapter = moshi.adapter(Player.class);
    System.out.println(handAdapter.fromJson(handJson));
    System.out.println(playerAdapter.fromJson(playerJson));
    System.out.println(handAdapter.toJson(
        new BlackjackHand(new Card('A', Suit.SPADES), Collections.<Card>emptyList())));
    System.out.println(playerAdapter.toJson(new Player("jake", -1)));
  }

  public static final class PolymorphicBlackjackJsonAdapter extends JsonAdapter<Object> {
    public static final JsonAdapter.Factory FACTORY = new Factory() {
      @Override
      public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if (type != BlackjackHand.class && type != Player.class) {
          return null;
        }
        JsonAdapter<BlackjackHand> handAdapter =
            moshi.nextAdapter(this, BlackjackHand.class, Collections.<Annotation>emptySet());
        JsonAdapter<Player> playerAdapter =
            moshi.nextAdapter(this, Player.class, Collections.<Annotation>emptySet());
        return new PolymorphicBlackjackJsonAdapter(handAdapter, playerAdapter);
      }
    };

    private final JsonAdapter<BlackjackHand> handAdapter;
    private final JsonAdapter<Player> playerAdapter;

    PolymorphicBlackjackJsonAdapter(JsonAdapter<BlackjackHand> handAdapter,
        JsonAdapter<Player> playerAdapter) {
      this.handAdapter = handAdapter;
      this.playerAdapter = playerAdapter;
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      // noinspection unchecked
      Map<String, Object> value = (Map<String, Object>) reader.readJsonValue();
      String type = (String) value.get("kind");
      Object data = value.get("data");
      switch (type) {
        case "blackjack-hand":
          return handAdapter.fromJsonValue(data);
        case "player":
          return playerAdapter.fromJsonValue(data);
        default:
          throw new JsonDataException("Unexpected type: " + type);
      }
    }

    @Override public void toJson(JsonWriter writer, Object value) throws IOException {
      Class<?> cls = value.getClass();
      writer.beginObject();
      writer.name("kind");
      String type;
      JsonAdapter adapter;
      if (cls == BlackjackHand.class) {
        type = "blackjack-hand";
        adapter = handAdapter;
      } else if (cls == Player.class) {
        type = "player";
        adapter = playerAdapter;
      } else {
        throw new AssertionError(cls + " is not BlackJackHand.class or Player.class.");
      }
      writer.value(type);
      writer.name("data");
      // noinspection unchecked
      adapter.toJson(writer, value);
      writer.endObject();
    }
  }
}
