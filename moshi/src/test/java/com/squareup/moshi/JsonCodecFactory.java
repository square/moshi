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
import okio.BufferedSource;

abstract class JsonCodecFactory {
  private static final Moshi MOSHI = new Moshi.Builder().build();
  private static final JsonAdapter<Object> OBJECT_ADAPTER = MOSHI.adapter(Object.class);

  static List<Object[]> factories() {
    final JsonCodecFactory utf8 =
        new JsonCodecFactory() {
          Buffer buffer;

          @Override
          public JsonReader newReader(String json) {
            Buffer buffer = new Buffer().writeUtf8(json);
            return JsonReader.of(buffer);
          }

          @Override
          JsonWriter newWriter() {
            buffer = new Buffer();
            return new JsonUtf8Writer(buffer);
          }

          @Override
          String json() {
            String result = buffer.readUtf8();
            buffer = null;
            return result;
          }

          @Override
          boolean encodesToBytes() {
            return true;
          }

          @Override
          public String toString() {
            return "Utf8";
          }
        };

    final JsonCodecFactory value =
        new JsonCodecFactory() {
          JsonValueWriter writer;

          @Override
          public JsonReader newReader(String json) throws IOException {
            Moshi moshi = new Moshi.Builder().build();
            Object object = moshi.adapter(Object.class).lenient().fromJson(json);
            return new JsonValueReader(object);
          }

          // TODO(jwilson): fix precision checks and delete his method.
          @Override
          boolean implementsStrictPrecision() {
            return false;
          }

          @Override
          JsonWriter newWriter() {
            writer = new JsonValueWriter();
            return writer;
          }

          @Override
          String json() {
            // This writer writes a DOM. Use other Moshi features to serialize it as a string.
            try {
              Buffer buffer = new Buffer();
              JsonWriter bufferedSinkWriter = JsonWriter.of(buffer);
              bufferedSinkWriter.setSerializeNulls(true);
              bufferedSinkWriter.setLenient(true);
              OBJECT_ADAPTER.toJson(bufferedSinkWriter, writer.root());
              return buffer.readUtf8();
            } catch (IOException e) {
              throw new AssertionError();
            }
          }

          // TODO(jwilson): support BigDecimal and BigInteger and delete his method.
          @Override
          boolean supportsBigNumbers() {
            return false;
          }

          @Override
          public String toString() {
            return "Value";
          }
        };

    final JsonCodecFactory valuePeek =
        new JsonCodecFactory() {
          @Override
          public JsonReader newReader(String json) throws IOException {
            return value.newReader(json).peekJson();
          }

          // TODO(jwilson): fix precision checks and delete his method.
          @Override
          boolean implementsStrictPrecision() {
            return false;
          }

          @Override
          JsonWriter newWriter() throws IOException {
            return value.newWriter();
          }

          @Override
          String json() throws IOException {
            return value.json();
          }

          // TODO(jwilson): support BigDecimal and BigInteger and delete his method.
          @Override
          boolean supportsBigNumbers() {
            return false;
          }

          @Override
          public String toString() {
            return "ValuePeek";
          }
        };

    /** Wrap the enclosing JsonReader or JsonWriter in another stream. */
    final JsonCodecFactory valueSource =
        new JsonCodecFactory() {
          private JsonWriter writer;

          @Override
          public JsonReader newReader(String json) throws IOException {
            JsonReader wrapped = utf8.newReader(json);
            wrapped.setLenient(true);
            BufferedSource valueSource = wrapped.nextSource();
            return JsonReader.of(valueSource);
          }

          @Override
          JsonWriter newWriter() throws IOException {
            JsonWriter wrapped = utf8.newWriter();
            wrapped.setLenient(true);
            writer = JsonWriter.of(wrapped.valueSink());
            return writer;
          }

          @Override
          String json() throws IOException {
            writer.close();
            return utf8.json();
          }

          @Override
          public String toString() {
            return "ValueSource";
          }
        };

    return Arrays.asList(
        new Object[] {utf8},
        new Object[] {value},
        new Object[] {valuePeek},
        new Object[] {valueSource});
  }

  abstract JsonReader newReader(String json) throws IOException;

  abstract JsonWriter newWriter() throws IOException;

  boolean implementsStrictPrecision() {
    return true;
  }

  abstract String json() throws IOException;

  boolean encodesToBytes() {
    return false;
  }

  boolean supportsBigNumbers() {
    return true;
  }
}
