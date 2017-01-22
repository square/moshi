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

import java.util.Arrays;
import java.util.List;
import okio.Buffer;

abstract class JsonWriterFactory {
  static List<Object[]> factories() {
    final JsonWriterFactory bufferedSink = new JsonWriterFactory() {
      Buffer buffer;

      @Override JsonWriter newWriter() {
        buffer = new Buffer();
        return new BufferedSinkJsonWriter(buffer);
      }

      @Override String json() {
        String result = buffer.readUtf8();
        buffer = null;
        return result;
      }

      @Override public String toString() {
        return "BufferedSinkJsonWriter";
      }
    };

    return Arrays.<Object[]>asList(
        new Object[] { bufferedSink });
  }

  abstract JsonWriter newWriter();
  abstract String json();
}
