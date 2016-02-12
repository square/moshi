/*
 * Copyright (C) 2016 Square, Inc.
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
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.squareup.moshi.JsonScope.DANGLING_NAME;
import static com.squareup.moshi.JsonScope.EMPTY_ARRAY;
import static com.squareup.moshi.JsonScope.EMPTY_DOCUMENT;
import static com.squareup.moshi.JsonScope.EMPTY_OBJECT;
import static com.squareup.moshi.JsonScope.NONEMPTY_ARRAY;
import static com.squareup.moshi.JsonScope.NONEMPTY_DOCUMENT;
import static com.squareup.moshi.JsonScope.NONEMPTY_OBJECT;

public final class ObjectJsonWriter extends JsonWriter {
  private final AtomicReference<Object> sink;

  private int[] stack = new int[32];
  private int stackSize = 0;
  {
    push(EMPTY_DOCUMENT);
  }

  private String[] pathNames = new String[32];
  private int[] pathIndices = new int[32];

  private Object[] objects = new Object[32];
  private int objectsSize = 1;

  private boolean lenient;

  private String deferredName;

  private boolean serializeNulls;

  private boolean promoteNameToValue;

  public ObjectJsonWriter(AtomicReference<Object> sink) {
    if (sink == null) throw new NullPointerException("sink == null");
    this.sink = sink;
  }

  private void pushObject(Object newTop) {
    if (objectsSize == objects.length) {
      Object[] newObjects = new Object[objectsSize * 2];
      System.arraycopy(objects, 0, newObjects, 0, objectsSize);
      objects = newObjects;
    }
    objects[objectsSize++] = newTop;
  }

  private Object popObject() {
    Object object = objects[objectsSize - 1];
    objects[objectsSize - 1] = null; // Free the object so that it can be garbage collected!
    objectsSize--;
    return object;
  }

  @Override public void setIndent(String indent) {
    // Ignored
  }

  @Override public final void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  @Override public boolean isLenient() {
    return lenient;
  }

  @Override public final void setSerializeNulls(boolean serializeNulls) {
    this.serializeNulls = serializeNulls;
  }

  @Override public final boolean getSerializeNulls() {
    return serializeNulls;
  }

  @Override public JsonWriter beginArray() {
    pushObject(new LinkedHashMap<String, Object>());
    return open(EMPTY_ARRAY);
  }

  @Override public JsonWriter endArray() {
    return close(EMPTY_ARRAY, NONEMPTY_ARRAY);
  }

  @Override public JsonWriter beginObject() {
    pushObject(new ArrayList<>());
    return open(EMPTY_OBJECT);
  }

  @Override public JsonWriter endObject() {
    return null;
  }

  /**
   * Enters a new scope by appending any necessary whitespace and the given
   * bracket.
   */
  private JsonWriter open(int empty) {
    beforeValue();
    pathIndices[stackSize] = 0;
    push(empty);
    return this;
  }

  /**
   * Closes the current scope by appending any necessary whitespace and the
   * given bracket.
   */
  private JsonWriter close(int empty, int nonempty) {
    int context = peek();
    if (context != nonempty && context != empty) {
      throw new IllegalStateException("Nesting problem.");
    }
    if (deferredName != null) {
      throw new IllegalStateException("Dangling name: " + deferredName);
    }

    stackSize--;
    pathNames[stackSize] = null; // Free the last path name so that it can be garbage collected!
    pathIndices[stackSize - 1]++;
    return this;
  }

  private void push(int newTop) {
    if (stackSize == stack.length) {
      int[] newStack = new int[stackSize * 2];
      System.arraycopy(stack, 0, newStack, 0, stackSize);
      stack = newStack;
    }
    stack[stackSize++] = newTop;
  }

  /**
   * Returns the value on the top of the stack.
   */
  private int peek() {
    if (stackSize == 0) {
      throw new IllegalStateException("JsonWriter is closed.");
    }
    return stack[stackSize - 1];
  }

  /**
   * Replace the value on the top of the stack with the given value.
   */
  private void replaceTop(int topOfStack) {
    stack[stackSize - 1] = topOfStack;
  }

  @Override public JsonWriter name(String name) {
    return null;
  }

  private void writeDeferredName() throws IOException {
    if (deferredName != null) {
      // TODO
      deferredName = null;
    }
  }

  @Override public JsonWriter value(String value) {
    return null;
  }

  @Override public JsonWriter nullValue() {
    return null;
  }

  @Override public JsonWriter value(boolean value) {
    return null;
  }

  @Override public JsonWriter value(double value) {
    return null;
  }

  @Override public JsonWriter value(long value) {
    return null;
  }

  @Override public JsonWriter value(Number value) {
    return null;
  }

  /**
   * Inserts any necessary separators and whitespace before a literal value,
   * inline array, or inline object. Also adjusts the stack to expect either a
   * closing bracket or another element.
   */
  @SuppressWarnings("fallthrough")
  private void beforeValue() {
    switch (peek()) {
      case NONEMPTY_DOCUMENT:
        if (!lenient) {
          throw new IllegalStateException(
              "JSON must have only one top-level value.");
        }
        // fall-through
      case EMPTY_DOCUMENT: // first in document
        replaceTop(NONEMPTY_DOCUMENT);
        break;

      case EMPTY_ARRAY: // first in array
        replaceTop(NONEMPTY_ARRAY);
        break;

      case NONEMPTY_ARRAY: // another in array
        break;

      case DANGLING_NAME: // value for name
        replaceTop(NONEMPTY_OBJECT);
        break;

      default:
        throw new IllegalStateException("Nesting problem.");
    }
  }

  @Override public String getPath() {
    return JsonScope.getPath(stackSize, stack, pathNames, pathIndices);
  }

  @Override void promoteNameToValue() {
    int context = peek();
    if (context != NONEMPTY_OBJECT && context != EMPTY_OBJECT) {
      throw new IllegalStateException("Nesting problem.");
    }
    promoteNameToValue = true;
  }

  @Override public void close() throws IOException {
    int size = stackSize;
    if (size > 1 || size == 1 && stack[size - 1] != NONEMPTY_DOCUMENT) {
      throw new IOException("Incomplete document");
    }
    stackSize = 0;
  }

  @Override public void flush() {
    if (stackSize == 0) {
      throw new IllegalStateException("JsonWriter is closed.");
    }
  }
}
