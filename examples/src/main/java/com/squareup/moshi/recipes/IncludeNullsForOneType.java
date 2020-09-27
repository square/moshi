/*
 * Copyright (C) 2020 Square, Inc.
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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;
import com.squareup.moshi.recipes.models.Tournament;
import java.io.IOException;
import java.util.Date;

public final class IncludeNullsForOneType {
  public void run() throws Exception {
    Moshi moshi =
        new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .add(new TournamentWithNullsAdapter())
            .build();

    JsonAdapter<Tournament> tournamentAdapter = moshi.adapter(Tournament.class);

    // Moshi normally skips nulls, but with our adapter registered they are emitted.
    Tournament withNulls = new Tournament("Waterloo Classic", null, null);
    System.out.println(tournamentAdapter.toJson(withNulls));
  }

  public static final class TournamentWithNullsAdapter {
    @ToJson
    void toJson(JsonWriter writer, Tournament tournament, JsonAdapter<Tournament> delegate)
        throws IOException {
      boolean wasSerializeNulls = writer.getSerializeNulls();
      writer.setSerializeNulls(true);
      try {
        // Once we've customized the JSON writer, we let the default JSON adapter do its job.
        delegate.toJson(writer, tournament);
      } finally {
        writer.setLenient(wasSerializeNulls);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    new IncludeNullsForOneType().run();
  }
}
