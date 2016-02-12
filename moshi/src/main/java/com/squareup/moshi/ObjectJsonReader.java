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

import java.util.Iterator;
import java.util.Map;

final class ObjectJsonReader extends JsonReader {
  private static final int PEEKED_NONE = 0;
  private static final int PEEKED_MAP = 1;
  private static final int PEEKED_MAP_ENTRY = 2;
  private static final int PEEKED_MAP_ITERATOR_EXHAUSTED = 3;
  private static final int PEEKED_LIST = 4;
  private static final int PEEKED_LIST_ITERATOR_EXHAUSTED = 5;
  private static final int PEEKED_BOOLEAN = 6;
  private static final int PEEKED_NULL = 7;
  private static final int PEEKED_STRING = 8;
  private static final int PEEKED_INT = 9;
  private static final int PEEKED_LONG = 10;
  private static final int PEEKED_FLOAT = 11;
  private static final int PEEKED_DOUBLE = 12;
  private static final int PEEKED_DONE = 13;

  /** True to accept non-spec compliant JSON */
  private boolean lenient = false;

  /** True to throw a {@link JsonDataException} on any attempt to call {@link #skipValue()}. */
  private boolean failOnUnknown = false;

  private int peeked = PEEKED_NONE;

  /*
   * The nesting stack. Using a manual array rather than an ArrayList saves 20%.
   */
  private int[] stack = new int[32];
  private int stackSize = 0;
  {
    stack[stackSize++] = JsonScope.EMPTY_DOCUMENT;
  }

  private String[] pathNames = new String[32];
  private int[] pathIndices = new int[32];

  private final Object source;
  private Object[] objects = new Object[32];
  private int objectsSize = 1;

  ObjectJsonReader(Object source) {
    this.source = source;
    this.objects[0] = source;
  }

  @Override public void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  @Override public boolean isLenient() {
    return lenient;
  }

  @Override public void setFailOnUnknown(boolean failOnUnknown) {
    this.failOnUnknown = failOnUnknown;
  }

  @Override public boolean failOnUnknown() {
    return failOnUnknown;
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

  @Override public void beginArray() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_LIST) {
      Iterable<?> iterable = (Iterable<?>) popObject();
      pushObject(iterable.iterator());

      push(JsonScope.EMPTY_ARRAY);
      pathIndices[stackSize - 1] = 0;
      peeked = PEEKED_NONE;
    } else {
      throw new JsonDataException("Expected BEGIN_ARRAY but was " + peek()
          + " at path " + getPath());
    }
  }

  @Override public void endArray() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_LIST_ITERATOR_EXHAUSTED) {
      popObject();
      stackSize--;
      pathIndices[stackSize - 1]++;
      peeked = PEEKED_NONE;
    } else {
      throw new JsonDataException("Expected END_ARRAY but was " + peek()
          + " at path " + getPath());
    }
  }

  @Override public void beginObject() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_MAP) {
      Map<?, ?> map = (Map<?, ?>) popObject();
      pushObject(map.entrySet().iterator());

      push(JsonScope.EMPTY_OBJECT);
      peeked = PEEKED_NONE;
    } else {
      throw new JsonDataException("Expected BEGIN_OBJECT but was " + peek()
          + " at path " + getPath());
    }
  }

  @Override public void endObject() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_MAP_ITERATOR_EXHAUSTED) {
      popObject();
      stackSize--;
      pathNames[stackSize] = null; // Free the last path name so that it can be garbage collected!
      pathIndices[stackSize - 1]++;
      peeked = PEEKED_NONE;
    } else {
      throw new JsonDataException("Expected END_OBJECT but was " + peek()
          + " at path " + getPath());
    }
  }

  @Override public boolean hasNext() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    return p != PEEKED_LIST_ITERATOR_EXHAUSTED && p != PEEKED_MAP_ITERATOR_EXHAUSTED;
  }

  @Override public Token peek() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    switch (p) {
      case PEEKED_MAP:
        return Token.BEGIN_OBJECT;
      case PEEKED_MAP_ITERATOR_EXHAUSTED:
        return Token.END_OBJECT;
      case PEEKED_LIST:
        return Token.BEGIN_ARRAY;
      case PEEKED_LIST_ITERATOR_EXHAUSTED:
        return Token.END_ARRAY;
      case PEEKED_MAP_ENTRY:
        return Token.NAME;
      case PEEKED_BOOLEAN:
        return Token.BOOLEAN;
      case PEEKED_NULL:
        return Token.NULL;
      case PEEKED_STRING:
        return Token.STRING;
      case PEEKED_INT:
      case PEEKED_LONG:
      case PEEKED_FLOAT:
      case PEEKED_DOUBLE:
        return Token.NUMBER;
      case PEEKED_DONE:
        return Token.END_DOCUMENT;
      default:
        throw new AssertionError();
    }
  }

  private int doPeek() {
    int peekStack = stack[stackSize - 1];
    if (peekStack == JsonScope.EMPTY_ARRAY || peekStack == JsonScope.NONEMPTY_ARRAY) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_ARRAY;

      Iterator<?> iterator = (Iterator<?>) objects[objectsSize - 1];
      if (!iterator.hasNext()) {
        return peeked = PEEKED_LIST_ITERATOR_EXHAUSTED;
      }

      pushObject(iterator.next());
    } else if (peekStack == JsonScope.EMPTY_OBJECT || peekStack == JsonScope.NONEMPTY_OBJECT) {
      stack[stackSize - 1] = JsonScope.DANGLING_NAME;

      Iterator<Map.Entry<?, ?>> iterator = (Iterator<Map.Entry<?, ?>>) objects[objectsSize - 1];
      if (!iterator.hasNext()) {
        return peeked = PEEKED_MAP_ITERATOR_EXHAUSTED;
      }
      pushObject(iterator.next());
      return peeked = PEEKED_MAP_ENTRY;
    } else if (peekStack == JsonScope.DANGLING_NAME) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_OBJECT;
    } else if (peekStack == JsonScope.EMPTY_DOCUMENT) {
      stack[stackSize - 1] = JsonScope.NONEMPTY_DOCUMENT;
    } else if (peekStack == JsonScope.CLOSED) {
      throw new IllegalStateException("JsonReader is closed");
    }

    if (objectsSize == 0) {
      return peeked = PEEKED_DONE;
    }

    Object object = objects[objectsSize - 1];
    if (object == null) {
      return peeked = PEEKED_NULL;
    }
    if (object instanceof Boolean) {
      return peeked = PEEKED_BOOLEAN;
    }
    if (object instanceof String) {
      return peeked = PEEKED_STRING;
    }
    if (object instanceof Integer) {
      return peeked = PEEKED_INT;
    }
    if (object instanceof Long) {
      return peeked = PEEKED_LONG;
    }
    if (object instanceof Float) {
      return peeked = PEEKED_FLOAT;
    }
    if (object instanceof Double) {
      return peeked = PEEKED_DOUBLE;
    }
    if (object instanceof Iterable) {
      return peeked = PEEKED_LIST;
    }
    if (object instanceof Map) {
      return peeked = PEEKED_MAP;
    }
    throw syntaxError("Unrecognized type " + object.getClass().getName() + ": " + object);
  }

  @Override public String nextName() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_MAP_ENTRY) {
      Map.Entry<?, ?> object = (Map.Entry<?, ?>) popObject();
      result = (String) object.getKey();
      pushObject(object.getValue());
    } else {
      throw new JsonDataException("Expected a name but was " + peek() + " at path " + getPath());
    }
    peeked = PEEKED_NONE;
    pathNames[stackSize - 1] = result;
    return result;
  }

  @Override public String nextString() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    String result;
    if (p == PEEKED_STRING
        || p == PEEKED_INT
        || p == PEEKED_LONG
        || p == PEEKED_FLOAT
        || p == PEEKED_DOUBLE) {
      result = popObject().toString();
    } else {
      throw new JsonDataException("Expected a string but was " + peek() + " at path " + getPath());
    }
    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  @Override public boolean nextBoolean() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    boolean result;
    if (p == PEEKED_BOOLEAN) {
      result = (boolean) popObject();
    } else {
      throw new JsonDataException("Expected a boolean but was " + peek() + " at path " + getPath());
    }
    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  @Override public <T> T nextNull() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    if (p == PEEKED_NULL) {
      popObject();
    } else {
      throw new JsonDataException("Expected null but was " + peek() + " at path " + getPath());
    }
    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return null;
  }

  @Override public double nextDouble() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }
    Object object = popObject();

    double result;
    if (p == PEEKED_INT) {
      result = (int) object;
    } else if (p == PEEKED_LONG) {
      result = (long) object;
    } else if (p == PEEKED_FLOAT) {
      result = (float) object;
    } else if (p == PEEKED_DOUBLE) {
      result = (double) object;
    } else if (p == PEEKED_STRING) {
      String string = object.toString();
      try {
        result = Double.parseDouble(string);
      } catch (NumberFormatException e) {
        throw new JsonDataException(
            "Expected a double but was " + string + " at path " + getPath());
      }
    } else {
      throw new JsonDataException("Expected a double but was " + peek() + " at path " + getPath());
    }

    if (!lenient && (Double.isNaN(result) || Double.isInfinite(result))) {
      throw new IllegalStateException(
          "JSON forbids NaN and infinities: " + result + " at path " + getPath());
    }

    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  @Override public long nextLong() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    Object object = popObject();

    long result;
    if (p == PEEKED_INT) {
      result = (int) object;
    } else if (p == PEEKED_LONG) {
      result = (long) object;
    } else if (p == PEEKED_FLOAT) {
      result = ((Float) object).longValue();
    } else if (p == PEEKED_DOUBLE) {
      result = ((Double) object).longValue();
    } else if (p == PEEKED_STRING) {
      String string = object.toString();
      try {
        result = Long.parseLong(string);
      } catch (NumberFormatException ignored) {
        double asDouble;
        try {
          asDouble = Double.parseDouble(string);
        } catch (NumberFormatException e) {
          throw new JsonDataException(
              "Expected a long but was " + string + " at path " + getPath());
        }
        result = (long) asDouble;
        if (result != asDouble) { // Make sure no precision was lost casting to 'long'.
          throw new JsonDataException(
              "Expected a long but was " + string + " at path " + getPath());
        }
      }
    } else {
      throw new JsonDataException("Expected a long but was " + peek() + " at path " + getPath());
    }

    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  @Override public int nextInt() {
    int p = peeked;
    if (p == PEEKED_NONE) {
      p = doPeek();
    }

    Object object = popObject();

    int result;
    if (p == PEEKED_INT) {
      result = (int) object;
    } else if (p == PEEKED_LONG) {
      long asLong = (long) object;
      result = (int) asLong;
      if (result != asLong) { // Make sure no precision was lost casting to 'int'.
        throw new JsonDataException("Expected an int but was " + object + " at path " + getPath());
      }
    } else if (p == PEEKED_FLOAT) {
      float asFloat = (float) object;
      result = (int) asFloat;
      if (result != asFloat) { // Make sure no precision was lost casting to 'int'.
        throw new JsonDataException("Expected an int but was " + object + " at path " + getPath());
      }
    } else if (p == PEEKED_DOUBLE) {
      double asDouble = (double) object;
      result = (int) asDouble;
      if (result != asDouble) { // Make sure no precision was lost casting to 'int'.
        throw new JsonDataException("Expected an int but was " + object + " at path " + getPath());
      }
    } else if (p == PEEKED_STRING) {
      String string = object.toString();
      try {
        result = Integer.parseInt(string);
      } catch (NumberFormatException ignored) {
        double asDouble;
        try {
          asDouble = Double.parseDouble(string);
        } catch (NumberFormatException e) {
          throw new JsonDataException(
              "Expected an int but was " + string + " at path " + getPath());
        }
        result = (int) asDouble;
        if (result != asDouble) { // Make sure no precision was lost casting to 'int'.
          throw new JsonDataException(
              "Expected an int but was " + string + " at path " + getPath());
        }
      }
    } else {
      throw new JsonDataException("Expected an int but was " + peek() + " at path " + getPath());
    }

    peeked = PEEKED_NONE;
    pathIndices[stackSize - 1]++;
    return result;
  }

  @Override public void close() {
    peeked = PEEKED_NONE;
    stack[0] = JsonScope.CLOSED;
    stackSize = 1;
    objects = null;
    objectsSize = -1;
  }

  @Override public void skipValue() {
    if (failOnUnknown) {
      throw new JsonDataException("Cannot skip unexpected " + peek() + " at " + getPath());
    }
    int count = 0;
    do {
      int p = peeked;
      if (p == PEEKED_NONE) {
        p = doPeek();
      }

      if (p == PEEKED_LIST_ITERATOR_EXHAUSTED) {
        stackSize--;
        count--;
      } else if (p == PEEKED_MAP_ITERATOR_EXHAUSTED) {
        stackSize--;
        count--;
      } else {
        popObject();
      }
      peeked = PEEKED_NONE;
    } while (count != 0);

    pathIndices[stackSize - 1]++;
    pathNames[stackSize - 1] = "null";
  }

  private void push(int newTop) {
    if (stackSize == stack.length) {
      int[] newStack = new int[stackSize * 2];
      int[] newPathIndices = new int[stackSize * 2];
      String[] newPathNames = new String[stackSize * 2];
      System.arraycopy(stack, 0, newStack, 0, stackSize);
      System.arraycopy(pathIndices, 0, newPathIndices, 0, stackSize);
      System.arraycopy(pathNames, 0, newPathNames, 0, stackSize);
      stack = newStack;
      pathIndices = newPathIndices;
      pathNames = newPathNames;
    }
    stack[stackSize++] = newTop;
  }

  @Override public String toString() {
    return "JsonReader(" + source + ")";
  }

  @Override public String getPath() {
    return JsonScope.getPath(stackSize, stack, pathNames, pathIndices);
  }

  /**
   * Throws a new IO exception with the given message and a context snippet
   * with this reader's content.
   */
  private IllegalStateException syntaxError(String message) {
    return new IllegalStateException(message + " at path " + getPath());
  }

  @Override void promoteNameToValue() {
    if (hasNext()) {
      pushObject(nextName());
      peeked = PEEKED_STRING;
    }
  }
}
