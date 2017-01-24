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

abstract class JsonWriterFactory {
  private static final Moshi MOSHI = new Moshi.Builder().build();
  private static final JsonAdapter<Object> OBJECT_ADAPTER = MOSHI.adapter(Object.class);

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

      @Override boolean supportsMultipleTopLevelValuesInOneDocument() {
        return true;
      }

      @Override public String toString() {
        return "BufferedSinkJsonWriter";
      }
    };

    final JsonWriterFactory object = new JsonWriterFactory() {
      ObjectJsonWriter writer;

      @Override JsonWriter newWriter() {
        writer = new ObjectJsonWriter();
        return writer;
      }

      @Override String json() {
        // This writer writes a DOM. Use other Moshi features to serialize it as a string.
        try {
          Buffer buffer = new Buffer();
          JsonWriter bufferedSinkWriter = JsonWriter.of(buffer);
          bufferedSinkWriter.setSerializeNulls(true);
          OBJECT_ADAPTER.toJson(bufferedSinkWriter, writer.root());
          return buffer.readUtf8();
        } catch (IOException e) {
          throw new AssertionError();
        }
      }

      // TODO(jwilson): support BigDecimal and BigInteger and delete his method.
      @Override boolean supportsBigNumbers() {
        return false;
      }

      @Override public String toString() {
        return "ObjectJsonWriter";
      }
    };

    return Arrays.asList(
        new Object[] { bufferedSink },
        new Object[] { object });
  }

  abstract JsonWriter newWriter();
  abstract String json();

  boolean supportsMultipleTopLevelValuesInOneDocument() {
    return false;
  }

  boolean supportsBigNumbers() {
    return true;
  }
}
