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
package com.squareup.moshi.recipes.models;

import java.util.List;

@SuppressWarnings("checkstyle:membername")
public final class BlackjackHand {
  public final Card hidden_card;
  public final List<Card> visible_cards;

  public BlackjackHand(Card hiddenCard, List<Card> visibleCards) {
    this.hidden_card = hiddenCard;
    this.visible_cards = visibleCards;
  }

  @Override public String toString() {
    return "hidden=" + hidden_card + ",visible=" + visible_cards;
  }
}
