/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.recipes;


import com.squareup.moshi.FromJson;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.JsonReader;

import javax.annotation.Nullable;
import java.io.IOException;

public final class CustomAdapterWithDelegate {
  public void run() throws Exception {
    // We want to match any Stage that starts with 'in-progress' as Stage.IN_PROGRESS
    // and leave the rest of the enum values as to match as normal.
    Moshi moshi = new Moshi.Builder().add(new StageAdapter()).build();
    JsonAdapter<Stage> jsonAdapter = moshi.adapter(Stage.class);

    System.out.println(jsonAdapter.fromJson("\"not-started\""));
    System.out.println(jsonAdapter.fromJson("\"in-progress\""));
    System.out.println(jsonAdapter.fromJson("\"in-progress-step1\""));
  }

  public static void main(String[] args) throws Exception {
    new CustomAdapterWithDelegate().run();
  }

  private enum Stage {
    @Json(name = "not-started") NOT_STARTED,
    @Json(name = "in-progress") IN_PROGRESS,
    @Json(name = "rejected") REJECTED,
    @Json(name = "completed") COMPLETED
  }

  private static final class StageAdapter {
    @FromJson
    @Nullable
    Stage fromJson(JsonReader jsonReader, JsonAdapter<Stage> delegate) throws IOException {
      String value = jsonReader.nextString();

      Stage stage;
      if (value.startsWith("in-progress")) {
        stage = Stage.IN_PROGRESS;
      } else {
        stage = delegate.fromJsonValue(value);
      }
      return stage;
    }
  }
}
