/*
 * Copyright (C) 2014 Square, Inc.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;

final class Util {
  public static final Annotation[] EMPTY_ANNOTATIONS_ARRAY = new Annotation[0];

  public static final AnnotatedElement NO_ANNOTATIONS = new AnnotatedElement() {
    @Override public boolean isAnnotationPresent(Class<? extends Annotation> aClass) {
      return false;
    }
    @Override public <T extends Annotation> T getAnnotation(Class<T> tClass) {
      return null;
    }
    @Override public Annotation[] getAnnotations() {
      return EMPTY_ANNOTATIONS_ARRAY;
    }
    @Override public Annotation[] getDeclaredAnnotations() {
      return EMPTY_ANNOTATIONS_ARRAY;
    }
  };

  public static boolean typesMatch(Type pattern, Type candidate) {
    // TODO: permit raw types (like Set.class) to match non-raw candidates (like Set<Long>).
    return pattern.equals(candidate);
  }
}
