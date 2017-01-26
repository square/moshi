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
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * This class reads a JSON document by traversing a Java object comprising maps, lists, and JSON
 * primitives. It does depth-first traversal keeping a stack starting with the root object. During
 * traversal a stack tracks the current position in the document:
 *
 * <ul>
 *   <li>The next element to act upon is on the top of the stack.
 *   <li>When the top of the stack is a {@link List}, calling {@link #beginArray()} replaces the
 *       list with a {@link ListIterator}. The first element of the iterator is pushed on top of the
 *       iterator.
 *   <li>Similarly, when the top of the stack is a {@link Map}, calling {@link #beginObject()}
 *       replaces the map with an {@link Iterator} of its entries. The first element of the iterator
 *       is pushed on top of the iterator.
 *   <li>When the top of the stack is a {@link Map.Entry}, calling {@link #nextName()} returns the
 *       entry's key and replaces the entry with its value on the stack.
 *   <li>When an element is consumed it is popped. If the new top of the stack has a non-exhausted
 *       iterator, the next element of that iterator is pushed.
 *   <li>If the top of the stack is an exhausted iterator, calling {@link #endArray} or {@link
 *       #endObject} will pop it.
 * </ul>
 */
final class ObjectJsonReader extends JsonReader {
  /** Sentinel object pushed on {@link #stack} when the reader is closed. */
  private static final Object JSON_READER_CLOSED = new Object();

  private final Object[] stack = new Object[32];

  public ObjectJsonReader(Object root) {
    scopes[stackSize] = JsonScope.NONEMPTY_DOCUMENT;
    stack[stackSize++] = root;
  }

  @Override public void beginArray() throws IOException {
    List<?> peeked = require(List.class, Token.BEGIN_ARRAY);

    ListIterator<?> iterator = peeked.listIterator();
    stack[stackSize - 1] = iterator;
    scopes[stackSize - 1] = JsonScope.EMPTY_ARRAY;
    pathIndices[stackSize - 1] = 0;

    // If the iterator isn't empty push its first value onto the stack.
    if (iterator.hasNext()) {
      push(iterator.next());
    }
  }

  @Override public void endArray() throws IOException {
    ListIterator<?> peeked = require(ListIterator.class, Token.END_ARRAY);
    if (peeked.hasNext()) {
      throw typeMismatch(peeked, Token.END_ARRAY);
    }
    remove();
  }

  @Override public void beginObject() throws IOException {
    Map<?, ?> peeked = require(Map.class, Token.BEGIN_OBJECT);

    Iterator<?> iterator = peeked.entrySet().iterator();
    stack[stackSize - 1] = iterator;
    scopes[stackSize - 1] = JsonScope.EMPTY_OBJECT;

    // If the iterator isn't empty push its first value onto the stack.
    if (iterator.hasNext()) {
      push(iterator.next());
    }
  }

  @Override public void endObject() throws IOException {
    Iterator<?> peeked = require(Iterator.class, Token.END_OBJECT);
    if (peeked instanceof ListIterator || peeked.hasNext()) {
      throw typeMismatch(peeked, Token.END_OBJECT);
    }
    pathNames[stackSize - 1] = null;
    remove();
  }

  @Override public boolean hasNext() throws IOException {
    // TODO(jwilson): this is consistent with BufferedSourceJsonReader but it doesn't make sense.
    if (stackSize == 0) return true;

    Object peeked = stack[stackSize - 1];
    return !(peeked instanceof Iterator) || ((Iterator) peeked).hasNext();
  }

  @Override public Token peek() throws IOException {
    if (stackSize == 0) return Token.END_DOCUMENT;

    // If the top of the stack is an iterator, take its first element and push it on the stack.
    Object peeked = stack[stackSize - 1];
    if (peeked instanceof ListIterator) return Token.END_ARRAY;
    if (peeked instanceof Iterator) return Token.END_OBJECT;
    if (peeked instanceof List) return Token.BEGIN_ARRAY;
    if (peeked instanceof Map) return Token.BEGIN_OBJECT;
    if (peeked instanceof Map.Entry) return Token.NAME;
    if (peeked instanceof String) return Token.STRING;
    if (peeked instanceof Boolean) return Token.BOOLEAN;
    if (peeked instanceof Number) return Token.NUMBER;
    if (peeked == null) return Token.NULL;
    if (peeked == JSON_READER_CLOSED) throw new IllegalStateException("JsonReader is closed");

    throw typeMismatch(peeked, "a JSON value");
  }

  @Override public String nextName() throws IOException {
    Map.Entry<?, ?> peeked = require(Map.Entry.class, Token.NAME);

    // Swap the Map.Entry for its value on the stack and return its key.
    String result = stringKey(peeked);
    stack[stackSize - 1] = peeked.getValue();
    pathNames[stackSize - 2] = result;
    return result;
  }

  @Override public int selectName(Options options) throws IOException {
    Map.Entry<?, ?> peeked = require(Map.Entry.class, Token.NAME);
    String name = stringKey(peeked);
    for (int i = 0, length = options.strings.length; i < length; i++) {
      // Swap the Map.Entry for its value on the stack and return its key.
      if (options.strings[i].equals(name)) {
        stack[stackSize - 1] = peeked.getValue();
        pathNames[stackSize - 2] = name;
        return i;
      }
    }
    return -1;
  }

  @Override public String nextString() throws IOException {
    String peeked = require(String.class, Token.STRING);
    remove();
    return peeked;
  }

  @Override public int selectString(Options options) throws IOException {
    String peeked = require(String.class, Token.STRING);
    for (int i = 0, length = options.strings.length; i < length; i++) {
      if (options.strings[i].equals(peeked)) {
        remove();
        return i;
      }
    }
    return -1;
  }

  @Override public boolean nextBoolean() throws IOException {
    Boolean peeked = require(Boolean.class, Token.BOOLEAN);
    remove();
    return peeked;
  }

  @Override public <T> T nextNull() throws IOException {
    require(Void.class, Token.NULL);
    remove();
    return null;
  }

  @Override public double nextDouble() throws IOException {
    Object peeked = require(Object.class, Token.NUMBER);

    double result;
    if (peeked instanceof Number) {
      result = ((Number) peeked).doubleValue();
    } else if (peeked instanceof String) {
      try {
        result = Double.parseDouble((String) peeked);
      } catch (NumberFormatException e) {
        throw typeMismatch(peeked, Token.NUMBER);
      }
    } else {
      throw typeMismatch(peeked, Token.NUMBER);
    }
    if (!lenient && (Double.isNaN(result) || Double.isInfinite(result))) {
      throw new JsonEncodingException("JSON forbids NaN and infinities: " + result
          + " at path " + getPath());
    }
    remove();
    return result;
  }

  @Override public long nextLong() throws IOException {
    Object peeked = require(Object.class, Token.NUMBER);

    long result;
    if (peeked instanceof Number) {
      result = ((Number) peeked).longValue();
    } else if (peeked instanceof String) {
      try {
        result = Long.parseLong((String) peeked);
      } catch (NumberFormatException e) {
        try {
          BigDecimal asDecimal = new BigDecimal((String) peeked);
          result = asDecimal.longValueExact();
        } catch (NumberFormatException e2) {
          throw typeMismatch(peeked, Token.NUMBER);
        }
      }
    } else {
      throw typeMismatch(peeked, Token.NUMBER);
    }
    remove();
    return result;
  }

  @Override public int nextInt() throws IOException {
    Object peeked = require(Object.class, Token.NUMBER);

    int result;
    if (peeked instanceof Number) {
      result = ((Number) peeked).intValue();
    } else if (peeked instanceof String) {
      try {
        result = Integer.parseInt((String) peeked);
      } catch (NumberFormatException e) {
        try {
          BigDecimal asDecimal = new BigDecimal((String) peeked);
          result = asDecimal.intValueExact();
        } catch (NumberFormatException e2) {
          throw typeMismatch(peeked, Token.NUMBER);
        }
      }
    } else {
      throw typeMismatch(peeked, Token.NUMBER);
    }
    remove();
    return result;
  }

  @Override public void skipValue() throws IOException {
    if (failOnUnknown) {
      throw new JsonDataException("Cannot skip unexpected " + peek() + " at " + getPath());
    }

    // If this element is in an object clear out the key.
    if (stackSize > 1) {
      pathNames[stackSize - 2] = "null";
    }

    Object skipped = stackSize != 0 ? stack[stackSize - 1] : null;

    if (skipped instanceof Map.Entry) {
      // We're skipping a name. Promote the map entry's value.
      Map.Entry<?, ?> entry = (Map.Entry<?, ?>) stack[stackSize - 1];
      stack[stackSize - 1] = entry.getValue();
    } else if (stackSize > 0) {
      // We're skipping a value.
      remove();
    }
  }

  @Override void promoteNameToValue() throws IOException {
    Map.Entry<?, ?> peeked = require(Map.Entry.class, Token.NAME);

    push(peeked.getKey());
    stack[stackSize - 2] = peeked.getValue();
  }

  @Override public void close() throws IOException {
    Arrays.fill(stack, 0, stackSize, null);
    stack[0] = JSON_READER_CLOSED;
    scopes[0] = JsonScope.CLOSED;
    stackSize = 1;
  }

  private void push(Object newTop) {
    if (stackSize == stack.length) {
      throw new JsonDataException("Nesting too deep at " + getPath());
    }
    stack[stackSize++] = newTop;
  }

  /**
   * Returns the top of the stack which is required to be a {@code type}. Throws if this reader is
   * closed, or if the type isn't what was expected.
   */
  private <T> T require(Class<T> type, Token expected) throws IOException {
    Object peeked = (stackSize != 0 ? stack[stackSize - 1] : null);

    if (type.isInstance(peeked)) {
      return type.cast(peeked);
    }
    if (peeked == null && expected == Token.NULL) {
      return null;
    }
    if (peeked == JSON_READER_CLOSED) {
      throw new IllegalStateException("JsonReader is closed");
    }
    throw typeMismatch(peeked, expected);
  }

  private String stringKey(Map.Entry<?, ?> entry) {
    Object name = entry.getKey();
    if (name instanceof String) return (String) name;
    throw typeMismatch(name, Token.NAME);
  }

  /**
   * Removes a value and prepares for the next. If we're iterating a map or list this advances the
   * iterator.
   */
  private void remove() {
    stackSize--;
    stack[stackSize] = null;
    scopes[stackSize] = 0;

    // If we're iterating an array or an object push its next element on to the stack.
    if (stackSize > 0) {
      pathIndices[stackSize - 1]++;

      Object parent = stack[stackSize - 1];
      if (parent instanceof Iterator && ((Iterator<?>) parent).hasNext()) {
        push(((Iterator<?>) parent).next());
      }
    }
  }
}
