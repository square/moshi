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

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import okio.Buffer;
import okio.ForwardingSource;
import okio.Okio;
import org.junit.Ignore;
import org.junit.Test;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class JsonUtf8ReaderTest {
  @Test public void readingDoesNotBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{}{}");

    JsonReader reader1 = JsonReader.of(buffer);
    reader1.beginObject();
    reader1.endObject();
    assertThat(buffer.size()).isEqualTo(2);

    JsonReader reader2 = JsonReader.of(buffer);
    reader2.beginObject();
    reader2.endObject();
    assertThat(buffer.size()).isEqualTo(0);
  }

  @Test public void readObjectBuffer() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = JsonReader.of(buffer);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextString()).isEqualTo("banana");
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readObjectSource() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("{\"a\": \"android\", \"b\": \"banana\"}");
    JsonReader reader = JsonReader.of(Okio.buffer(new ForwardingSource(buffer) {}));
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextString()).isEqualTo("banana");
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void nullSource() {
    try {
      JsonReader.of(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void unescapingInvalidCharacters() throws IOException {
    String json = "[\"\\u000g\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void unescapingTruncatedCharacters() throws IOException {
    String json = "[\"\\u000";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void unescapingTruncatedSequence() throws IOException {
    String json = "[\"\\";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictNonFiniteDoublesWithSkipValue() throws IOException {
    String json = "[NaN]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test @Ignore public void numberWithOctalPrefix() throws IOException {
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
    assertThat(reader.nextString()).isEqualTo("01");
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void peekingUnquotedStringsPrefixedWithBooleans() throws IOException {
    JsonReader reader = newReader("[truey]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("truey");
    reader.endArray();
  }

  @Test public void malformedNumbers() throws IOException {
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
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextString()).isEqualTo(s);
    reader.endArray();
  }

  @Test public void peekingUnquotedStringsPrefixedWithIntegers() throws IOException {
    JsonReader reader = newReader("[12.34e5x]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
    try {
      reader.nextInt();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextString()).isEqualTo("12.34e5x");
  }

  @Test public void peekLongMinValue() throws IOException {
    JsonReader reader = newReader("[-9223372036854775808]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    assertThat(reader.nextLong()).isEqualTo(-9223372036854775808L);
  }

  @Test public void peekLongMaxValue() throws IOException {
    JsonReader reader = newReader("[9223372036854775807]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    assertThat(reader.nextLong()).isEqualTo(9223372036854775807L);
  }

  @Test public void longLargerThanMaxLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test public void longLargerThanMinLongThatWrapsAround() throws IOException {
    JsonReader reader = newReader("[-22233720368547758070]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test public void peekLargerThanLongMaxValue() throws IOException {
    JsonReader reader = newReader("[9223372036854775808]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test public void precisionNotDiscarded() throws IOException {
    JsonReader reader = newReader("[9223372036854775806.5]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
  }

  @Test public void peekLargerThanLongMinValue() throws IOException {
    JsonReader reader = newReader("[-9223372036854775809]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextDouble()).isEqualTo(-9223372036854775809d);
  }

  @Test public void highPrecisionLong() throws IOException {
    String json = "[9223372036854775806.000]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertThat(reader.nextLong()).isEqualTo(9223372036854775806L);
    reader.endArray();
  }

  @Test public void peekMuchLargerThanLongMinValue() throws IOException {
    JsonReader reader = newReader("[-92233720368547758080]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(NUMBER);
    try {
      reader.nextLong();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextDouble()).isEqualTo(-92233720368547758080d);
  }

  @Test public void negativeZeroIsANumber() throws Exception {
    JsonReader reader = newReader("-0");
    assertEquals(NUMBER, reader.peek());
    assertEquals("-0", reader.nextString());
  }

  @Test public void numberToStringCoersion() throws Exception {
    JsonReader reader = newReader("[0, 9223372036854775807, 2.5, 3.010, \"a\", \"5\"]");
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("0");
    assertThat(reader.nextString()).isEqualTo("9223372036854775807");
    assertThat(reader.nextString()).isEqualTo("2.5");
    assertThat(reader.nextString()).isEqualTo("3.010");
    assertThat(reader.nextString()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("5");
    reader.endArray();
  }

  @Test public void quotedNumberWithEscape() throws IOException {
    JsonReader reader = newReader("[\"12\u00334\"]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(STRING);
    assertThat(reader.nextInt()).isEqualTo(1234);
  }

  @Test public void mixedCaseLiterals() throws IOException {
    JsonReader reader = newReader("[True,TruE,False,FALSE,NULL,nulL]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isFalse();
    assertThat(reader.nextBoolean()).isFalse();
    reader.nextNull();
    reader.nextNull();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void missingValue() throws IOException {
    JsonReader reader = newReader("{\"a\":}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void prematureEndOfInput() throws IOException {
    JsonReader reader = newReader("{\"a\":true,");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.nextName();
      fail();
    } catch (EOFException expected) {
    }
  }

  @SuppressWarnings("CheckReturnValue")
  @Test public void prematurelyClosed() throws IOException {
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

  @Test public void strictNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientNameValueSeparator() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("{\"a\"=>true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();
  }

  @Test public void strictNameValueSeparatorWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\"=true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }

    reader = newReader("{\"a\"=>true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void commentsInStringValue() throws Exception {
    JsonReader reader = newReader("[\"// comment\"]");
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("// comment");
    reader.endArray();

    reader = newReader("{\"a\":\"#someComment\"}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("#someComment");
    reader.endObject();

    reader = newReader("{\"#//a\":\"#some //Comment\"}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("#//a");
    assertThat(reader.nextString()).isEqualTo("#some //Comment");
    reader.endObject();
  }

  @Test public void strictComments() throws IOException {
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

  @Test public void lenientComments() throws IOException {
    JsonReader reader = newReader("[// comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("[# comment \n true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("[/* comment */ true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();

    reader = newReader("a//");
    reader.setLenient(true);
    assertThat(reader.nextString()).isEqualTo("a");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictCommentsWithSkipValue() throws IOException {
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

  @Test public void strictUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientUnquotedNames() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
  }

  @Test public void jsonIsSingleUnquotedString() throws IOException {
    JsonReader reader = newReader("abc");
    reader.setLenient(true);
    assertThat(reader.nextString()).isEqualTo("abc");
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictUnquotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{a:true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSingleQuotedNames() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
  }

  @Test public void strictSingleQuotedNamesWithSkipValue() throws IOException {
    JsonReader reader = newReader("{'a':true}");
    reader.beginObject();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictUnquotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientUnquotedStrings() throws IOException {
    JsonReader reader = newReader("[a]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
  }

  @Test public void lenientUnquotedStringsDelimitedByComment() throws IOException {
    JsonReader reader = newReader("[a#comment\n]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
    reader.endArray();
  }

  @Test public void strictSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSingleQuotedStrings() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
  }

  @Test public void strictSingleQuotedStringsWithSkipValue() throws IOException {
    JsonReader reader = newReader("['a']");
    reader.beginArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.nextBoolean();
      reader.nextBoolean();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSemicolonDelimitedArray() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isTrue();
  }

  @Test public void strictSemicolonDelimitedArrayWithSkipValue() throws IOException {
    JsonReader reader = newReader("[true;true]");
    reader.beginArray();
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextBoolean();
      reader.nextName();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientSemicolonDelimitedNameValuePair() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextName()).isEqualTo("b");
  }

  @Test public void strictSemicolonDelimitedNameValuePairWithSkipValue() throws IOException {
    JsonReader reader = newReader("{\"a\":true;\"b\":true}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void strictUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
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
    assertThat(reader.nextBoolean()).isTrue();
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

  @Test public void lenientUnnecessaryArraySeparators() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    reader.nextNull();
    assertThat(reader.nextBoolean()).isTrue();
    reader.endArray();

    reader = newReader("[,true]");
    reader.setLenient(true);
    reader.beginArray();
    reader.nextNull();
    assertThat(reader.nextBoolean()).isTrue();
    reader.endArray();

    reader = newReader("[true,]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    reader.nextNull();
    reader.endArray();

    reader = newReader("[,]");
    reader.setLenient(true);
    reader.beginArray();
    reader.nextNull();
    reader.nextNull();
    reader.endArray();
  }

  @Test public void strictUnnecessaryArraySeparatorsWithSkipValue() throws IOException {
    JsonReader reader = newReader("[true,,true]");
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
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
    assertThat(reader.nextBoolean()).isTrue();
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

  @Test public void strictMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientMultipleTopLevelValues() throws IOException {
    JsonReader reader = newReader("[] true {}");
    reader.setLenient(true);
    reader.beginArray();
    reader.endArray();
    assertThat(reader.nextBoolean()).isTrue();
    reader.beginObject();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void strictMultipleTopLevelValuesWithSkipValue() throws IOException {
    JsonReader reader = newReader("[] []");
    reader.beginArray();
    reader.endArray();
    try {
      reader.skipValue();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test @Ignore public void bomIgnoredAsFirstCharacterOfDocument() throws IOException {
    JsonReader reader = newReader("\ufeff[]");
    reader.beginArray();
    reader.endArray();
  }

  @Test public void bomForbiddenAsOtherCharacterInDocument() throws IOException {
    JsonReader reader = newReader("[\ufeff]");
    reader.beginArray();
    try {
      reader.endArray();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void failWithPosition() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "[\n\n\n\n\n\"a\",}]");
  }

  @Test public void failWithPositionGreaterThanBufferSize() throws IOException {
    String spaces = repeat(' ', 8192);
    testFailWithPosition("Expected value at path $[1]",
        "[\n\n" + spaces + "\n\n\n\"a\",}]");
  }

  @Test public void failWithPositionOverSlashSlashEndOfLineComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "\n// foo\n\n//bar\r\n[\"a\",}");
  }

  @Test public void failWithPositionOverHashEndOfLineComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "\n# foo\n\n#bar\r\n[\"a\",}");
  }

  @Test public void failWithPositionOverCStyleComment() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "\n\n/* foo\n*\n*\r\nbar */[\"a\",}");
  }

  @Test public void failWithPositionOverQuotedString() throws IOException {
    testFailWithPosition("Expected value at path $[1]",
        "[\"foo\nbar\r\nbaz\n\",\n  }");
  }

  @Test public void failWithPositionOverUnquotedString() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\n\nabcd\n\n,}");
  }

  @Test public void failWithEscapedNewlineCharacter() throws IOException {
    testFailWithPosition("Expected value at path $[1]", "[\n\n\"\\\n\n\",}");
  }

  @Test @Ignore public void failWithPositionIsOffsetByBom() throws IOException {
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
      assertThat(expected).hasMessage(message);
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
      assertThat(expected).hasMessage(message);
    }
  }

  @SuppressWarnings("CheckReturnValue")
  @Test public void failWithPositionDeepPath() throws IOException {
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
      assertThat(expected).hasMessage("Expected value at path $[1].a[2]");
    }
  }

  @Test public void failureMessagePathFromSkipName() throws IOException {
    JsonReader reader = newReader("{\"a\":[42,}");
    reader.beginObject();
    reader.skipName();
    reader.beginArray();
    reader.nextInt();
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
      assertThat(expected).hasMessage("Expected value at path $.null[1]");
    }
  }

  @Test @Ignore public void strictVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.beginArray();
    try {
      assertThat(reader.nextDouble()).isEqualTo(1d);
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test @Ignore public void lenientVeryLongNumber() throws IOException {
    JsonReader reader = newReader("[0." + repeat('9', 8192) + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    assertThat(reader.nextDouble()).isEqualTo(1d);
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void veryLongUnquotedLiteral() throws IOException {
    String literal = "a" + repeat('b', 8192) + "c";
    JsonReader reader = newReader("[" + literal + "]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo(literal);
    reader.endArray();
  }

  @Test public void tooDeeplyNestedArrays() throws IOException {
    JsonReader reader = newReader(repeat("[", MAX_DEPTH + 1) + repeat("]", MAX_DEPTH + 1));
    for (int i = 0; i < MAX_DEPTH; i++) {
      reader.beginArray();
    }
    try {
      reader.beginArray();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Nesting too deep at $" + repeat("[0]", MAX_DEPTH));
    }
  }

  @Test public void tooDeeplyNestedObjects() throws IOException {
    // Build a JSON document structured like {"a":{"a":{"a":{"a":true}}}}, but 255 levels deep.
    String array = "{\"a\":%s}";
    String json = "true";
    for (int i = 0; i < MAX_DEPTH + 1; i++) {
      json = String.format(array, json);
    }

    JsonReader reader = newReader(json);
    for (int i = 0; i < MAX_DEPTH; i++) {
      reader.beginObject();
      assertThat(reader.nextName()).isEqualTo("a");
    }
    try {
      reader.beginObject();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Nesting too deep at $" + repeat(".a", MAX_DEPTH));
    }
  }

  // http://code.google.com/p/google-gson/issues/detail?id=409
  @Test public void stringEndingInSlash() throws IOException {
    JsonReader reader = newReader("/");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void documentWithCommentEndingInSlash() throws IOException {
    JsonReader reader = newReader("/* foo *//");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void stringWithLeadingSlash() throws IOException {
    JsonReader reader = newReader("/x");
    reader.setLenient(true);
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void unterminatedObject() throws IOException {
    JsonReader reader = newReader("{\"a\":\"android\"x");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void veryLongQuotedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[\"" + string + "\"]";
    JsonReader reader = newReader(json);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo(string);
    reader.endArray();
  }

  @Test public void veryLongUnquotedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[" + string + "]";
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo(string);
    reader.endArray();
  }

  @Test public void veryLongUnterminatedString() throws IOException {
    char[] stringChars = new char[1024 * 16];
    Arrays.fill(stringChars, 'x');
    String string = new String(stringChars);
    String json = "[" + string;
    JsonReader reader = newReader(json);
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo(string);
    try {
      reader.peek();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void strictExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("b");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void lenientExtraCommasInMaps() throws IOException {
    JsonReader reader = newReader("{\"a\":\"b\",}");
    reader.setLenient(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("b");
    try {
      reader.peek();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void malformedDocuments() throws IOException {
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
    assertDocument("{\"name\"=>\"string\":", BEGIN_OBJECT, NAME, STRING,
        JsonEncodingException.class);
    assertDocument("{\"name\"=>\"string\"=", BEGIN_OBJECT, NAME, STRING,
        JsonEncodingException.class);
    assertDocument("{\"name\"=>\"string\"=>", BEGIN_OBJECT, NAME, STRING,
        JsonEncodingException.class);
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
   * This test behave slightly differently in Gson 2.2 and earlier. It fails
   * during peek rather than during nextString().
   */
  @Test public void unterminatedStringFailure() throws IOException {
    JsonReader reader = newReader("[\"string");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.STRING);
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
    }
  }

  @Test public void invalidEscape() throws IOException {
    JsonReader reader = newReader("[\"str\\ing\"]");
    reader.beginArray();
    try {
      reader.nextString();
      fail();
    } catch (JsonEncodingException expected) {
      assertThat(expected).hasMessage("Invalid escape sequence: \\i at path $[0]");
    }
  }

  @Test public void lenientInvalidEscape() throws IOException {
    JsonReader reader = newReader("[\"str\\ing\"]");
    reader.setLenient(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("string");
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
        assertThat(reader.nextName()).isEqualTo("name");
      } else if (expectation == BOOLEAN) {
        assertThat(reader.nextBoolean()).isFalse();
      } else if (expectation == STRING) {
        assertThat(reader.nextString()).isEqualTo("string");
      } else if (expectation == NUMBER) {
        assertThat(reader.nextInt()).isEqualTo(123);
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
}
