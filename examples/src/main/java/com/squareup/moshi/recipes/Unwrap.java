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
package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.recipes.Unwrap.EnvelopeJsonAdapter.Enveloped;
import com.squareup.moshi.recipes.models.Card;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class Unwrap {
  private Unwrap() {
  }

  public static void main(String[] args) throws Exception {
    String json = ""
        + "{\"data\":"
        + "  {\n"
        + "    \"rank\": \"4\",\n"
        + "    \"suit\": \"CLUBS\"\n"
        + "  }"
        + "}";
    Moshi moshi = new Moshi.Builder().add(EnvelopeJsonAdapter.FACTORY).build();
    JsonAdapter<Card> adapter = moshi.adapter(Card.class, Enveloped.class);
    Card out = adapter.fromJson(json);
    System.out.println(out);
  }

  public static final class EnvelopeJsonAdapter extends JsonAdapter<Object> {
    public static final JsonAdapter.Factory FACTORY = new Factory() {
      @Override public @Nullable JsonAdapter<?> create(
          Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        Set<? extends Annotation> delegateAnnotations =
            Types.nextAnnotations(annotations, Enveloped.class);
        if (delegateAnnotations == null) {
          return null;
        }
        Type envelope =
            Types.newParameterizedTypeWithOwner(EnvelopeJsonAdapter.class, Envelope.class, type);
        JsonAdapter<Envelope<?>> delegate = moshi.nextAdapter(this, envelope, delegateAnnotations);
        return new EnvelopeJsonAdapter(delegate);
      }
    };

    @Retention(RUNTIME) @JsonQualifier public @interface Enveloped {
    }

    private static final class Envelope<T> {
      final T data;

      Envelope(T data) {
        this.data = data;
      }
    }

    private final JsonAdapter<Envelope<?>> delegate;

    EnvelopeJsonAdapter(JsonAdapter<Envelope<?>> delegate) {
      this.delegate = delegate;
    }

    @Override public Object fromJson(JsonReader reader) throws IOException {
      return delegate.fromJson(reader).data;
    }

    @Override public void toJson(JsonWriter writer, Object value) throws IOException {
      delegate.toJson(writer, new Envelope<>(value));
    }
  }
}
