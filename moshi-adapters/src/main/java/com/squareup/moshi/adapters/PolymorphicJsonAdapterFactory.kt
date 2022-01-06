/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonAdapter.Factory
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Options
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.rawType
import java.io.IOException
import java.lang.reflect.Type
import javax.annotation.CheckReturnValue

/**
 * A JsonAdapter factory for objects that include type information in the JSON. When decoding JSON
 * Moshi uses this type information to determine which class to decode to. When encoding Moshi uses
 * the object’s class to determine what type information to include.
 *
 * Suppose we have an interface, its implementations, and a class that uses them:
 *
 * ```
 * interface HandOfCards { }
 *
 * class BlackjackHand implements HandOfCards {
 *   Card hidden_card;
 *   List<Card> visible_cards;
 * }
 *
 * class HoldemHand implements HandOfCards {
 *   Set<Card> hidden_cards;
 * }
 *
 * class Player {
 *   String name;
 *   HandOfCards hand;
 * }
 * ```
 *
 * We want to decode the following JSON into the player model above:
 *
 * ```
 * {
 *   "name": "Jesse",
 *     "hand": {
 *     "hand_type": "blackjack",
 *     "hidden_card": "9D",
 *     "visible_cards": ["8H", "4C"]
 *   }
 * }
 *```
 *
 * Left unconfigured, Moshi would incorrectly attempt to decode the hand object to the abstract
 * `HandOfCards` interface. We configure it to use the appropriate subtype instead:
 *
 * ```
 * Moshi moshi = new Moshi.Builder()
 *   .add(PolymorphicJsonAdapterFactory.of(HandOfCards.class, "hand_type")
 *     .withSubtype(BlackjackHand.class, "blackjack")
 *     .withSubtype(HoldemHand.class, "holdem"))
 *   .build();
 * ```
 *
 * This class imposes strict requirements on its use:
 *  * Base types may be classes or interfaces.
 *  * Subtypes must encode as JSON objects.
 *  * Type information must be in the encoded object. Each message must have a type label like
 * `hand_type` whose value is a string like `blackjack` that identifies which type
 * to use.
 *  * Each type identifier must be unique.
 *
 * For best performance type information should be the first field in the object. Otherwise Moshi
 * must reprocess the JSON stream once it knows the object's type.
 *
 * If an unknown subtype is encountered when decoding:
 *  * If [withDefaultValue] is used, then `defaultValue` will be returned.
 *  * If [withFallbackJsonAdapter] is used, then the `fallbackJsonAdapter.fromJson(reader)` result will be returned.
 *  * Otherwise a [JsonDataException] will be thrown.
 *
 * If an unknown type is encountered when encoding:
 *  * If [withFallbackJsonAdapter] is used, then the `fallbackJsonAdapter.toJson(writer, value)` result will be returned.
 *  * Otherwise a [IllegalArgumentException] will be thrown.
 *
 * If the same subtype has multiple labels the first one is used when encoding.
 */
public class PolymorphicJsonAdapterFactory<T> internal constructor(
  private val baseType: Class<T>,
  private val labelKey: String,
  private val labels: List<String>,
  private val subtypes: List<Type>,
  private val fallbackJsonAdapter: JsonAdapter<Any>?
) : Factory {
  /** Returns a new factory that decodes instances of `subtype`. */
  public fun withSubtype(subtype: Class<out T>, label: String): PolymorphicJsonAdapterFactory<T> {
    require(!labels.contains(label)) { "Labels must be unique." }
    val newLabels = buildList {
      addAll(labels)
      add(label)
    }
    val newSubtypes = buildList {
      addAll(subtypes)
      add(subtype)
    }
    return PolymorphicJsonAdapterFactory(
      baseType = baseType,
      labelKey = labelKey,
      labels = newLabels,
      subtypes = newSubtypes,
      fallbackJsonAdapter = fallbackJsonAdapter
    )
  }

  /**
   * Returns a new factory that with default to `fallbackJsonAdapter.fromJson(reader)` upon
   * decoding of unrecognized labels.
   *
   * The [JsonReader] instance will not be automatically consumed, so make sure to consume
   * it within your implementation of [JsonAdapter.fromJson]
   */
  public fun withFallbackJsonAdapter(
    fallbackJsonAdapter: JsonAdapter<Any>?
  ): PolymorphicJsonAdapterFactory<T> {
    return PolymorphicJsonAdapterFactory(
      baseType = baseType,
      labelKey = labelKey,
      labels = labels,
      subtypes = subtypes,
      fallbackJsonAdapter = fallbackJsonAdapter
    )
  }

  /**
   * Returns a new factory that will default to `defaultValue` upon decoding of unrecognized
   * labels. The default value should be immutable.
   */
  public fun withDefaultValue(defaultValue: T?): PolymorphicJsonAdapterFactory<T> {
    return withFallbackJsonAdapter(buildFallbackJsonAdapter(defaultValue))
  }

  private fun buildFallbackJsonAdapter(defaultValue: T?): JsonAdapter<Any> {
    return object : JsonAdapter<Any>() {
      override fun fromJson(reader: JsonReader): Any? {
        reader.skipValue()
        return defaultValue
      }

      override fun toJson(writer: JsonWriter, value: Any?) {
        throw IllegalArgumentException(
          "Expected one of $subtypes but found $value, a ${value?.javaClass}. Register this subtype."
        )
      }
    }
  }

  override fun create(type: Type, annotations: Set<Annotation?>, moshi: Moshi): JsonAdapter<*>? {
    if (type.rawType != baseType || annotations.isNotEmpty()) {
      return null
    }
    val jsonAdapters: List<JsonAdapter<Any>> = subtypes.map(moshi::adapter)
    return PolymorphicJsonAdapter(labelKey, labels, subtypes, jsonAdapters, fallbackJsonAdapter)
      .nullSafe()
  }

  internal class PolymorphicJsonAdapter(
    private val labelKey: String,
    private val labels: List<String>,
    private val subtypes: List<Type>,
    private val jsonAdapters: List<JsonAdapter<Any>>,
    private val fallbackJsonAdapter: JsonAdapter<Any>?
  ) : JsonAdapter<Any>() {
    /** Single-element options containing the label's key only.  */
    private val labelKeyOptions: Options = Options.of(labelKey)

    /** Corresponds to subtypes.  */
    private val labelOptions: Options = Options.of(*labels.toTypedArray())

    override fun fromJson(reader: JsonReader): Any? {
      val peeked = reader.peekJson()
      peeked.setFailOnUnknown(false)
      val labelIndex = peeked.use(::labelIndex)
      return if (labelIndex == -1) {
        fallbackJsonAdapter?.fromJson(reader)
      } else {
        jsonAdapters[labelIndex].fromJson(reader)
      }
    }

    private fun labelIndex(reader: JsonReader): Int {
      reader.beginObject()
      while (reader.hasNext()) {
        if (reader.selectName(labelKeyOptions) == -1) {
          reader.skipName()
          reader.skipValue()
          continue
        }
        val labelIndex = reader.selectString(labelOptions)
        if (labelIndex == -1 && fallbackJsonAdapter == null) {
          throw JsonDataException(
            "Expected one of $labels for key '$labelKey' but found '${reader.nextString()}'. Register a subtype for this label."
          )
        }
        return labelIndex
      }
      throw JsonDataException("Missing label for $labelKey")
    }

    @Throws(IOException::class)
    override fun toJson(writer: JsonWriter, value: Any?) {
      val type: Class<*> = value!!.javaClass
      val labelIndex = subtypes.indexOf(type)
      val adapter: JsonAdapter<Any> = if (labelIndex == -1) {
        requireNotNull(fallbackJsonAdapter) {
          "Expected one of $subtypes but found $value, a ${value.javaClass}. Register this subtype."
        }
      } else {
        jsonAdapters[labelIndex]
      }
      writer.beginObject()
      if (adapter !== fallbackJsonAdapter) {
        writer.name(labelKey).value(labels[labelIndex])
      }
      val flattenToken = writer.beginFlatten()
      adapter.toJson(writer, value)
      writer.endFlatten(flattenToken)
      writer.endObject()
    }

    override fun toString(): String {
      return "PolymorphicJsonAdapter($labelKey)"
    }
  }

  public companion object {
    /**
     * @param baseType The base type for which this factory will create adapters. Cannot be Object.
     * @param labelKey The key in the JSON object whose value determines the type to which to map the
     * JSON object.
     */
    @JvmStatic
    @CheckReturnValue
    public fun <T> of(baseType: Class<T>, labelKey: String): PolymorphicJsonAdapterFactory<T> {
      return PolymorphicJsonAdapterFactory(
        baseType = baseType,
        labelKey = labelKey,
        labels = emptyList(),
        subtypes = emptyList(),
        fallbackJsonAdapter = null
      )
    }
  }
}
