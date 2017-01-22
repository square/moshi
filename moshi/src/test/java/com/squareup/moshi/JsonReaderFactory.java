/*
 * Copyright (C) 2017 Square, Inc.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import okio.Buffer;

abstract class JsonReaderFactory {
  public static final JsonReaderFactory BUFFERED_SOURCE = new JsonReaderFactory() {
    @Override public JsonReader newReader(String json) {
      Buffer buffer = new Buffer().writeUtf8(json);
      return JsonReader.of(buffer);
    }

    @Override public String toString() {
      return "BufferedSourceJsonReader";
    }
  };

  public static final JsonReaderFactory JSON_OBJECT = new JsonReaderFactory() {
    @Override public JsonReader newReader(String json) throws IOException {
      Moshi moshi = new Moshi.Builder().build();
      Object object = moshi.adapter(Object.class).lenient().fromJson(json);
      return new ObjectJsonReader(object);
    }

    @Override public String toString() {
      return "ObjectJsonReader";
    }
  };

  static List<Object[]> factories() {
    return Arrays.asList(
        new Object[] { BUFFERED_SOURCE },
        new Object[] { JSON_OBJECT });
  }

  abstract JsonReader newReader(String json) throws IOException;
}
