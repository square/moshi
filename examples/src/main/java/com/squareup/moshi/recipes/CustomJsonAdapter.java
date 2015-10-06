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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class CustomJsonAdapter {

    private static final DateFormat FORMAT_DATE_AND_TIME = new SimpleDateFormat("yyyyMMddHH:mm");
    private static final DateFormat FORMAT_DATE = new SimpleDateFormat("yyyyMMdd");
    private static final DateFormat FORMAT_TIME = new SimpleDateFormat("HH:mm");

    public void run() throws Exception {
        // for some reason our JSON has date and time as separate fields -
        // we will use a custom JsonAdapter<Event> to clean that up during parsing
        // also, we are not interested in "fooToken", so we'll ignore that
        String json = ""
                + "{\n"
                + "  \"title\": \"Blackjack tournament\",\n"
                + "  \"beginDateAndTime\": \"20151006\",\n"
                + "  \"beginTime\": \"15:59\",\n"
                + "  \"fooToken\": \"zih7wenvkqyqwer099qr\"\n"
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
                } else if (name.equals("beginDateAndTime")) {
                    beginDate = jsonReader.nextString();
                } else if (name.equals("beginTime")) {
                    beginTime = jsonReader.nextString();
                } else {
                    // skip (e.g.) "fooToken"
                    jsonReader.skipValue();
                }
            }
            jsonReader.endObject();

            Date beginDateAndTime;
            if ((beginDate != null) && (beginTime != null)) {
                try {
                    beginDateAndTime = FORMAT_DATE_AND_TIME.parse(beginDate + beginTime);
                } catch (ParseException e) {
                    beginDateAndTime = null;
                }
            } else {
                beginDateAndTime = null;
            }

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
                jsonWriter.name("beginDateAndTime");
                jsonWriter.value(FORMAT_DATE.format(event.beginDateAndTime));

                jsonWriter.name("beginTime");
                jsonWriter.value(FORMAT_TIME.format(event.beginDateAndTime));
            }

            jsonWriter.endObject();
        }
    }

    public static final class Event {
        public final String title;
        public final Date beginDateAndTime;

        public Event(String title, Date beginDateAndTime) {
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
