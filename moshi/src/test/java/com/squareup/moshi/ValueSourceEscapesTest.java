package com.squareup.moshi;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Ignore;
import org.junit.Test;

public final class ValueSourceEscapesTest {
  @Test
  public void escapedDoubleQuote() throws IOException {
    testSpecialCharacter("\\\"escaped\\\"");
  }

  @Ignore
  @Test
  public void escapedSingleQuote() throws IOException {
    testSpecialCharacter("\\'escaped\\'");
  }

  @Test
  public void newLine1() throws IOException {
    testSpecialCharacter("\\n");
  }

  @Test
  public void newLine2() throws IOException {
    testSpecialCharacter("\\r\\n");
  }

  @Test
  public void stringTerminals() throws IOException {
    testSpecialCharacter("{}[]:, \\n\\t\\r\\f/\\\\;#=");
  }

  @Ignore
  @Test
  public void backlash1() throws IOException {
    testSpecialCharacter("\\/");
  }

  @Test
  public void backlash2() throws IOException {
    testSpecialCharacter("\\\\");
  }

  private void testSpecialCharacter(String value) throws IOException {
    String input = "{\"a\":\"" + value + "\"}";
    JsonReader reader =
        JsonReader.of(Okio.buffer(Okio.source(new ByteArrayInputStream(input.getBytes()))));
    Buffer out = new Buffer();
    JsonWriter writer = JsonWriter.of(out);

    reader.beginObject();
    writer.beginObject();

    writer.name(reader.nextName());
    BufferedSource source = reader.valueSource();
    try (JsonReader localReader = JsonReader.of(source)) {
      writer.value(localReader.nextString());
    }

    reader.endObject();
    writer.endObject();

    writer.flush();

    String streamed = out.clone().readUtf8();

    JsonReader.of(out).readJsonValue();

    assertEquals(input, streamed);
  }
}
