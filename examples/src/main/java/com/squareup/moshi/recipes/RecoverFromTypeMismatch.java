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
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.recipes.models.Suit;
import java.util.List;

public final class RecoverFromTypeMismatch {
  public void run() throws Exception {
    String json = "[\"DIAMONDS\", \"STARS\", \"HEARTS\"]";

    Moshi moshi = new Moshi.Builder()
        .add(DefaultOnDataMismatchAdapter.newFactory(Suit.class, Suit.CLUBS))
        .build();
    JsonAdapter<List<Suit>> jsonAdapter = moshi.adapter(
        Types.newParameterizedType(List.class, Suit.class));

    List<Suit> suits = jsonAdapter.fromJson(json);
    System.out.println(suits);
  }

  public static void main(String[] args) throws Exception {
    new RecoverFromTypeMismatch().run();
  }
}
