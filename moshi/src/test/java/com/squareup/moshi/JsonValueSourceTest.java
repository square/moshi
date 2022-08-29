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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.EOFException;
import java.io.IOException;
import okio.Buffer;
import org.junit.Test;

public final class JsonValueSourceTest {
  @Test
  public void simpleValues() throws IOException {
    assertEquals(jsonPrefix("{\"hello\": \"world\"}, 1, 2, 3"), "{\"hello\": \"world\"}");
    assertEquals(jsonPrefix("['hello', 'world'], 1, 2, 3"), "['hello', 'world']");
    assertEquals(jsonPrefix("\"hello\", 1, 2, 3"), "\"hello\"");
  }

  @Test
  public void braceMatching() throws IOException {
    assertEquals(jsonPrefix("[{},{},[],[{}]],[]"), "[{},{},[],[{}]]");
    assertEquals(
        jsonPrefix("[\"a\",{\"b\":{\"c\":{\"d\":[\"e\"]}}}],[]"),
        "[\"a\",{\"b\":{\"c\":{\"d\":[\"e\"]}}}]");
  }

  @Test
  public void stringEscapes() throws IOException {
    assertEquals(jsonPrefix("[\"12\\u00334\"],[]"), "[\"12\\u00334\"]");
    assertEquals(jsonPrefix("[\"12\\n34\"],[]"), "[\"12\\n34\"]");
    assertEquals(jsonPrefix("[\"12\\\"34\"],[]"), "[\"12\\\"34\"]");
    assertEquals(jsonPrefix("[\"12\\'34\"],[]"), "[\"12\\'34\"]");
    assertEquals(jsonPrefix("[\"12\\\\34\"],[]"), "[\"12\\\\34\"]");
    assertEquals(jsonPrefix("[\"12\\\\\"],[]"), "[\"12\\\\\"]");
  }

  @Test
  public void bracesInStrings() throws IOException {
    assertEquals(jsonPrefix("[\"]\"],[]"), "[\"]\"]");
    assertEquals(jsonPrefix("[\"\\]\"],[]"), "[\"\\]\"]");
    assertEquals(jsonPrefix("[\"\\[\"],[]"), "[\"\\[\"]");
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
    assertEquals(jsonPrefix("['hello', 'world'], 1, 2, 3"), "['hello', 'world']");
    assertEquals(jsonPrefix("'abc\\'', 123"), "'abc\\''");
  }

  @Test
  public void lenientCStyleComments() throws IOException {
    assertEquals(jsonPrefix("[\"a\"/* \"b\" */,\"c\"],[]"), "[\"a\"/* \"b\" */,\"c\"]");
    assertEquals(jsonPrefix("[\"a\"/*]*/],[]"), "[\"a\"/*]*/]");
    assertEquals(jsonPrefix("[\"a\"/**/],[]"), "[\"a\"/**/]");
    assertEquals(jsonPrefix("[\"a\"/*/ /*/],[]"), "[\"a\"/*/ /*/]");
    assertEquals(jsonPrefix("[\"a\"/*/ **/],[]"), "[\"a\"/*/ **/]");
  }

  @Test
  public void lenientEndOfLineComments() throws IOException {
    assertEquals(jsonPrefix("[\"a\"// \"b\" \n,\"c\"],[]"), "[\"a\"// \"b\" \n,\"c\"]");
    assertEquals(jsonPrefix("[\"a\"// \"b\" \r\n,\"c\"],[]"), "[\"a\"// \"b\" \r\n,\"c\"]");
    assertEquals(jsonPrefix("[\"a\"// \"b\" \r,\"c\"],[]"), "[\"a\"// \"b\" \r,\"c\"]");
    assertEquals(jsonPrefix("[\"a\"//]\r\n\"c\"],[]"), "[\"a\"//]\r\n\"c\"]");
  }

  @Test
  public void lenientSlashInToken() throws IOException {
    assertEquals(jsonPrefix("{a/b:\"c\"},[]"), "{a/b:\"c\"}");
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

    assertEquals(allData.readUtf8(), ",[\"d\", \"e\"]");
  }

  private String jsonPrefix(String string) throws IOException {
    Buffer allData = new Buffer();
    allData.writeUtf8(string);

    Buffer jsonPrefixBuffer = new Buffer();
    jsonPrefixBuffer.writeAll(new JsonValueSource(allData));

    String result = jsonPrefixBuffer.readUtf8();
    String remainder = allData.readUtf8();
    assertEquals(result + remainder, string);

    return result;
  }
}
