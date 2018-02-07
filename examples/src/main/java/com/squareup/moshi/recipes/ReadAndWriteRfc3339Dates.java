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
package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;
import com.squareup.moshi.recipes.models.Tournament;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class ReadAndWriteRfc3339Dates {
  public void run() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(Date.class, new Rfc3339DateJsonAdapter())
        .build();
    JsonAdapter<Tournament> jsonAdapter = moshi.adapter(Tournament.class);

    // The RFC3339 JSON adapter can read dates with a timezone offset like '-05:00'.
    String lastTournament = ""
        + "{"
        + "  \"location\":\"Chainsaw\","
        + "  \"name\":\"21 for 21\","
        + "  \"start\":\"2015-09-01T20:00:00-05:00\""
        + "}";
    System.out.println("Last tournament: " + jsonAdapter.fromJson(lastTournament));

    // The RFC3339 JSON adapter always writes dates with UTC, using a 'Z' suffix.
    Tournament nextTournament = new Tournament(
        "Waterloo Classic", "Bauer Kitchen", newDate(2015, 10, 1, 20, -5));
    System.out.println("Next tournament JSON: " + jsonAdapter.toJson(nextTournament));
  }

  public static void main(String[] args) throws Exception {
    new ReadAndWriteRfc3339Dates().run();
  }

  private Date newDate(int year, int month, int day, int hour, int offset) {
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
    calendar.set(year, month - 1, day, hour, 0, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return new Date(calendar.getTimeInMillis() - TimeUnit.HOURS.toMillis(offset));
  }
}
