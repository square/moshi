/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.moshi.internal;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import javax.annotation.Nullable;

public final class ObjectJsonAdapter<T> extends JsonAdapter<T> {

  private final T instance;

  public ObjectJsonAdapter(T instance) {
    this.instance = instance;
  }

  @Override public @Nullable T fromJson(JsonReader reader) throws IOException {
    reader.beginObject();
    while (reader.hasNext()) {
      reader.skipName();
      reader.skipValue();
    }
    reader.endObject();
    return instance;
  }

  @Override public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
    writer.beginObject().endObject();
  }

  @Override public String toString() {
    return "ObjectJsonAdapter<" + instance.getClass().getCanonicalName() + ">";
  }
}
