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
  static List<Object[]> factories() {
    JsonReaderFactory bufferedSource = new JsonReaderFactory() {
      @Override public JsonReader newReader(String json) {
        Buffer buffer = new Buffer().writeUtf8(json);
        return JsonReader.of(buffer);
      }

      @Override boolean supportsMultipleTopLevelValuesInOneDocument() {
        return true;
      }

      @Override public String toString() {
        return "BufferedSourceJsonReader";
      }
    };

    JsonReaderFactory jsonObject = new JsonReaderFactory() {
      @Override public JsonReader newReader(String json) throws IOException {
        Moshi moshi = new Moshi.Builder().build();
        Object object = moshi.adapter(Object.class).lenient().fromJson(json);
        return new ObjectJsonReader(object);
      }

      // TODO(jwilson): fix precision checks and delete his method.
      @Override boolean implementsStrictPrecision() {
        return false;
      }

      @Override public String toString() {
        return "ObjectJsonReader";
      }
    };

    return Arrays.asList(
        new Object[] { bufferedSource },
        new Object[] { jsonObject });
  }

  abstract JsonReader newReader(String json) throws IOException;

  boolean supportsMultipleTopLevelValuesInOneDocument() {
    return false;
  }

  boolean implementsStrictPrecision() {
    return true;
  }
}
