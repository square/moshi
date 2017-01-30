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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.squareup.moshi.JsonScope.EMPTY_ARRAY;
import static com.squareup.moshi.JsonScope.EMPTY_DOCUMENT;
import static com.squareup.moshi.JsonScope.EMPTY_OBJECT;
import static com.squareup.moshi.JsonScope.NONEMPTY_DOCUMENT;

/** Writes JSON by building a Java object comprising maps, lists, and JSON primitives. */
final class ObjectJsonWriter extends JsonWriter {
  private final Object[] stack = new Object[32];
  private String deferredName;

  ObjectJsonWriter() {
    pushScope(EMPTY_DOCUMENT);
  }

  public Object root() {
    int size = stackSize;
    if (size > 1 || size == 1 && scopes[size - 1] != NONEMPTY_DOCUMENT) {
      throw new IllegalStateException("Incomplete document");
    }
    return stack[0];
  }

  @Override public JsonWriter beginArray() throws IOException {
    if (stackSize == stack.length) {
      throw new JsonDataException("Nesting too deep at " + getPath() + ": circular reference?");
    }
    List<Object> list = new ArrayList<>();
    add(list);
    stack[stackSize] = list;
    pathIndices[stackSize] = 0;
    pushScope(EMPTY_ARRAY);
    return this;
  }

  @Override public JsonWriter endArray() throws IOException {
    if (peekScope() != EMPTY_ARRAY) {
      throw new IllegalStateException("Nesting problem.");
    }
    stackSize--;
    stack[stackSize] = null;
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public JsonWriter beginObject() throws IOException {
    if (stackSize == stack.length) {
      throw new JsonDataException("Nesting too deep at " + getPath() + ": circular reference?");
    }
    Map<String, Object> map = new LinkedHashTreeMap<>();
    add(map);
    stack[stackSize] = map;
    pushScope(EMPTY_OBJECT);
    return this;
  }

  @Override public JsonWriter endObject() throws IOException {
    if (peekScope() != EMPTY_OBJECT || deferredName != null) {
      throw new IllegalStateException("Nesting problem.");
    }
    promoteValueToName = false;
    stackSize--;
    stack[stackSize] = null;
    pathNames[stackSize] = null; // Free the last path name so that it can be garbage collected!
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public JsonWriter name(String name) throws IOException {
    if (name == null) {
      throw new NullPointerException("name == null");
    }
    if (stackSize == 0) {
      throw new IllegalStateException("JsonWriter is closed.");
    }
    if (peekScope() != EMPTY_OBJECT || deferredName != null) {
      throw new IllegalStateException("Nesting problem.");
    }
    deferredName = name;
    pathNames[stackSize - 1] = name;
    promoteValueToName = false;
    return this;
  }

  @Override public JsonWriter value(String value) throws IOException {
    if (promoteValueToName) {
      return name(value);
    }
    add(value);
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public JsonWriter nullValue() throws IOException {
    add(null);
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public JsonWriter value(boolean value) throws IOException {
    add(value);
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public JsonWriter value(Boolean value) throws IOException {
    add(value);
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public JsonWriter value(double value) throws IOException {
    return value(Double.valueOf(value));
  }

  @Override public JsonWriter value(long value) throws IOException {
    if (promoteValueToName) {
      return name(Long.toString(value));
    }
    add(value);
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public JsonWriter value(Number value) throws IOException {
    if (!lenient) {
      double d = value.doubleValue();
      if (d == Double.POSITIVE_INFINITY || d == Double.NEGATIVE_INFINITY || Double.isNaN(d)) {
        throw new IllegalArgumentException("Numeric values must be finite, but was " + value);
      }
    }
    if (promoteValueToName) {
      return name(value.toString());
    }
    add(value);
    pathIndices[stackSize - 1]++;
    return this;
  }

  @Override public void close() throws IOException {
    int size = stackSize;
    if (size > 1 || size == 1 && scopes[size - 1] != NONEMPTY_DOCUMENT) {
      throw new IOException("Incomplete document");
    }
    stackSize = 0;
  }

  @Override public void flush() throws IOException {
    if (stackSize == 0) {
      throw new IllegalStateException("JsonWriter is closed.");
    }
  }

  private ObjectJsonWriter add(Object newTop) {
    int scope = peekScope();

    if (stackSize == 1) {
      if (scope != EMPTY_DOCUMENT) {
        throw new IllegalStateException("JSON must have only one top-level value.");
      }
      scopes[stackSize - 1] = NONEMPTY_DOCUMENT;
      stack[stackSize - 1] = newTop;

    } else if (scope == EMPTY_OBJECT && deferredName != null) {
      if (newTop != null || serializeNulls) {
        @SuppressWarnings("unchecked") // Our maps always have string keys and object values.
        Map<String, Object> map = (Map<String, Object>) stack[stackSize - 1];
        Object replaced = map.put(deferredName, newTop);
        if (replaced != null) {
          throw new IllegalArgumentException("Map key '" + deferredName
              + "' has multiple values at path " + getPath() + ": " + replaced + " and " + newTop);
        }
      }
      deferredName = null;

    } else if (scope == EMPTY_ARRAY) {
      @SuppressWarnings("unchecked") // Our lists always have object values.
      List<Object> list = (List<Object>) stack[stackSize - 1];
      list.add(newTop);

    } else {
      throw new IllegalStateException("Nesting problem.");
    }

    return this;
  }
}
