/*
 * Copyright (C) 2015 Square Inc.
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
import okio.Buffer;

final class TestUtil {
  static final int MAX_DEPTH = 255;

  static JsonReader newReader(String json) {
    Buffer buffer = new Buffer().writeUtf8(json);
    return JsonReader.of(buffer);
  }

  static String repeat(char c, int count) {
    char[] array = new char[count];
    Arrays.fill(array, c);
    return new String(array);
  }

  static String repeat(String s, int count) {
    StringBuilder result = new StringBuilder(s.length() * count);
    for (int i = 0; i < count; i++) {
      result.append(s);
    }
    return result.toString();
  }

  private TestUtil() {
    throw new AssertionError("No instances.");
  }
}
