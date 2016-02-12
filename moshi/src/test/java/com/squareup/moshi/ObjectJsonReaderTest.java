package com.squareup.moshi;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static com.squareup.moshi.JsonReader.Token.BEGIN_ARRAY;
import static com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class ObjectJsonReaderTest {
  @Test public void readArray() throws IOException {
    JsonReader reader = new ObjectJsonReader(Arrays.asList(true, true));
    reader.beginArray();
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextBoolean()).isTrue();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readEmptyArray() throws IOException {
    JsonReader reader = new ObjectJsonReader(emptyList());
    reader.beginArray();
    assertThat(reader.hasNext()).isFalse();
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readObject() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("a", "android");
    object.put("b", "banana");
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.nextString()).isEqualTo("android");
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextString()).isEqualTo("banana");
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void readEmptyObject() throws IOException {
    JsonReader reader = new ObjectJsonReader(Collections.emptyMap());
    reader.beginObject();
    assertThat(reader.hasNext()).isFalse();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipArray() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("a", Arrays.asList("one", "two", "three"));
    object.put("b", 123);
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipArrayAfterPeek() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("a", Arrays.asList("one", "two", "three"));
    object.put("b", 123);
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    assertThat(reader.peek()).isEqualTo(BEGIN_ARRAY);
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipTopLevelObject() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("a", Arrays.asList("one", "two", "three"));
    object.put("b", 123);
    JsonReader reader = new ObjectJsonReader(object);
    reader.skipValue();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipObject() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    Map<String, Object> nestedObject = new LinkedHashMap<>();
    nestedObject.put("c", emptyList());
    nestedObject.put("d", Arrays.asList(true, true, Collections.emptyMap()));
    object.put("a", nestedObject);
    object.put("b", "banana");
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipObjectAfterPeek() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("one", singletonMap("num", 1));
    object.put("two", singletonMap("num", 2));
    object.put("three", singletonMap("num", 3));
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("one");
    assertThat(reader.peek()).isEqualTo(BEGIN_OBJECT);
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("two");
    assertThat(reader.peek()).isEqualTo(BEGIN_OBJECT);
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("three");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipInteger() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("a", 123456789);
    object.put("b", -123456789);
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void skipDouble() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("a", Double.MIN_VALUE);
    object.put("b", Double.MAX_VALUE);
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    reader.skipValue();
    assertThat(reader.nextName()).isEqualTo("b");
    reader.skipValue();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void failOnUnknownFailsOnUnknownObjectValue() throws IOException {
    Map<String, Object> object = Collections.<String, Object>singletonMap("a", 123);
    JsonReader reader = new ObjectJsonReader(object);
    reader.setFailOnUnknown(true);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Cannot skip unexpected NUMBER at $.a");
    }
    // Confirm that the reader is left in a consistent state after the exception.
    reader.setFailOnUnknown(false);
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void failOnUnknownFailsOnUnknownArrayElement() throws IOException {
    JsonReader reader = new ObjectJsonReader(Arrays.asList("a", 123));
    reader.setFailOnUnknown(true);
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("a");
    try {
      reader.skipValue();
      fail();
    } catch (JsonDataException expected) {
      assertThat(expected).hasMessage("Cannot skip unexpected NUMBER at $[1]");
    }
    // Confirm that the reader is left in a consistent state after the exception.
    reader.setFailOnUnknown(false);
    assertThat(reader.nextInt()).isEqualTo(123);
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void helloWorld() throws IOException {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("hello", true);
    object.put("foo", Collections.singletonList("world"));
    JsonReader reader = new ObjectJsonReader(object);
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("hello");
    assertThat(reader.nextBoolean()).isTrue();
    assertThat(reader.nextName()).isEqualTo("foo");
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("world");
    reader.endArray();
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void integerFromOtherNumberTypes() throws IOException {
    JsonReader reader = new ObjectJsonReader(Arrays.asList(1, 1L, 1.0f, 1.0));
    reader.beginArray();
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.nextInt()).isEqualTo(1);
    assertThat(reader.nextInt()).isEqualTo(1);
    reader.endArray();
  }

  @Test public void longFromOtherNumberTypes() throws IOException {
    JsonReader reader = new ObjectJsonReader(Arrays.asList(1, 1L, 1.0f, 1.0));
    reader.beginArray();
    assertThat(reader.nextLong()).isEqualTo(1L);
    assertThat(reader.nextLong()).isEqualTo(1L);
    assertThat(reader.nextLong()).isEqualTo(1L);
    assertThat(reader.nextLong()).isEqualTo(1L);
    reader.endArray();
  }

  @Test public void doubleFromOtherNumberTypes() throws IOException {
    JsonReader reader = new ObjectJsonReader(Arrays.asList(1, 1L, 1.0f, 1.0));
    reader.beginArray();
    assertThat(reader.nextDouble()).isEqualTo(1.0);
    assertThat(reader.nextDouble()).isEqualTo(1.0);
    assertThat(reader.nextDouble()).isEqualTo(1.0);
    assertThat(reader.nextDouble()).isEqualTo(1.0);
    reader.endArray();
  }

  @Test public void stringFromNumberTypes() throws IOException {
    JsonReader reader = new ObjectJsonReader(Arrays.asList(1, 1L, 1.0f, 1.0));
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("1");
    assertThat(reader.nextString()).isEqualTo("1");
    assertThat(reader.nextString()).isEqualTo("1.0");
    assertThat(reader.nextString()).isEqualTo("1.0");
    reader.endArray();
  }

  @Test public void prematurelyClosed() throws IOException {
    JsonReader reader1 = new ObjectJsonReader(singletonMap("a", emptyList()));
    reader1.beginObject();
    reader1.close();
    try {
      reader1.nextName();
      fail();
    } catch (IllegalStateException expected) {
    }

    JsonReader reader2 = new ObjectJsonReader(singletonMap("a", emptyList()));
    reader2.close();
    try {
      reader2.beginObject();
      fail();
    } catch (IllegalStateException expected) {
    }

    JsonReader reader3 = new ObjectJsonReader(singletonMap("a", true));
    reader3.beginObject();
    reader3.nextName();
    reader3.peek();
    reader3.close();
    try {
      reader3.nextBoolean();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void nextFailuresDoNotAdvance() throws IOException {
    JsonReader reader = new ObjectJsonReader(singletonMap("a", true));
    reader.beginObject();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextName()).isEqualTo("a");
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.beginArray();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.endArray();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.beginObject();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.endObject();
      fail();
    } catch (JsonDataException expected) {
    }
    assertThat(reader.nextBoolean()).isTrue();
    try {
      reader.nextString();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.nextName();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.beginArray();
      fail();
    } catch (JsonDataException expected) {
    }
    try {
      reader.endArray();
      fail();
    } catch (JsonDataException expected) {
    }
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
    reader.close();
  }

  @Test public void integerMismatchWithDoubleDoesNotAdvance() {

  }

  @Test public void topLevelValueTypes() throws IOException {
    JsonReader reader1 = new ObjectJsonReader(true);
    assertThat(reader1.nextBoolean()).isTrue();
    assertThat(reader1.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader2 = new ObjectJsonReader(false);
    assertThat(reader2.nextBoolean()).isFalse();
    assertThat(reader2.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader3 = new ObjectJsonReader(null);
    assertThat(reader3.nextNull()).isNull();
    assertThat(reader3.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader4 = new ObjectJsonReader(123);
    assertThat(reader4.nextLong()).isEqualTo(123);
    assertThat(reader4.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader5 = new ObjectJsonReader(123.4);
    assertThat(reader5.nextDouble()).isEqualTo(123.4);
    assertThat(reader5.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);

    JsonReader reader6 = new ObjectJsonReader("Hi");
    assertThat(reader6.nextString()).isEqualTo("Hi");
    assertThat(reader6.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void list() throws IOException {
    JsonReader reader = new ObjectJsonReader(Arrays.asList("Hello", "World"));
    reader.beginArray();
    assertThat(reader.nextString()).isEqualTo("Hello");
    assertThat(reader.nextString()).isEqualTo("World");
    reader.endArray();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }

  @Test public void map() throws IOException {
    JsonReader reader = new ObjectJsonReader(singletonMap("Hello", "World"));
    reader.beginObject();
    assertThat(reader.nextName()).isEqualTo("Hello");
    assertThat(reader.nextString()).isEqualTo("World");
    reader.endObject();
    assertThat(reader.peek()).isEqualTo(JsonReader.Token.END_DOCUMENT);
  }
}
