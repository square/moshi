/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import java.io.EOFException;
import java.io.IOException;
import okio.Buffer;
import org.junit.Test;

public final class JsonValueSourceTest {
  @Test
  public void simpleValues() throws IOException {
    assertThat(jsonPrefix("{\"hello\": \"world\"}, 1, 2, 3")).isEqualTo("{\"hello\": \"world\"}");
    assertThat(jsonPrefix("['hello', 'world'], 1, 2, 3")).isEqualTo("['hello', 'world']");
    assertThat(jsonPrefix("\"hello\", 1, 2, 3")).isEqualTo("\"hello\"");
  }

  @Test
  public void braceMatching() throws IOException {
    assertThat(jsonPrefix("[{},{},[],[{}]],[]")).isEqualTo("[{},{},[],[{}]]");
    assertThat(jsonPrefix("[\"a\",{\"b\":{\"c\":{\"d\":[\"e\"]}}}],[]"))
        .isEqualTo("[\"a\",{\"b\":{\"c\":{\"d\":[\"e\"]}}}]");
  }

  @Test
  public void stringEscapes() throws IOException {
    assertThat(jsonPrefix("[\"12\\u00334\"],[]")).isEqualTo("[\"12\\u00334\"]");
    assertThat(jsonPrefix("[\"12\\n34\"],[]")).isEqualTo("[\"12\\n34\"]");
    assertThat(jsonPrefix("[\"12\\\"34\"],[]")).isEqualTo("[\"12\\\"34\"]");
    assertThat(jsonPrefix("[\"12\\'34\"],[]")).isEqualTo("[\"12\\'34\"]");
    assertThat(jsonPrefix("[\"12\\\\34\"],[]")).isEqualTo("[\"12\\\\34\"]");
    assertThat(jsonPrefix("[\"12\\\\\"],[]")).isEqualTo("[\"12\\\\\"]");
  }

  @Test
  public void bracesInStrings() throws IOException {
    assertThat(jsonPrefix("[\"]\"],[]")).isEqualTo("[\"]\"]");
    assertThat(jsonPrefix("[\"\\]\"],[]")).isEqualTo("[\"\\]\"]");
    assertThat(jsonPrefix("[\"\\[\"],[]")).isEqualTo("[\"\\[\"]");
  }

  @Test
  public void unterminatedString() throws IOException {
    try {
      jsonPrefix("{\"a\":\"b...");
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void unterminatedObject() throws IOException {
    try {
      jsonPrefix("{\"a\":\"b\",\"c\":");
      fail();
    } catch (EOFException expected) {
    }
    try {
      jsonPrefix("{");
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void unterminatedArray() throws IOException {
    try {
      jsonPrefix("[\"a\",\"b\",\"c\",");
      fail();
    } catch (EOFException expected) {
    }
    try {
      jsonPrefix("[");
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void lenientUnterminatedSingleQuotedString() throws IOException {
    try {
      jsonPrefix("{\"a\":'b...");
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void emptyStream() throws IOException {
    try {
      jsonPrefix("");
      fail();
    } catch (EOFException expected) {
    }
    try {
      jsonPrefix("        ");
      fail();
    } catch (EOFException expected) {
    }
    try {
      jsonPrefix("/* comment */");
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void lenientSingleQuotedStrings() throws IOException {
    assertThat(jsonPrefix("['hello', 'world'], 1, 2, 3")).isEqualTo("['hello', 'world']");
    assertThat(jsonPrefix("'abc\\'', 123")).isEqualTo("'abc\\''");
  }

  @Test
  public void lenientCStyleComments() throws IOException {
    assertThat(jsonPrefix("[\"a\"/* \"b\" */,\"c\"],[]")).isEqualTo("[\"a\"/* \"b\" */,\"c\"]");
    assertThat(jsonPrefix("[\"a\"/*]*/],[]")).isEqualTo("[\"a\"/*]*/]");
    assertThat(jsonPrefix("[\"a\"/**/],[]")).isEqualTo("[\"a\"/**/]");
    assertThat(jsonPrefix("[\"a\"/*/ /*/],[]")).isEqualTo("[\"a\"/*/ /*/]");
    assertThat(jsonPrefix("[\"a\"/*/ **/],[]")).isEqualTo("[\"a\"/*/ **/]");
  }

  @Test
  public void lenientEndOfLineComments() throws IOException {
    assertThat(jsonPrefix("[\"a\"// \"b\" \n,\"c\"],[]")).isEqualTo("[\"a\"// \"b\" \n,\"c\"]");
    assertThat(jsonPrefix("[\"a\"// \"b\" \r\n,\"c\"],[]")).isEqualTo("[\"a\"// \"b\" \r\n,\"c\"]");
    assertThat(jsonPrefix("[\"a\"// \"b\" \r,\"c\"],[]")).isEqualTo("[\"a\"// \"b\" \r,\"c\"]");
    assertThat(jsonPrefix("[\"a\"//]\r\n\"c\"],[]")).isEqualTo("[\"a\"//]\r\n\"c\"]");
  }

  @Test
  public void lenientSlashInToken() throws IOException {
    assertThat(jsonPrefix("{a/b:\"c\"},[]")).isEqualTo("{a/b:\"c\"}");
  }

  @Test
  public void lenientUnterminatedEndOfLineComment() throws IOException {
    try {
      jsonPrefix("{\"a\",//}");
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void lenientUnterminatedCStyleComment() throws IOException {
    try {
      jsonPrefix("{\"a\",/* *");
      fail();
    } catch (EOFException expected) {
    }
    try {
      jsonPrefix("{\"a\",/* **");
      fail();
    } catch (EOFException expected) {
    }
    try {
      jsonPrefix("{\"a\",/* /**");
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void discard() throws IOException {
    Buffer allData = new Buffer();
    allData.writeUtf8("{\"a\",\"b\",\"c\"},[\"d\", \"e\"]");

    JsonValueSource jsonValueSource = new JsonValueSource(allData);
    jsonValueSource.close();
    jsonValueSource.discard();

    assertThat(allData.readUtf8()).isEqualTo(",[\"d\", \"e\"]");
  }

  private String jsonPrefix(String string) throws IOException {
    Buffer allData = new Buffer();
    allData.writeUtf8(string);

    Buffer jsonPrefixBuffer = new Buffer();
    jsonPrefixBuffer.writeAll(new JsonValueSource(allData));

    String result = jsonPrefixBuffer.readUtf8();
    String remainder = allData.readUtf8();
    assertThat(result + remainder).isEqualTo(string);

    return result;
  }
}
