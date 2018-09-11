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
package com.squareup.moshi.adapters;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import okio.Buffer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@SuppressWarnings("CheckReturnValue")
public final class EnumJsonAdapterTest {
  @Test public void toAndFromJson() throws Exception {
    EnumJsonAdapter<Roshambo> adapter = EnumJsonAdapter.create(Roshambo.class);
    assertThat(adapter.fromJson("\"ROCK\"")).isEqualTo(Roshambo.ROCK);
    assertThat(adapter.toJson(Roshambo.PAPER)).isEqualTo("\"PAPER\"");
  }

  @Test public void withJsonName() throws Exception {
    EnumJsonAdapter<Roshambo> adapter = EnumJsonAdapter.create(Roshambo.class);
    assertThat(adapter.fromJson("\"scr\"")).isEqualTo(Roshambo.SCISSORS);
    assertThat(adapter.toJson(Roshambo.SCISSORS)).isEqualTo("\"scr\"");
  }

  @Test public void withoutFallbackValue() throws Exception {
    EnumJsonAdapter<Roshambo> adapter = EnumJsonAdapter.create(Roshambo.class);
    JsonReader reader = JsonReader.of(new Buffer().writeUtf8("\"SPOCK\""));
    try {
      adapter.fromJson(reader);
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage(
          "Expected one of [ROCK, PAPER, scr] but was SPOCK at path $");
    }
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void withFallbackValue() throws Exception {
    EnumJsonAdapter<Roshambo> adapter = EnumJsonAdapter.create(Roshambo.class)
        .withUnknownFallback(Roshambo.ROCK);
    JsonReader reader = JsonReader.of(new Buffer().writeUtf8("\"SPOCK\""));
    assertThat(adapter.fromJson(reader)).isEqualTo(Roshambo.ROCK);
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void withNullFallbackValue() throws Exception {
    EnumJsonAdapter<Roshambo> adapter = EnumJsonAdapter.create(Roshambo.class)
        .withUnknownFallback(null);
    JsonReader reader = JsonReader.of(new Buffer().writeUtf8("\"SPOCK\""));
    assertThat(adapter.fromJson(reader)).isNull();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  enum Roshambo {
    ROCK,
    PAPER,
    @Json(name = "scr") SCISSORS
  }
}
