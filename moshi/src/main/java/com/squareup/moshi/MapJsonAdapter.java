/*
 * Copyright (C) 2015 Square, Inc.
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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Converts maps with string keys to JSON objects.
 *
 * TODO: support maps with other key types and convert to/from strings.
 */
final class MapJsonAdapter<K, V> extends JsonAdapter<Map<K, V>> {
  public static final Factory FACTORY = new Factory() {
    @Override public JsonAdapter<?> create(Type type, AnnotatedElement annotations, Moshi moshi) {
      Class<?> rawType = Types.getRawType(type);
      if (rawType != Map.class) return null;
      Type[] keyAndValue = Types.mapKeyAndValueTypes(type, rawType);
      if (keyAndValue[0] != String.class) return null;
      return new MapJsonAdapter<>(moshi, keyAndValue[1]).nullSafe();
    }
  };

  private final JsonAdapter<V> valueAdapter;

  public MapJsonAdapter(Moshi moshi, Type valueType) {
    this.valueAdapter = moshi.adapter(valueType);
  }

  @Override public void toJson(JsonWriter writer, Map<K, V> map) throws IOException {
    writer.beginObject();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      writer.name((String) entry.getKey());
      valueAdapter.toJson(writer, entry.getValue());
    }
    writer.endObject();
  }

  @Override public Map<K, V> fromJson(JsonReader reader) throws IOException {
    LinkedHashTreeMap<K, V> result = new LinkedHashTreeMap<>();
    reader.beginObject();
    while (reader.hasNext()) {
      @SuppressWarnings("unchecked") // Currently 'K' is always 'String'.
      K name = (K) reader.nextName();
      V value = valueAdapter.fromJson(reader);
      V replaced = result.put(name, value);
      if (replaced != null) {
        throw new IllegalArgumentException("object property '" + name + "' has multiple values");
      }
    }
    reader.endObject();
    return result;
  }
}
