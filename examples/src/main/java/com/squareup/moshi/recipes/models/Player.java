/*
 * Copyright (C) 2016 Square, Inc.
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

import com.squareup.moshi.Json;

public final class Player {
  public final String username;
  public final @Json(name = "lucky number") int luckyNumber;

  public Player(String username, int luckyNumber) {
    this.username = username;
    this.luckyNumber = luckyNumber;
  }

  @Override public String toString() {
    return username + " gets lucky with " + luckyNumber;
  }
}
