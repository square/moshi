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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

public abstract class AbstractJsonReader implements JsonReader {
  // The nesting stack. Using a manual array rather than an ArrayList saves 20%. This stack will
  // grow itself up to 256 levels of nesting including the top-level document. Deeper nesting is
  // prone to trigger StackOverflowErrors.
  int stackSize;
  int[] scopes;
  String[] pathNames;
  int[] pathIndices;

  /** True to accept non-spec compliant JSON. */
  boolean lenient;

  /** True to throw a {@link JsonDataException} on any attempt to call {@link #skipValue()}. */
  boolean failOnUnknown;

  // Package-private to control subclasses.
  AbstractJsonReader() {
    scopes = new int[32];
    pathNames = new String[32];
    pathIndices = new int[32];
  }

  // Package-private to control subclasses.
  AbstractJsonReader(AbstractJsonReader copyFrom) {
    this.stackSize = copyFrom.stackSize;
    this.scopes = copyFrom.scopes.clone();
    this.pathNames = copyFrom.pathNames.clone();
    this.pathIndices = copyFrom.pathIndices.clone();
    this.lenient = copyFrom.lenient;
    this.failOnUnknown = copyFrom.failOnUnknown;
  }

  final void pushScope(int newTop) {
    if (stackSize == scopes.length) {
      if (stackSize == 256) {
        throw new JsonDataException("Nesting too deep at " + getPath());
      }
      scopes = Arrays.copyOf(scopes, scopes.length * 2);
      pathNames = Arrays.copyOf(pathNames, pathNames.length * 2);
      pathIndices = Arrays.copyOf(pathIndices, pathIndices.length * 2);
    }
    scopes[stackSize++] = newTop;
  }

  /**
   * Throws a new IO exception with the given message and a context snippet
   * with this reader's content.
   */
  final JsonEncodingException syntaxError(String message) throws JsonEncodingException {
    throw new JsonEncodingException(message + " at path " + getPath());
  }

  final JsonDataException typeMismatch(@Nullable Object value, Object expected) {
    if (value == null) {
      return new JsonDataException(
          "Expected " + expected + " but was null at path " + getPath());
    } else {
      return new JsonDataException("Expected " + expected + " but was " + value + ", a "
          + value.getClass().getName() + ", at path " + getPath());
    }
  }

  @Override
  public final void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  @Override
  @CheckReturnValue public final boolean isLenient() {
    return lenient;
  }

  @Override
  public final void setFailOnUnknown(boolean failOnUnknown) {
    this.failOnUnknown = failOnUnknown;
  }

  @Override
  @CheckReturnValue public final boolean failOnUnknown() {
    return failOnUnknown;
  }

  @Override
  public final @Nullable Object readJsonValue() throws IOException {
    switch (peek()) {
      case BEGIN_ARRAY:
        List<Object> list = new ArrayList<>();
        beginArray();
        while (hasNext()) {
          list.add(readJsonValue());
        }
        endArray();
        return list;

      case BEGIN_OBJECT:
        Map<String, Object> map = new LinkedHashTreeMap<>();
        beginObject();
        while (hasNext()) {
          String name = nextName();
          Object value = readJsonValue();
          Object replaced = map.put(name, value);
          if (replaced != null) {
            throw new JsonDataException("Map key '" + name + "' has multiple values at path "
                + getPath() + ": " + replaced + " and " + value);
          }
        }
        endObject();
        return map;

      case STRING:
        return nextString();

      case NUMBER:
        return nextDouble();

      case BOOLEAN:
        return nextBoolean();

      case NULL:
        return nextNull();

      default:
        throw new IllegalStateException(
            "Expected a value but was " + peek() + " at path " + getPath());
    }
  }

  @Override
  @CheckReturnValue public final String getPath() {
    return JsonScope.getPath(stackSize, scopes, pathNames, pathIndices);
  }
}
