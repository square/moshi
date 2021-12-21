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
package com.squareup.moshi

import kotlin.Throws
import java.lang.reflect.InvocationTargetException
import com.squareup.moshi.internal.Util
import java.io.ObjectStreamClass
import java.io.ObjectInputStream

/**
 * Magic that creates instances of arbitrary concrete classes. Derived from Gson's UnsafeAllocator
 * and ConstructorConstructor classes.
 *
 * @author Joel Leitch
 * @author Jesse Wilson
 */
internal abstract class ClassFactory<T> {
  @Throws(InvocationTargetException::class, IllegalAccessException::class, InstantiationException::class)
  abstract fun newInstance(): T

  companion object {
    @JvmStatic
    operator fun <T> get(rawType: Class<*>): ClassFactory<T> {
      // Try to find a no-args constructor. May be any visibility including private.
      try {
        val constructor = rawType.getDeclaredConstructor()
        constructor.isAccessible = true
        return object : ClassFactory<T>() {
          override fun newInstance(): T {
            return constructor.newInstance() as T
          }

          override fun toString(): String {
            return rawType.name
          }
        }
      } catch (ignored: NoSuchMethodException) {
        // No no-args constructor. Fall back to something more magical...
      }

      // Try the JVM's Unsafe mechanism.
      // public class Unsafe {
      //   public Object allocateInstance(Class<?> type);
      // }
      try {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val f = unsafeClass.getDeclaredField("theUnsafe")
        f.isAccessible = true
        val unsafe = f[null]
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return object : ClassFactory<T>() {
          override fun newInstance(): T {
            return allocateInstance.invoke(unsafe, rawType) as T
          }

          override fun toString(): String {
            return rawType.name
          }
        }
      } catch (e: IllegalAccessException) {
        throw AssertionError()
      } catch (ignored: ClassNotFoundException) {
        // Not the expected version of the Oracle Java library!
      } catch (ignored: NoSuchMethodException) {
        // Not the expected version of the Oracle Java library!
      } catch (ignored: NoSuchFieldException) {
        // Not the expected version of the Oracle Java library!
      }

      // Try (post-Gingerbread) Dalvik/libcore's ObjectStreamClass mechanism.
      // public class ObjectStreamClass {
      //   private static native int getConstructorId(Class<?> c);
      //   private static native Object newInstance(Class<?> instantiationClass, int methodId);
      // }

      try {
        val getConstructorId =
          ObjectStreamClass::class.java.getDeclaredMethod("getConstructorId", Class::class.java)
        getConstructorId.isAccessible = true
        val constructorId = getConstructorId.invoke(null, Any::class.java) as Int
        val newInstance = ObjectStreamClass::class.java.getDeclaredMethod(
          "newInstance",
          Class::class.java,
          Int::class.javaPrimitiveType
        )
        newInstance.isAccessible = true
        return object : ClassFactory<T>() {
          override fun newInstance(): T {
            return newInstance.invoke(null, rawType, constructorId) as T
          }

          override fun toString(): String {
            return rawType.name
          }
        }
      } catch (e: IllegalAccessException) {
        throw AssertionError()
      } catch (e: InvocationTargetException) {
        throw Util.rethrowCause(e)
      } catch (ignored: NoSuchMethodException) {
        // Not the expected version of Dalvik/libcore!
      }

      // Try (pre-Gingerbread) Dalvik/libcore's ObjectInputStream mechanism.
      // public class ObjectInputStream {
      //   private static native Object newInstance(
      //     Class<?> instantiationClass, Class<?> constructorClass);
      // }
      try {
        val newInstance =
          ObjectInputStream::class.java.getDeclaredMethod("newInstance", Class::class.java, Class::class.java)
        newInstance.isAccessible = true
        return object : ClassFactory<T>() {
          override fun newInstance(): T {
            return newInstance.invoke(null, rawType, Any::class.java) as T
          }

          override fun toString(): String {
            return rawType.name
          }
        }
      } catch (ignored: Exception) {
      }

      throw IllegalArgumentException("cannot construct instances of ${rawType.name}")
    }
  }
}
