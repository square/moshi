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
package com.squareup.moshi.adapters;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.Date;

/**
 * Formats dates using <a href="https://www.ietf.org/rfc/rfc3339.txt">RFC 3339</a>, which is
 * formatted like {@code 2015-09-26T18:23:50.250Z}. This adapter is null-safe. To use, add this as
 * an adapter for {@code Date.class} on your {@link com.squareup.moshi.Moshi.Builder Moshi.Builder}:
 *
 * <pre> {@code
 *
 *   Moshi moshi = new Moshi.Builder()
 *       .add(Date.class, new Rfc3339DateJsonAdapter())
 *       .build();
 * }</pre>
 */
public final class Rfc3339DateJsonAdapter extends JsonAdapter<Date> {
  @Override public synchronized Date fromJson(JsonReader reader) throws IOException {
    if (reader.peek() == JsonReader.Token.NULL) {
      return reader.nextNull();
    }
    String string = reader.nextString();
    return Iso8601Utils.parse(string);
  }

  @Override public synchronized void toJson(JsonWriter writer, Date value) throws IOException {
    if (value == null) {
      writer.nullValue();
    } else {
      String string = Iso8601Utils.format(value);
      writer.value(string);
    }
  }
}
