/*
 * Copyright (C) 2010 Google Inc.
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

import static com.squareup.moshi.JsonReader.Token.BEGIN_ARRAY;
import static com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT;
import static com.squareup.moshi.JsonReader.Token.BOOLEAN;
import static com.squareup.moshi.JsonReader.Token.END_ARRAY;
import static com.squareup.moshi.JsonReader.Token.END_OBJECT;
import static com.squareup.moshi.JsonReader.Token.NAME;
import static com.squareup.moshi.JsonReader.Token.NULL;
import static com.squareup.moshi.JsonReader.Token.NUMBER;
import static com.squareup.moshi.JsonReader.Token.STRING;
import static com.squareup.moshi.TestUtil.MAX_DEPTH;
import static com.squareup.moshi.TestUtil.newReader;
import static com.squareup.moshi.TestUtil.repeat;
import static org.junit.Assert.*;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
import org.junit.Ignore;
import org.junit.Test;

public final class JsonUtf8ReaderTest {
  @Test
  public void readingDoesNotBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{}{}");

    JsonReader reader1 = JsonReader.of(buffer);
    reader1.beginObject();
    reader1.endObject();
    assertEquals(buffer.size(), 2);

    JsonReader reader2 = JsonReader.of(buffer);
    reader2.beginObject();
    reader2.endObject();
    assertEquals(buffer.size(), 0);
  }

  @Test
  public void readObjectBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = JsonReader.of(buffer);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextString(), "android");
    assertEquals(reader.nextName(), "b");
    assertEquals(reader.nextString(), "banana");
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void readObjectSource() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = JsonReader.of(Okio.buffer(new ForwardingSource(buffer) {}));
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextString(), "android");
    assertEquals(reader.nextName(), "b");
    assertEquals(reader.nextString(), "banana");
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void nullSource() {
    try {
      JsonReader.of(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void unescapingInvalidCharacters() throws IOException {
    String json = "[\"\\u000g\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void unescapingTruncatedCharacters() throws IOException {
    String json = "[\"\\u000";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void unescapingTruncatedSequence() throws IOException {
    String json = "[\"\\";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictNonFiniteDoublesWithSkipValue() throws IOException {
    String json = "[NaN]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  @Ignore
  public void numberWithOctalPrefix() throws IOException {
    String json = "[01]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
    try {
      reader.nextInt();
      fail();
    } catch (JsonEncodingException expected) {
    }
    try {
      reader.nextLong();
      fail();
    } catch (JsonEncodingException expected) {
    }
    try {
      reader.nextDouble();
      fail();
    } catch (JsonEncodingException expected) {
    }
    assertEquals(reader.nextString(), "01");
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void peekingUnquotedStringsPrefixedWithBooleans() throws IOException {
    JsonReader reader = newReader("[truey]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), STRING);
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextString(), "truey");
    reader.endArray();
  }

  @Test
  public void malformedNumbers() throws IOException {
    assertNotANumber("-");
    assertNotANumber(".");

    // exponent lacks digit
    assertNotANumber("e");
    assertNotANumber("0e");
    assertNotANumber(".e");
    assertNotANumber("0.e");
    assertNotANumber("-.0e");

    // no integer
    assertNotANumber("e1");
    assertNotANumber(".e1");
    assertNotANumber("-e1");

    // trailing characters
    assertNotANumber("1x");
    assertNotANumber("1.1x");
    assertNotANumber("1e1x");
    assertNotANumber("1ex");
    assertNotANumber("1.1ex");
    assertNotANumber("1.1e1x");

    // fraction has no digit
    assertNotANumber("0.");
    assertNotANumber("-0.");
    assertNotANumber("0.e1");
    assertNotANumber("-0.e1");

    // no leading digit
    assertNotANumber(".0");
    assertNotANumber("-.0");
    assertNotANumber(".0e1");
    assertNotANumber("-.0e1");
  }

  private void assertNotANumber(String s) throws IOException {
    JsonReader reader = newReader("[" + s + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextString(), s);
    reader.endArray();
  }

  @Test
  public void peekingUnquotedStringsPrefixedWithIntegers() throws IOException {
    JsonReader reader = newReader("[12.34e5x]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), STRING);
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(reader.nextString(), "12.34e5x");
  }

  @Test
  public void peekLongMinValue() throws IOException {
    JsonReader reader = newReader("[-9223372036854775808]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    assertEquals(reader.nextLong(), -9223372036854775808L);
  }

  @Test
  public void peekLongMaxValue() throws IOException {
    JsonReader reader = newReader("[9223372036854775807]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    assertEquals(reader.nextLong(), 9223372036854775807L);
  }

  @Test
  public void longLargerThanMaxLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test
  public void longLargerThanMinLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[-22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test
  public void peekLargerThanLongMaxValue() throws IOException {
    JsonReader reader = newReader("[9223372036854775808]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test
  public void precisionNotDiscarded() throws IOException {
    JsonReader reader = newReader("[9223372036854775806.5]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test
  public void peekLargerThanLongMinValue() throws IOException {
    JsonReader reader = newReader("[-9223372036854775809]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(-9223372036854775809d, reader.nextDouble(), 0);
  }

  @Test
  public void highPrecisionLong() throws IOException {
    String json = "[9223372036854775806.000]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals(reader.nextLong(), 9223372036854775806L);
    reader.endArray();
  }

  @Test
  public void peekMuchLargerThanLongMinValue() throws IOException {
    JsonReader reader = newReader("[-92233720368547758080]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertEquals(-92233720368547758080d, reader.nextDouble(), 0);
  }

  @Test
  public void negativeZeroIsANumber() throws Exception {
    JsonReader reader = newReader("-0");
    assertEquals(NUMBER, reader.peek());
    assertEquals("-0", reader.nextString());
  }

  @Test
  public void numberToStringCoersion() throws Exception {
    JsonReader reader = newReader("[0, 9223372036854775807, 2.5, 3.010, \"a\", \"5\"]");
    reader.beginArray();
    assertEquals(reader.nextString(), "0");
    assertEquals(reader.nextString(), "9223372036854775807");
    assertEquals(reader.nextString(), "2.5");
    assertEquals(reader.nextString(), "3.010");
    assertEquals(reader.nextString(), "a");
    assertEquals(reader.nextString(), "5");
    reader.endArray();
  }

  @Test
  public void quotedNumberWithEscape() throws IOException {
    JsonReader reader = newReader("[\"12\u00334\"]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), STRING);
    assertEquals(reader.nextInt(), 1234);
  }

  @Test
  public void mixedCaseLiterals() throws IOException {
    JsonReader reader = newReader("[True,TruE,False,FALSE,NULL,nulL]");
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    assertTrue(reader.nextBoolean());
    assertFalse(reader.nextBoolean());
    assertFalse(reader.nextBoolean());
    reader.nextNull();
    reader.nextNull();
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void missingValue() throws IOException {
    JsonReader reader = newReader("{\"a\":}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void prematureEndOfInput() throws IOException {
    JsonReader reader = newReader("{\"a\":true,");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertTrue(reader.nextBoolean());
    try {
      reader.nextName();
      fail();
    } catch (EOFException expected) {
    }
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void prematurelyClosed() throws IOException {
    try {
      JsonReader reader = newReader("{\"a\":[]}");
      reader.beginObject();
      reader.close();
      reader.nextName();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      JsonReader reader = newReader("{\"a\":[]}");
      reader.close();
      reader.beginObject();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      JsonReader reader = newReader("{\"a\":true}");
      reader.beginObject();
      reader.nextName();
      reader.peek();
      reader.close();
      reader.nextBoolean();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void strictNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertTrue(reader.nextBoolean());

    reader = newReader("{\"a\"=>true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertTrue(reader.nextBoolean());
  }

  @Test
  public void strictNameValueSeparatorWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void commentsInStringValue() throws Exception {
    JsonReader reader = newReader("[\"// comment\"]");
    reader.beginArray();
    assertEquals(reader.nextString(), "// comment");
    reader.endArray();

    reader = newReader("{\"a\":\"#someComment\"}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextString(), "#someComment");
    reader.endObject();

    reader = newReader("{\"#//a\":\"#some //Comment\"}");
    reader.beginObject();
    assertEquals(reader.nextName(), "#//a");
    assertEquals(reader.nextString(), "#some //Comment");
    reader.endObject();
  }

  @Test
  public void strictComments() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[# comment \n true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[/* comment */ true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientComments() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(reader.nextBoolean());

    reader = newReader("[# comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(reader.nextBoolean());

    reader = newReader("[/* comment */ true]");
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(reader.nextBoolean());

    reader = newReader("a//");
    reader.setLenient(true);
    assertEquals(reader.nextString(), "a");
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void strictCommentsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[# comment \n true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[/* comment */ true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
  }

  @Test
  public void jsonIsSingleUnquotedString() throws IOException {
    JsonReader reader = newReader("abc");
    reader.setLenient(true);
    assertEquals(reader.nextString(), "abc");
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void strictUnquotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
  }

  @Test
  public void strictSingleQuotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictUnquotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.nextString(), "a");
  }

  @Test
  public void lenientUnquotedStringsDelimitedByComment() throws IOException {
    JsonReader reader = newReader("[a#comment\n]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.nextString(), "a");
    reader.endArray();
  }

  @Test
  public void strictSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.nextString(), "a");
  }

  @Test
  public void strictSingleQuotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    assertTrue(reader.nextBoolean());
  }

  @Test
  public void strictSemicolonDelimitedArrayWithSkipValue() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.nextBoolean();
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertTrue(reader.nextBoolean());
    assertEquals(reader.nextName(), "b");
  }

  @Test
  public void strictSemicolonDelimitedNameValuePairWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,true]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[true,]");
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,]");
    reader.beginArray();
    try {
      reader.nextNull();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    reader.nextNull();
    assertTrue(reader.nextBoolean());
    reader.endArray();

    reader = newReader("[,true]");
    reader.setLenient(true);
    reader.beginArray();
    reader.nextNull();
    assertTrue(reader.nextBoolean());
    reader.endArray();

    reader = newReader("[true,]");
    reader.setLenient(true);
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    reader.nextNull();
    reader.endArray();

    reader = newReader("[,]");
    reader.setLenient(true);
    reader.beginArray();
    reader.nextNull();
    reader.nextNull();
    reader.endArray();
  }

  @Test
  public void strictUnnecessaryArraySeparatorsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,true]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[true,]");
    reader.beginArray();
    assertTrue(reader.nextBoolean());
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("[,]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void strictMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] true {}");
    reader.setLenient(true);
    reader.beginArray();
    reader.endArray();
    assertTrue(reader.nextBoolean());
    reader.beginObject();
    reader.endObject();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void strictMultipleTopLevelValuesWithSkipValue() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  @Ignore
  public void bomIgnoredAsFirstCharacterOfDocument() throws IOException {
    JsonReader reader = newReader("\ufeff[]");
    reader.beginArray();
    reader.endArray();
  }

  @Test
  public void bomForbiddenAsOtherCharacterInDocument() throws IOException {
    JsonReader reader = newReader("[\ufeff]");
    reader.beginArray();
    try {
      reader.endArray();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void failWithPosition() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\n\n\n\n\n\"a\",}]");
  }

  @Test
  public void failWithPositionGreaterThanBufferSize() throws IOException {
    String spaces = repeat(' ', 8192);
    testFailWithPosition("Expected value at path $[1]", "[\n\n" + spaces + "\n\n\n\"a\",}]");
  }

  @Test
  public void failWithPositionOverSlashSlashEndOfLineComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "\n// foo\n\n//bar\r\n[\"a\",}");
  }

  @Test
  public void failWithPositionOverHashEndOfLineComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "\n# foo\n\n#bar\r\n[\"a\",}");
  }

  @Test
  public void failWithPositionOverCStyleComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "\n\n/* foo\n*\n*\r\nbar */[\"a\",}");
  }

  @Test
  public void failWithPositionOverQuotedString() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\"foo\nbar\r\nbaz\n\",\n  }");
  }

  @Test
  public void failWithPositionOverUnquotedString() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\n\nabcd\n\n,}");
  }

  @Test
  public void failWithEscapedNewlineCharacter() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\n\n\"\\\n\n\",}");
  }

  @Test
  @Ignore
  public void failWithPositionIsOffsetByBom() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "\ufeff[\"a\",}]");
  }

  private void testFailWithPosition(String message, String json) throws IOException {
    // Validate that it works reading the string normally.
    JsonReader reader1 = newReader(json);
    reader1.setLenient(true);
    reader1.beginArray();
    reader1.nextString();
    try {
      reader1.peek();
      fail();
    } catch (JsonEncodingException expected) {
      assertTrue(expected.getMessage().contains(message));
    }

    // Also validate that it works when skipping.
    JsonReader reader2 = newReader(json);
    reader2.setLenient(true);
    reader2.beginArray();
    reader2.skipValue();
    try {
      reader2.peek();
      fail();
    } catch (JsonEncodingException expected) {
      assertTrue(expected.getMessage().contains(message));
    }
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void failWithPositionDeepPath() throws IOException {
    JsonReader reader = newReader("[1,{\"a\":[2,3,}");
    reader.beginArray();
    reader.nextInt();
    reader.beginObject();
    reader.nextName();
    reader.beginArray();
    reader.nextInt();
    reader.nextInt();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
      assertTrue(expected.getMessage().contains("Expected value at path $[1].a[2]"));
    }
  }

  @Test
  public void failureMessagePathFromSkipName() throws IOException {
    JsonReader reader = newReader("{\"a\":[42,}");
    reader.beginObject();
    reader.skipName();
    reader.beginArray();
    reader.nextInt();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
      assertTrue(expected.getMessage().contains("Expected value at path $.null[1]"));
    }
  }

  @Test
  @Ignore
  public void strictVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.beginArray();
    try {
      assertEquals(reader.nextDouble(), 1d);
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  @Ignore
  public void lenientVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    assertEquals(reader.nextDouble(), 1d);
    reader.endArray();
    assertEquals(reader.peek(), JsonReader.Token.END_DOCUMENT);
  }

  @Test
  public void veryLongUnquotedLiteral() throws IOException {
    String literal = "a" + repeat('b', 8192) + "c";
    JsonReader reader = newReader("[" + literal + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.nextString(), literal);
    reader.endArray();
  }

  @Test
  public void tooDeeplyNestedArrays() throws IOException {
    JsonReader reader = newReader(repeat("[", MAX_DEPTH + 1) + repeat("]", MAX_DEPTH + 1));
    for (int i = 0; i < MAX_DEPTH; i++) {
      reader.beginArray();
    }
    try {
      reader.beginArray();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(
          expected.getMessage().contains("Nesting too deep at $" + repeat("[0]", MAX_DEPTH)));
    }
  }

  @Test
  public void tooDeeplyNestedObjects() throws IOException {
    // Build a JSON document structured like {"a":{"a":{"a":{"a":true}}}}, but 255 levels deep.
    String array = "{\"a\":%s}";
    String json = "true";
    for (int i = 0; i < MAX_DEPTH + 1; i++) {
      json = String.format(array, json);
    }

    JsonReader reader = newReader(json);
    for (int i = 0; i < MAX_DEPTH; i++) {
      reader.beginObject();
      assertEquals(reader.nextName(), "a");
    }
    try {
      reader.beginObject();
      fail();
    } catch (JsonDataException expected) {
      assertTrue(expected.getMessage().contains("Nesting too deep at $" + repeat(".a", MAX_DEPTH)));
    }
  }

  // http://code.google.com/p/google-gson/issues/detail?id=409
  @Test
  public void stringEndingInSlash() throws IOException {
    JsonReader reader = newReader("/");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void documentWithCommentEndingInSlash() throws IOException {
    JsonReader reader = newReader("/* foo *//");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void stringWithLeadingSlash() throws IOException {
    JsonReader reader = newReader("/x");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void unterminatedObject() throws IOException {
    JsonReader reader = newReader("{\"a\":\"android\"x");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextString(), "android");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void veryLongQuotedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[\"" + string + "\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertEquals(reader.nextString(), string);
    reader.endArray();
  }

  @Test
  public void veryLongUnquotedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[" + string + "]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.nextString(), string);
    reader.endArray();
  }

  @Test
  public void veryLongUnterminatedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[" + string;
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.nextString(), string);
    try {
      reader.peek();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void strictExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextString(), "b");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void lenientExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.setLenient(true);
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    assertEquals(reader.nextString(), "b");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void malformedDocuments() throws IOException {
    assertDocument("{]", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{,", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{{", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{[", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{:", BEGIN_OBJECT, JsonEncodingException.class);
    assertDocument("{\"name\",", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\":}", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\"::", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\":,", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\"=}", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{\"name\"=>}", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument(
        "{\"name\"=>\"string\":", BEGIN_OBJECT, NAME, STRING, JsonEncodingException.class);
    assertDocument(
        "{\"name\"=>\"string\"=", BEGIN_OBJECT, NAME, STRING, JsonEncodingException.class);
    assertDocument(
        "{\"name\"=>\"string\"=>", BEGIN_OBJECT, NAME, STRING, JsonEncodingException.class);
    assertDocument("{\"name\"=>\"string\",", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\"=>\"string\",\"name\"", BEGIN_OBJECT, NAME, STRING, NAME);
    assertDocument("[}", BEGIN_ARRAY, JsonEncodingException.class);
    assertDocument("[,]", BEGIN_ARRAY, NULL, NULL, END_ARRAY);
    assertDocument("{", BEGIN_OBJECT, EOFException.class);
    assertDocument("{\"name\"", BEGIN_OBJECT, NAME, EOFException.class);
    assertDocument("{\"name\",", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{'name'", BEGIN_OBJECT, NAME, EOFException.class);
    assertDocument("{'name',", BEGIN_OBJECT, NAME, JsonEncodingException.class);
    assertDocument("{name", BEGIN_OBJECT, NAME, EOFException.class);
    assertDocument("[", BEGIN_ARRAY, EOFException.class);
    assertDocument("[string", BEGIN_ARRAY, STRING, EOFException.class);
    assertDocument("[\"string\"", BEGIN_ARRAY, STRING, EOFException.class);
    assertDocument("['string'", BEGIN_ARRAY, STRING, EOFException.class);
    assertDocument("[123", BEGIN_ARRAY, NUMBER, EOFException.class);
    assertDocument("[123,", BEGIN_ARRAY, NUMBER, EOFException.class);
    assertDocument("{\"name\":123", BEGIN_OBJECT, NAME, NUMBER, EOFException.class);
    assertDocument("{\"name\":123,", BEGIN_OBJECT, NAME, NUMBER, EOFException.class);
    assertDocument("{\"name\":\"string\"", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":\"string\",", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":'string'", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":'string',", BEGIN_OBJECT, NAME, STRING, EOFException.class);
    assertDocument("{\"name\":false", BEGIN_OBJECT, NAME, BOOLEAN, EOFException.class);
    assertDocument("{\"name\":false,,", BEGIN_OBJECT, NAME, BOOLEAN, JsonEncodingException.class);
  }

  /**
   * This test behave slightly differently in Gson 2.2 and earlier. It fails during peek rather than
   * during nextString().
   */
  @Test
  public void unterminatedStringFailure() throws IOException {
    JsonReader reader = newReader("[\"string");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.peek(), JsonReader.Token.STRING);
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test
  public void invalidEscape() throws IOException {
    JsonReader reader = newReader("[\"str\\ing\"]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
      assertTrue(expected.getMessage().contains("Invalid escape sequence: \\i at path $[0]"));
    }
  }

  @Test
  public void lenientInvalidEscape() throws IOException {
    JsonReader reader = newReader("[\"str\\ing\"]");
    reader.setLenient(true);
    reader.beginArray();
    assertEquals(reader.nextString(), "string");
  }

  private void assertDocument(String document, Object... expectations) throws IOException {
    JsonReader reader = newReader(document);
    reader.setLenient(true);
    for (Object expectation : expectations) {
      if (expectation == BEGIN_OBJECT) {
        reader.beginObject();
      } else if (expectation == BEGIN_ARRAY) {
        reader.beginArray();
      } else if (expectation == END_OBJECT) {
        reader.endObject();
      } else if (expectation == END_ARRAY) {
        reader.endArray();
      } else if (expectation == NAME) {
        assertEquals(reader.nextName(), "name");
      } else if (expectation == BOOLEAN) {
        assertFalse(reader.nextBoolean());
      } else if (expectation == STRING) {
        assertEquals(reader.nextString(), "string");
      } else if (expectation == NUMBER) {
        assertEquals(reader.nextInt(), 123);
      } else if (expectation == NULL) {
        reader.nextNull();
      } else if (expectation instanceof Class
          && Exception.class.isAssignableFrom((Class<?>) expectation)) {
        try {
          reader.peek();
          fail();
        } catch (Exception expected) {
          assertEquals(expected.toString(), expectation, expected.getClass());
        }
      } else {
        throw new AssertionError();
      }
    }
  }

  @Test
  public void nextSourceObject_withWhitespace() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\n  \"a\": {\n    \"b\": 2,\n    \"c\": 3\n  }\n}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "{\n    \"b\": 2,\n    \"c\": 3\n  }");
    }
  }

  @Test
  public void nextSourceLong_WithWhitespace() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\n  \"a\": -2\n}");
    reader.beginObject();
    assertEquals(reader.nextName(), "a");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "-2");
    }
  }

  /**
   * Confirm that {@link JsonReader#nextSource} doesn't load data from the underlying stream until
   * its required by the caller. If the source is backed by a slow network stream, we want users to
   * get data as it arrives.
   *
   * <p>Because we don't have a slow stream in this test, we just add bytes to our underlying stream
   * immediately before they're needed.
   */
  @Test
  public void nextSourceStreams() throws IOException {
    Buffer stream = new Buffer();
    stream.writeUtf8("[\"");

    JsonReader reader = JsonReader.of(Okio.buffer((Source) stream));
    reader.beginArray();
    BufferedSource source = reader.nextSource();
    assertEquals(source.readUtf8(1), "\"");
    stream.writeUtf8("hello");
    assertEquals(source.readUtf8(5), "hello");
    stream.writeUtf8("world");
    assertEquals(source.readUtf8(5), "world");
    stream.writeUtf8("\"");
    assertEquals(source.readUtf8(1), "\"");
    stream.writeUtf8("]");
    assertTrue(source.exhausted());
    reader.endArray();
  }

  @Test
  public void nextSourceObjectAfterSelect() throws IOException {
    // language=JSON
    JsonReader reader = newReader("[\"p\u0065psi\"]");
    reader.beginArray();
    assertEquals(reader.selectName(JsonReader.Options.of("coke")), -1);
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "\"pepsi\""); // not the original characters!
    }
  }

  @Test
  public void nextSourceObjectAfterPromoteNameToValue() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":true}");
    reader.beginObject();
    reader.promoteNameToValue();
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(valueSource.readUtf8(), "\"a\"");
    }
    assertTrue(reader.nextBoolean());
    reader.endObject();
  }

  @Test
  public void nextSourcePath() throws IOException {
    // language=JSON
    JsonReader reader = newReader("{\"a\":true,\"b\":[],\"c\":false}");
    reader.beginObject();

    assertEquals(reader.nextName(), "a");
    assertEquals(reader.getPath(), "$.a");
    assertTrue(reader.nextBoolean());
    assertEquals(reader.getPath(), "$.a");

    assertEquals(reader.nextName(), "b");
    try (BufferedSource valueSource = reader.nextSource()) {
      assertEquals(reader.getPath(), "$.b");
      assertEquals(valueSource.readUtf8(), "[]");
    }
    assertEquals(reader.getPath(), "$.b");

    assertEquals(reader.nextName(), "c");
    assertEquals(reader.getPath(), "$.c");
    assertFalse(reader.nextBoolean());
    assertEquals(reader.getPath(), "$.c");
    reader.endObject();
  }
}
