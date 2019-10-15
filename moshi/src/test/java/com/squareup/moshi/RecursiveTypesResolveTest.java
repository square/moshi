/*
 * Copyright (C) 2017 Gson Authors
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

import com.squareup.moshi.internal.Util;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test fixes for infinite recursion on {@link Util#resolve(java.lang.reflect.Type, Class,
 * java.lang.reflect.Type)}, described at <a href="https://github.com/google/gson/issues/440">Issue #440</a>
 * and similar issues.
 * <p>
 * These tests originally caused {@link StackOverflowError} because of infinite recursion on attempts to
 * resolve generics on types, with an intermediate types like 'Foo2&lt;? extends ? super ? extends ... ? extends A&gt;'
 * <p>
 * Adapted from https://github.com/google/gson/commit/a300148003e3a067875b1444e8268b6e0f0e0e02 in
 * service of https://github.com/square/moshi/issues/338.
 */
public final class RecursiveTypesResolveTest {

  private static class Foo1<A> {
    public Foo2<? extends A> foo2;
  }

  private static class Foo2<B> {
    public Foo1<? super B> foo1;
  }

  /**
   * Test simplest case of recursion.
   */
  @Test public void recursiveResolveSimple() {
    JsonAdapter<Foo1> adapter = new Moshi.Builder().build().adapter(Foo1.class);
    assertNotNull(adapter);
  }

  //
  // Tests belows check the behaviour of the methods changed for the fix
  //

  @Test public void doubleSupertype() {
    assertEquals(Types.supertypeOf(Number.class),
            Types.supertypeOf(Types.supertypeOf(Number.class)));
  }

  @Test public void doubleSubtype() {
    assertEquals(Types.subtypeOf(Number.class),
            Types.subtypeOf(Types.subtypeOf(Number.class)));
  }

  @Test public void superSubtype() {
    assertEquals(Types.subtypeOf(Object.class),
            Types.supertypeOf(Types.subtypeOf(Number.class)));
  }

  @Test public void subSupertype() {
    assertEquals(Types.subtypeOf(Object.class),
            Types.subtypeOf(Types.supertypeOf(Number.class)));
  }
}
