/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Represents a generic type {@code T}. Java doesn't yet provide a way to represent generic types,
 * so this class does. Forces clients to create a subclass of this class which enables retrieval the
 * type information even at runtime.
 *
 * <p>For example, to create a type literal for {@code List<String>}, you can create an empty
 * anonymous inner class: <pre>   {@code
 *
 *   TypeToken<List<String>> list = new TypeToken<List<String>>() {};
 * }</pre>
 *
 * <p>This syntax cannot be used to create type literals that have wildcard parameters, such as
 * {@code Class<?>} or {@code List<? extends CharSequence>}.
 *
 * @author Bob Lee
 * @author Sven Mawson
 * @author Jesse Wilson
 */
public class TypeLiteral<T> {
  final Class<? super T> rawType;
  final Type type;
  final int hashCode;

  /**
   * Constructs a new type literal. Derives represented class from type parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type parameter in the
   * anonymous class's type hierarchy so we can reconstitute it at runtime despite erasure.
   */
  @SuppressWarnings("unchecked")
  protected TypeLiteral() {
    this.type = getSuperclassTypeParameter(getClass());
    this.rawType = (Class<? super T>) Types.getRawType(type);
    this.hashCode = type.hashCode();
  }

  /** Unsafe. Constructs a type literal manually. */
  @SuppressWarnings("unchecked")
  TypeLiteral(Type type) {
    if (type == null) throw new NullPointerException("type == null");
    this.type = Types.canonicalize(type);
    this.rawType = (Class<? super T>) Types.getRawType(this.type);
    this.hashCode = this.type.hashCode();
  }

  /** Returns the type from super class's type parameter in canonical for}. */
  static Type getSuperclassTypeParameter(Class<?> subclass) {
    Type superclass = subclass.getGenericSuperclass();
    if (superclass instanceof Class) throw new IllegalArgumentException("Missing type parameter.");
    ParameterizedType parameterized = (ParameterizedType) superclass;
    return Types.canonicalize(parameterized.getActualTypeArguments()[0]);
  }

  /** Returns the raw (non-generic) type for this type literal. */
  public final Class<? super T> getRawType() {
    return rawType;
  }

  public final Type getType() {
    return type;
  }

  @Override public final int hashCode() {
    return this.hashCode;
  }

  @Override public final boolean equals(Object o) {
    return o instanceof TypeLiteral<?>
        && Types.equals(type, ((TypeLiteral<?>) o).type);
  }

  @Override public final String toString() {
    return Types.typeToString(type);
  }

  /** Returns a type literal for {@code type}. */
  public static TypeLiteral<?> get(Type type) {
    return new TypeLiteral<Object>(type);
  }

  /** Returns a type literal for {@code type}. */
  public static <T> TypeLiteral<T> get(Class<T> type) {
    return new TypeLiteral<T>(type);
  }
}
