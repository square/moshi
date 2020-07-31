/*
 * Copyright (C) 2011 Google Inc.
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
package com.squareup.moshi.adapters;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * A JsonAdapter factory for objects that include type information in the JSON. When decoding JSON
 * Moshi uses this type information to determine which class to decode to. When encoding Moshi uses
 * the object’s class to determine what type information to include.
 *
 * <p>Suppose we have an interface, its implementations, and a class that uses them:
 *
 * <pre> {@code
 *
 *   interface HandOfCards {
 *   }
 *
 *   class BlackjackHand implements HandOfCards {
 *     Card hidden_card;
 *     List<Card> visible_cards;
 *   }
 *
 *   class HoldemHand implements HandOfCards {
 *     Set<Card> hidden_cards;
 *   }
 *
 *   class Player {
 *     String name;
 *     HandOfCards hand;
 *   }
 * }</pre>
 *
 * <p>We want to decode the following JSON into the player model above:
 *
 * <pre> {@code
 *
 *   {
 *     "name": "Jesse",
 *     "hand": {
 *       "hand_type": "blackjack",
 *       "hidden_card": "9D",
 *       "visible_cards": ["8H", "4C"]
 *     }
 *   }
 * }</pre>
 *
 * <p>Left unconfigured, Moshi would incorrectly attempt to decode the hand object to the abstract
 * {@code HandOfCards} interface. We configure it to use the appropriate subtype instead:
 *
 * <pre> {@code
 *
 *   Moshi moshi = new Moshi.Builder()
 *       .add(PolymorphicJsonAdapterFactory.of(HandOfCards.class, "hand_type")
 *           .withSubtype(BlackjackHand.class, "blackjack")
 *           .withSubtype(HoldemHand.class, "holdem"))
 *       .build();
 * }</pre>
 *
 * <p>This class imposes strict requirements on its use:
 *
 * <ul>
 *   <li>Base types may be classes or interfaces.
 *   <li>Subtypes must encode as JSON objects.
 *   <li>Type information must be in the encoded object. Each message must have a type label like
 *       {@code hand_type} whose value is a string like {@code blackjack} that identifies which type
 *       to use.
 *   <li>Each type identifier must be unique.
 * </ul>
 *
 * <p>For best performance type information should be the first field in the object. Otherwise
 * Moshi must reprocess the JSON stream once it knows the object's type.
 *
 * <p>If an unknown subtype is encountered when decoding:
 *  <ul>
 *    <li> if {@link #withDefaultValue(Object)} is used, then {@code defaultValue} will be returned
 *    <li> if {@link #withFallbackJsonAdapter(JsonAdapter)} is used, then the
 *        {@code fallbackJsonAdapter.fromJson(reader)} result will be returned
 *    <li> otherwise a {@link JsonDataException} will be thrown
 *  </ul>
 *
 * <p>If an unknown type is encountered when encoding:
 *  <ul>
 *    <li> if {@link #withFallbackJsonAdapter(JsonAdapter)} is used, then the
 *        {@code fallbackJsonAdapter.toJson(writer, value)} result will be returned
 *    <li> otherwise a {@link IllegalArgumentException} will be thrown
 *  </ul>
 *
 * <p>If the same subtype has multiple labels the first one is used when encoding.
 */
public final class PolymorphicJsonAdapterFactory<T> implements JsonAdapter.Factory {
  /**
   * Thin wrapper around {@link JsonAdapter} to allow {@link PolymorphicJsonAdapter} to
   * distinguish between {@code JsonAdapter} added due to a {@code defaultValue} or added
   * by users of {@link PolymorphicJsonAdapterFactory}.
   */
  private abstract static class DefaultJsonAdapter<T> extends JsonAdapter<T> {
  }

  final Class<T> baseType;
  final String labelKey;
  final List<String> labels;
  final List<Type> subtypes;
  @Nullable final JsonAdapter<Object> fallbackJsonAdapter;

  PolymorphicJsonAdapterFactory(
      Class<T> baseType,
      String labelKey,
      List<String> labels,
      List<Type> subtypes,
      @Nullable JsonAdapter<Object> fallbackJsonAdapter) {
    this.baseType = baseType;
    this.labelKey = labelKey;
    this.labels = labels;
    this.subtypes = subtypes;
    this.fallbackJsonAdapter = fallbackJsonAdapter;
  }

  /**
   * @param baseType The base type for which this factory will create adapters. Cannot be Object.
   * @param labelKey The key in the JSON object whose value determines the type to which to map the
   *     JSON object.
   */
  @CheckReturnValue
  public static <T> PolymorphicJsonAdapterFactory<T> of(Class<T> baseType, String labelKey) {
    if (baseType == null) throw new NullPointerException("baseType == null");
    if (labelKey == null) throw new NullPointerException("labelKey == null");
    return new PolymorphicJsonAdapterFactory<>(
        baseType,
        labelKey,
        Collections.<String>emptyList(),
        Collections.<Type>emptyList(),
        null);
  }

  /**
   * Returns a new factory that decodes instances of {@code subtype}.
   */
  public PolymorphicJsonAdapterFactory<T> withSubtype(Class<? extends T> subtype, String label) {
    if (subtype == null) throw new NullPointerException("subtype == null");
    if (label == null) throw new NullPointerException("label == null");
    if (labels.contains(label)) {
      throw new IllegalArgumentException("Labels must be unique.");
    }
    List<String> newLabels = new ArrayList<>(labels);
    newLabels.add(label);
    List<Type> newSubtypes = new ArrayList<>(subtypes);
    newSubtypes.add(subtype);
    return new PolymorphicJsonAdapterFactory<>(baseType,
        labelKey,
        newLabels,
        newSubtypes,
        fallbackJsonAdapter);
  }

  /**
   * Returns a new factory that with default to {@code fallbackJsonAdapter.fromJson(reader)}
   * upon decoding of unrecognized labels.
   *
   * <p>The {@link JsonReader} instance will not be automatically consumed, so sure to consume it
   * within your implementation of {@link JsonAdapter#fromJson(JsonReader)}
   */
  public PolymorphicJsonAdapterFactory<T> withFallbackJsonAdapter(
      @Nullable JsonAdapter<Object> fallbackJsonAdapter) {
    return new PolymorphicJsonAdapterFactory<>(baseType,
        labelKey,
        labels,
        subtypes,
        fallbackJsonAdapter);
  }

  /**
   * Returns a new factory that will default to {@code defaultValue} upon decoding of unrecognized
   * labels. The default value should be immutable.
   */
  public PolymorphicJsonAdapterFactory<T> withDefaultValue(@Nullable T defaultValue) {
    return withFallbackJsonAdapter(buildFallbackJsonAdapter(defaultValue));
  }

  private JsonAdapter<Object> buildFallbackJsonAdapter(final T defaultValue) {
    return new DefaultJsonAdapter<Object>() {
      @Nullable
      @Override
      public Object fromJson(JsonReader reader) throws IOException {
        reader.skipValue();
        return defaultValue;
      }

      @Override
      public void toJson(JsonWriter writer, @Nullable Object value) throws IOException {
        throw new IOException("This method should never be called. "
            + "If you find this on your stacktraces please report it "
            + "to the https://github.com/square/moshi project");
      }
    };
  }

  @Override
  public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
    if (Types.getRawType(type) != baseType || !annotations.isEmpty()) {
      return null;
    }

    List<JsonAdapter<Object>> jsonAdapters = new ArrayList<>(subtypes.size());
    for (int i = 0, size = subtypes.size(); i < size; i++) {
      jsonAdapters.add(moshi.adapter(subtypes.get(i)));
    }

    return new PolymorphicJsonAdapter(labelKey,
        labels,
        subtypes,
        jsonAdapters,
        fallbackJsonAdapter
    ).nullSafe();
  }

  static final class PolymorphicJsonAdapter extends JsonAdapter<Object> {
    final String labelKey;
    final List<String> labels;
    final List<Type> subtypes;
    final List<JsonAdapter<Object>> jsonAdapters;
    @Nullable final JsonAdapter<Object> fallbackJsonAdapter;

    /** Single-element options containing the label's key only. */
    final JsonReader.Options labelKeyOptions;
    /** Corresponds to subtypes. */
    final JsonReader.Options labelOptions;

    PolymorphicJsonAdapter(String labelKey,
        List<String> labels,
        List<Type> subtypes,
        List<JsonAdapter<Object>> jsonAdapters,
        @Nullable JsonAdapter<Object> fallbackJsonAdapter) {
      this.labelKey = labelKey;
      this.labels = labels;
      this.subtypes = subtypes;
      this.jsonAdapters = jsonAdapters;
      this.fallbackJsonAdapter = fallbackJsonAdapter;

      this.labelKeyOptions = JsonReader.Options.of(labelKey);
      this.labelOptions = JsonReader.Options.of(labels.toArray(new String[0]));
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      JsonReader peeked = reader.peekJson();
      peeked.setFailOnUnknown(false);
      int labelIndex;
      try {
        labelIndex = labelIndex(peeked);
      } finally {
        peeked.close();
      }
      if (labelIndex == -1) {
        return this.fallbackJsonAdapter.fromJson(reader);
      } else {
        return jsonAdapters.get(labelIndex).fromJson(reader);
      }
    }

    private int labelIndex(JsonReader reader) throws IOException {
      reader.beginObject();
      while (reader.hasNext()) {
        if (reader.selectName(labelKeyOptions) == -1) {
          reader.skipName();
          reader.skipValue();
          continue;
        }

        int labelIndex = reader.selectString(labelOptions);
        if (labelIndex == -1 && this.fallbackJsonAdapter == null) {
          throw new JsonDataException("Expected one of "
              + labels
              + " for key '"
              + labelKey
              + "' but found '"
              + reader.nextString()
              + "'. Register a subtype for this label.");
        }
        return labelIndex;
      }

      throw new JsonDataException("Missing label for " + labelKey);
    }

    @Override public void toJson(JsonWriter writer, Object value) throws IOException {
      Class<?> type = value.getClass();
      int labelIndex = subtypes.indexOf(type);
      final JsonAdapter<Object> adapter;
      if (labelIndex == -1) {
        if (fallbackJsonAdapter == null || fallbackJsonAdapter instanceof DefaultJsonAdapter) {
          throw new IllegalArgumentException("Expected one of "
              + subtypes
              + " but found "
              + value
              + ", a "
              + value.getClass()
              + ". Register this subtype.");
        } else {
          adapter = fallbackJsonAdapter;
        }
      } else {
        adapter = jsonAdapters.get(labelIndex);
      }

      writer.beginObject();
      if (adapter != fallbackJsonAdapter) {
        writer.name(labelKey).value(labels.get(labelIndex));
      }
      int flattenToken = writer.beginFlatten();
      adapter.toJson(writer, value);
      writer.endFlatten(flattenToken);
      writer.endObject();
    }

    @Override public String toString() {
      return "PolymorphicJsonAdapter(" + labelKey + ")";
    }
  }
}
