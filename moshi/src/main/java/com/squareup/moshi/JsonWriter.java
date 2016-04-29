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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import okio.BufferedSink;

/**
 * Writes a JSON (<a href="http://www.ietf.org/rfc/rfc7159.txt">RFC 7159</a>)
 * encoded value to a stream, one token at a time. The stream includes both
 * literal values (strings, numbers, booleans and nulls) as well as the begin
 * and end delimiters of objects and arrays.
 *
 * <h3>Encoding JSON</h3>
 * To encode your data as JSON, create a new {@code JsonWriter}. Each JSON
 * document must contain one top-level array or object. Call methods on the
 * writer as you walk the structure's contents, nesting arrays and objects as
 * necessary:
 * <ul>
 *   <li>To write <strong>arrays</strong>, first call {@link #beginArray()}.
 *       Write each of the array's elements with the appropriate {@link #value}
 *       methods or by nesting other arrays and objects. Finally close the array
 *       using {@link #endArray()}.
 *   <li>To write <strong>objects</strong>, first call {@link #beginObject()}.
 *       Write each of the object's properties by alternating calls to
 *       {@link #name} with the property's value. Write property values with the
 *       appropriate {@link #value} method or by nesting other objects or arrays.
 *       Finally close the object using {@link #endObject()}.
 * </ul>
 *
 * <h3>Example</h3>
 * Suppose we'd like to encode a stream of messages such as the following: <pre> {@code
 * [
 *   {
 *     "id": 912345678901,
 *     "text": "How do I stream JSON in Java?",
 *     "geo": null,
 *     "user": {
 *       "name": "json_newb",
 *       "followers_count": 41
 *      }
 *   },
 *   {
 *     "id": 912345678902,
 *     "text": "@json_newb just use JsonWriter!",
 *     "geo": [50.454722, -104.606667],
 *     "user": {
 *       "name": "jesse",
 *       "followers_count": 2
 *     }
 *   }
 * ]}</pre>
 * This code encodes the above structure: <pre>   {@code
 *   public void writeJsonStream(BufferedSink sink, List<Message> messages) throws IOException {
 *     JsonWriter writer = JsonWriter.of(sink);
 *     writer.setIndentSpaces(4);
 *     writeMessagesArray(writer, messages);
 *     writer.close();
 *   }
 *
 *   public void writeMessagesArray(JsonWriter writer, List<Message> messages) throws IOException {
 *     writer.beginArray();
 *     for (Message message : messages) {
 *       writeMessage(writer, message);
 *     }
 *     writer.endArray();
 *   }
 *
 *   public void writeMessage(JsonWriter writer, Message message) throws IOException {
 *     writer.beginObject();
 *     writer.name("id").value(message.getId());
 *     writer.name("text").value(message.getText());
 *     if (message.getGeo() != null) {
 *       writer.name("geo");
 *       writeDoublesArray(writer, message.getGeo());
 *     } else {
 *       writer.name("geo").nullValue();
 *     }
 *     writer.name("user");
 *     writeUser(writer, message.getUser());
 *     writer.endObject();
 *   }
 *
 *   public void writeUser(JsonWriter writer, User user) throws IOException {
 *     writer.beginObject();
 *     writer.name("name").value(user.getName());
 *     writer.name("followers_count").value(user.getFollowersCount());
 *     writer.endObject();
 *   }
 *
 *   public void writeDoublesArray(JsonWriter writer, List<Double> doubles) throws IOException {
 *     writer.beginArray();
 *     for (Double value : doubles) {
 *       writer.value(value);
 *     }
 *     writer.endArray();
 *   }}</pre>
 *
 * <p>Each {@code JsonWriter} may be used to write a single JSON stream.
 * Instances of this class are not thread safe. Calls that would result in a
 * malformed JSON string will fail with an {@link IllegalStateException}.
 */
public abstract class JsonWriter implements Closeable, Flushable {
  /**
   * Returns a new instance that writes a JSON-encoded stream to {@code sink}.
   */
  public static JsonWriter of(BufferedSink sink) {
    return new BufferedSinkJsonWriter(sink);
  }

  JsonWriter() {
    // Package-private to control subclasses.
  }

  /**
   * Sets the indentation string to be repeated for each level of indentation
   * in the encoded document. If {@code indent.isEmpty()} the encoded document
   * will be compact. Otherwise the encoded document will be more
   * human-readable.
   *
   * @param indent a string containing only whitespace.
   */
  public abstract void setIndent(String indent);

  /**
   * Configure this writer to relax its syntax rules. By default, this writer
   * only emits well-formed JSON as specified by <a
   * href="http://www.ietf.org/rfc/rfc7159.txt">RFC 7159</a>. Setting the writer
   * to lenient permits the following:
   * <ul>
   *   <li>Top-level values of any type. With strict writing, the top-level
   *       value must be an object or an array.
   *   <li>Numbers may be {@linkplain Double#isNaN() NaNs} or {@linkplain
   *       Double#isInfinite() infinities}.
   * </ul>
   */
  public abstract void setLenient(boolean lenient);

  /**
   * Returns true if this writer has relaxed syntax rules.
   */
  public abstract boolean isLenient();

  /**
   * Sets whether object members are serialized when their value is null.
   * This has no impact on array elements. The default is false.
   */
  public abstract void setSerializeNulls(boolean serializeNulls);

  /**
   * Returns true if object members are serialized when their value is null.
   * This has no impact on array elements. The default is false.
   */
  public abstract boolean getSerializeNulls();

  /**
   * Begins encoding a new array. Each call to this method must be paired with
   * a call to {@link #endArray}.
   *
   * @return this writer.
   */
  public abstract JsonWriter beginArray() throws IOException;

  /**
   * Ends encoding the current array.
   *
   * @return this writer.
   */
  public abstract JsonWriter endArray() throws IOException;

  /**
   * Begins encoding a new object. Each call to this method must be paired
   * with a call to {@link #endObject}.
   *
   * @return this writer.
   */
  public abstract JsonWriter beginObject() throws IOException;

  /**
   * Ends encoding the current object.
   *
   * @return this writer.
   */
  public abstract JsonWriter endObject() throws IOException;

  /**
   * Encodes the property name.
   *
   * @param name the name of the forthcoming value. May not be null.
   * @return this writer.
   */
  public abstract JsonWriter name(String name) throws IOException;

  /**
   * Encodes {@code value}.
   *
   * @param value the literal string value, or null to encode a null literal.
   * @return this writer.
   */
  public abstract JsonWriter value(String value) throws IOException;

  /**
   * Encodes {@code null}.
   *
   * @return this writer.
   */
  public abstract JsonWriter nullValue() throws IOException;

  /**
   * Encodes {@code value}.
   *
   * @return this writer.
   */
  public abstract JsonWriter value(boolean value) throws IOException;

  /**
   * Encodes {@code value}.
   *
   * @return this writer.
   */
  public abstract JsonWriter value(Boolean value) throws IOException;

  /**
   * Encodes {@code value}.
   *
   * @param value a finite value. May not be {@linkplain Double#isNaN() NaNs} or
   *     {@linkplain Double#isInfinite() infinities}.
   * @return this writer.
   */
  public abstract JsonWriter value(double value) throws IOException;

  /**
   * Encodes {@code value}.
   *
   * @return this writer.
   */
  public abstract JsonWriter value(long value) throws IOException;

  /**
   * Encodes {@code value}.
   *
   * @param value a finite value. May not be {@linkplain Double#isNaN() NaNs} or
   *     {@linkplain Double#isInfinite() infinities}.
   * @return this writer.
   */
  public abstract JsonWriter value(Number value) throws IOException;

  /**
   * Changes the reader to treat the next string value as a name. This is useful for map adapters so
   * that arbitrary type adapters can use {@link #value(String)} to write a name value.
   */
  abstract void promoteNameToValue() throws IOException;

  /**
   * Returns a <a href="http://goessner.net/articles/JsonPath/">JsonPath</a> to
   * the current location in the JSON value.
   */
  public abstract String getPath();
}
