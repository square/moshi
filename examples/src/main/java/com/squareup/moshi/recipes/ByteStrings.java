/*
 * Copyright (C) 2018 Square, Inc.
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
import okio.ByteString;

public final class ByteStrings {
  public void run() throws Exception {
    String json = "\"TW9zaGksIE9saXZlLCBXaGl0ZSBDaGluPw\"";

    Moshi moshi = new Moshi.Builder()
        .add(ByteString.class, new Base64ByteStringAdapter())
        .build();
    JsonAdapter<ByteString> jsonAdapter = moshi.adapter(ByteString.class);

    ByteString byteString = jsonAdapter.fromJson(json);
    System.out.println(byteString);
  }

  /**
   * Formats byte strings using <a href="http://www.ietf.org/rfc/rfc2045.txt">Base64</a>. No line
   * breaks or whitespace is included in the encoded form.
   */
  public final class Base64ByteStringAdapter extends JsonAdapter<ByteString> {
    @Override public ByteString fromJson(JsonReader reader) throws IOException {
      String base64 = reader.nextString();
      return ByteString.decodeBase64(base64);
    }

    @Override public void toJson(JsonWriter writer, ByteString value) throws IOException {
      String string = value.base64();
      writer.value(string);
    }
  }

  public static void main(String[] args) throws Exception {
    new ByteStrings().run();
  }
}
