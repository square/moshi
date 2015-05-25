/*
 * Copyright (C) 2014 Square, Inc.
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
package com.squareup.moshi;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumJsonAdapterTest {
  private final Moshi moshi = new Moshi.Builder().build();

  enum Roshambo {
    ROCK,
    PAPER,
    SCISSORS
  }

  @Test public void basics() throws Exception {
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class).lenient();
    assertThat(adapter.fromJson("\"ROCK\"")).isEqualTo(Roshambo.ROCK);
    assertThat(adapter.toJson(Roshambo.PAPER)).isEqualTo("\"PAPER\"");
  }

  @Test public void invalidInput() throws Exception {
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class).lenient();
    assertThat(adapter.fromJson("\"Lizard\"")).isNull();
    assertThat(adapter.fromJson("\"SPOCK\"")).isNull();
  }

  @Test public void nulls() throws Exception {
    JsonAdapter<Roshambo> adapter = moshi.adapter(Roshambo.class).lenient();
    assertThat(adapter.fromJson("null")).isNull();
    assertThat(adapter.toJson(null)).isEqualTo("null");
  }
}