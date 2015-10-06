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
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;

import java.io.IOException;

public final class CustomJsonAdapter {

    public void run() throws Exception {
        // for some reason our JSON has date and time as separate fields -
        // we will use a custom JsonAdapter<Event> to clean that up during parsing
        String json = ""
                + "{\n"
                + "  \"title\": \"Blackjack tournament\",\n"
                + "  \"beginDate\": \"20151006\",\n"
                + "  \"beginTime\": \"15:59\"\n"
                + "}\n";

        Moshi moshi = new Moshi.Builder()
                .add(Event.class, new EventAdapter())
                .build();
        JsonAdapter<Event> jsonAdapter = moshi.adapter(Event.class);

        Event event = jsonAdapter.fromJson(json);
        System.out.println(event);
        System.out.println(jsonAdapter.toJson(event));
    }

    public static void main(String[] args) throws Exception {
        new CustomJsonAdapter().run();
    }

    public static final class EventAdapter extends JsonAdapter<Event> {
        @Override
        public Event fromJson(JsonReader jsonReader) throws IOException {
            String title = null;
            String beginDate = null;
            String beginTime = null;

            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                if (name.equals("title")) {
                    title = jsonReader.nextString();
                } else if (name.equals("beginDate")) {
                    beginDate = jsonReader.nextString();
                } else if (name.equals("beginTime")) {
                    beginTime = jsonReader.nextString();
                } else {
                    jsonReader.skipValue();
                }
            }
            jsonReader.endObject();

            final String beginDateAndTime = (beginDate != null) && (beginTime != null) ?
                    beginDate + " " + beginTime : null;

            return new Event(title, beginDateAndTime);
        }

        @Override
        public void toJson(JsonWriter jsonWriter, Event event) throws IOException {
            jsonWriter.beginObject();

            if (event.title != null) {
                jsonWriter.name("title");
                jsonWriter.value(event.title);
            }

            if (event.beginDateAndTime != null) {
                jsonWriter.name("beginDate");
                jsonWriter.value(event.beginDateAndTime.substring(0, 8));

                jsonWriter.name("beginTime");
                jsonWriter.value(event.beginDateAndTime.substring(9, 14));
            }

            jsonWriter.endObject();
        }
    }

    public static final class Event {
        public final String title;
        public final String beginDateAndTime;

        public Event(String title, String beginDateAndTime) {
            this.title = title;
            this.beginDateAndTime = beginDateAndTime;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "title='" + title + '\'' +
                    ", beginDateAndTime=" + beginDateAndTime +
                    '}';
        }
    }
}
