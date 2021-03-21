/*
 * Copyright (C) 2020 Square, Inc.
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

import com.squareup.moshi.internal.Util;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

class KotlinTypeAdapteFactory {
  static final JsonAdapter.Factory FACTORY =
      new JsonAdapter.Factory() {
        @Nullable
        @Override
        public JsonAdapter<?> create(
            Type type, Set<? extends Annotation> annotations, Moshi moshi) {
          if (type instanceof Util.KotlinType) {
            Util.KotlinType kotlinType = (Util.KotlinType) type;
            JsonAdapter<?> adapter = moshi.adapter(kotlinType.getOriginalType(), annotations);
            if (kotlinType.getMarkedNullable()) {
              return adapter.nullSafe();
            } else {
              return adapter.nonNull();
            }
          } else {
            return null;
          }
        }
      };
}
