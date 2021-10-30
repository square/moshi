/*
 * Copyright (C) 2021 Square, Inc.
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
package com.squareup.moshi;

import com.squareup.moshi.internal.OptionalJsonAdapter;
import com.squareup.moshi.internal.Util;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;

final class OptionalJsonAdapterFactory implements JsonAdapter.Factory {

  static final JsonAdapter.Factory INSTANCE =
      !Util.SUPPORTS_OPTIONAL ? null : new OptionalJsonAdapterFactory();

  private OptionalJsonAdapterFactory() {}

  @Nullable
  @Override
  public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
    if (type instanceof ParameterizedType && Util.isOptionalType(type)) {
      return new OptionalJsonAdapter<>(
          moshi.adapter(((ParameterizedType) type).getActualTypeArguments()[0]));
    }
    return null;
  }

  @Override
  public String toString() {
    return "OptionalJsonAdapterFactory";
  }
}
