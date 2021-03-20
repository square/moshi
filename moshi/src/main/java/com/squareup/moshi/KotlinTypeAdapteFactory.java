package com.squareup.moshi;

import com.squareup.moshi.internal.Util;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

class KotlinTypeAdapteFactory {
  static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {
        @Nullable
        @Override
        public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
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
