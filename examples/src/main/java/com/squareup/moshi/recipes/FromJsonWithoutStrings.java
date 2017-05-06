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

import com.squareup.moshi.FromJson;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;

public final class FromJsonWithoutStrings {
  public void run() throws Exception {
    // For some reason our JSON has date and time as separate fields. We will clean that up during
    // parsing: Moshi will first parse the JSON directly to an EventJson and from that the
    // EventJsonAdapter will create the actual Event.
    String json = ""
        + "{\n"
        + "  \"title\": \"Blackjack tournament\",\n"
        + "  \"begin_date\": \"20151010\",\n"
        + "  \"begin_time\": \"17:04\"\n"
        + "}\n";

    Moshi moshi = new Moshi.Builder().add(new EventJsonAdapter()).build();
    JsonAdapter<Event> jsonAdapter = moshi.adapter(Event.class);

    Event event = jsonAdapter.fromJson(json);
    System.out.println(event);
    System.out.println(jsonAdapter.toJson(event));
  }

  public static void main(String[] args) throws Exception {
    new FromJsonWithoutStrings().run();
  }

  @SuppressWarnings("checkstyle:membername")
  private static final class EventJson {
    String title;
    String begin_date;
    String begin_time;
  }

  public static final class Event {
    String title;
    String beginDateAndTime;

    @Override public String toString() {
      return "Event{"
          + "title='" + title + '\''
          + ", beginDateAndTime='" + beginDateAndTime + '\''
          + '}';
    }
  }

  private static final class EventJsonAdapter {
    @FromJson Event eventFromJson(EventJson eventJson) {
      Event event = new Event();
      event.title = eventJson.title;
      event.beginDateAndTime = eventJson.begin_date + " " + eventJson.begin_time;
      return event;
    }

    @ToJson EventJson eventToJson(Event event) {
      EventJson json = new EventJson();
      json.title = event.title;
      json.begin_date = event.beginDateAndTime.substring(0, 8);
      json.begin_time = event.beginDateAndTime.substring(9, 14);
      return json;
    }
  }
}
