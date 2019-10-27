/*
 * Copyright (C) 2011 Google Inc.
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
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Magic that creates instances of arbitrary concrete classes. Derived from Gson's UnsafeAllocator
 * and ConstructorConstructor classes.
 *
 * @author Joel Leitch
 * @author Jesse Wilson
 */
abstract class ClassFactory<T> {
  abstract T newInstance() throws
      InvocationTargetException, IllegalAccessException, InstantiationException;

  public static <T> ClassFactory<T> get(final Class<?> rawType) {
    // Try to find a no-args constructor. May be any visibility including private.
    try {
      final Constructor<?> constructor = rawType.getDeclaredConstructor();
      constructor.setAccessible(true);
      return new ClassFactory<T>() {
        @SuppressWarnings("unchecked") // T is the same raw type as is requested
        @Override public T newInstance() throws IllegalAccessException, InvocationTargetException,
            InstantiationException {
          Object[] args = null;
          return (T) constructor.newInstance(args);
        }
        @Override public String toString() {
          return rawType.getName();
        }
      };
    } catch (NoSuchMethodException ignored) {
      // No no-args constructor. Fall back to something more magical...
    }

    // Try the JVM's Unsafe mechanism.
    // public class Unsafe {
    //   public Object allocateInstance(Class<?> type);
    // }
    try {
      Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      Field f = unsafeClass.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      final Object unsafe = f.get(null);
      final Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
      return new ClassFactory<T>() {
        @SuppressWarnings("unchecked")
        @Override public T newInstance() throws InvocationTargetException, IllegalAccessException {
          return (T) allocateInstance.invoke(unsafe, rawType);
        }
        @Override public String toString() {
          return rawType.getName();
        }
      };
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) {
      // Not the expected version of the Oracle Java library!
    }

    // Try (post-Gingerbread) Dalvik/libcore's ObjectStreamClass mechanism.
    // public class ObjectStreamClass {
    //   private static native int getConstructorId(Class<?> c);
    //   private static native Object newInstance(Class<?> instantiationClass, int methodId);
    // }
    try {
      Method getConstructorId = ObjectStreamClass.class.getDeclaredMethod(
          "getConstructorId", Class.class);
      getConstructorId.setAccessible(true);
      final int constructorId = (Integer) getConstructorId.invoke(null, Object.class);
      final Method newInstance = ObjectStreamClass.class.getDeclaredMethod("newInstance",
          Class.class, int.class);
      newInstance.setAccessible(true);
      return new ClassFactory<T>() {
        @SuppressWarnings("unchecked")
        @Override public T newInstance() throws InvocationTargetException, IllegalAccessException {
          return (T) newInstance.invoke(null, rawType, constructorId);
        }
        @Override public String toString() {
          return rawType.getName();
        }
      };
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    } catch (InvocationTargetException e) {
      throw Util.rethrowCause(e);
    } catch (NoSuchMethodException ignored) {
      // Not the expected version of Dalvik/libcore!
    }

    // Try (pre-Gingerbread) Dalvik/libcore's ObjectInputStream mechanism.
    // public class ObjectInputStream {
    //   private static native Object newInstance(
    //     Class<?> instantiationClass, Class<?> constructorClass);
    // }
    try {
      final Method newInstance = ObjectInputStream.class.getDeclaredMethod(
          "newInstance", Class.class, Class.class);
      newInstance.setAccessible(true);
      return new ClassFactory<T>() {
        @SuppressWarnings("unchecked")
        @Override public T newInstance() throws InvocationTargetException, IllegalAccessException {
          return (T) newInstance.invoke(null, rawType, Object.class);
        }
        @Override public String toString() {
          return rawType.getName();
        }
      };
    } catch (Exception ignored) {
    }

    throw new IllegalArgumentException("cannot construct instances of " + rawType.getName());
  }
}
