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

import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Magic that creates instances of arbitrary concrete classes. Derived from Gson's UnsafeAllocator
 * and ConstructorConstructor classes.
 *
 * @author Joel Leitch
 * @author Jesse Wilson
 */
abstract class ClassFactory<T> {

    /**
     * This can't be a {@link LinkedHashTreeMap} since {@link Class}
     * does not implement {@link Comparable}.
     */
    private static Map<Class<?>, ClassFactory<?>> unsafeFactories = new HashMap<>();

    abstract T newInstance() throws
            InvocationTargetException, IllegalAccessException, InstantiationException;

    public static <T> ClassFactory<T> get(final Class<?> rawType) {
        if (unsafeFactories.containsKey(rawType)) {
            //noinspection unchecked
            return (ClassFactory<T>) unsafeFactories.get(rawType);
        }

        // Try to find a constructor with the JsonConstructor annotation
        for (int i = 0; i < rawType.getConstructors().length; i++) {
            final Constructor<?> constructor = rawType.getConstructors()[i];
            boolean hasAnnotation = constructor.isAnnotationPresent(JsonConstructor.class);

            if (hasAnnotation) {
                constructor.setAccessible(true);
                ClassFactory<T> factory = new ClassFactory<T>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    T newInstance() throws InvocationTargetException, IllegalAccessException, InstantiationException {
                        Class<?>[] parameterTypes = constructor.getParameterTypes();
                        Object[] args = new Object[parameterTypes.length];
                        for (int j = 0; j < parameterTypes.length; j++) {
                            Class<?> parameterType = parameterTypes[j];
                            // Default values according to
                            // https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html
                            if (parameterType == byte.class || parameterType == Byte.class) {
                                args[j] = (byte) 0;
                            } else if (parameterType == short.class || parameterType == Short.class) {
                                args[j] = (short) 0;
                            } else if (parameterType == int.class || parameterType == Integer.class) {
                                args[j] = 0;
                            } else if (parameterType == long.class || parameterType == Long.class) {
                                args[j] = 0L;
                            } else if (parameterType == float.class || parameterType == Float.class) {
                                args[j] = 0f;
                            } else if (parameterType == double.class || parameterType == Double.class) {
                                args[j] = 0.0;
                            } else if (parameterType == char.class || parameterType == Character.class) {
                                args[j] = '\u0000';
                            } else if (parameterType == boolean.class || parameterType == Boolean.class) {
                                args[j] = false;
                            } else if (parameterType == String.class) {
                                args[j] = "";
                            } else if (parameterType.isInterface()) {
                                args[j] = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{parameterType}, new InvocationHandler() {
                                    @Override
                                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                        throw new IllegalAccessException("Trying to call a method on a " +
                                                "stub object created by Moshi as requested using the JsonConstructor" +
                                                "annotation.");
                                    }
                                });
                            } else if (parameterType.isEnum() && parameterType.getEnumConstants().length > 0) {
                                args[j] = parameterType.getEnumConstants()[0];
                            } else {
                                args[j] = ClassFactory.get(parameterType).newInstance();
                            }
                            System.out.println("Constructed " + j + "/" + parameterTypes.length);
                        }

                        return (T) constructor.newInstance(args);
                    }

                    @Override
                    public String toString() {
                        return rawType.getName();
                    }
                };
                unsafeFactories.put(rawType, factory);
                return factory;
            }
        }

        // Try to find a no-args constructor. May be any visibility including private.
        try {
            final Constructor<?> constructor = rawType.getDeclaredConstructor();
            constructor.setAccessible(true);
            ClassFactory<T> factory = new ClassFactory<T>() {
                @SuppressWarnings("unchecked") // T is the same raw type as is requested
                @Override
                public T newInstance() throws IllegalAccessException, InvocationTargetException,
                        InstantiationException {
                    Object[] args = null;
                    return (T) constructor.newInstance(args);
                }

                @Override
                public String toString() {
                    return rawType.getName();
                }
            };
            unsafeFactories.put(rawType, factory);
            return factory;
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
            ClassFactory<T> factory = new ClassFactory<T>() {
                @SuppressWarnings("unchecked")
                @Override
                public T newInstance() throws InvocationTargetException, IllegalAccessException {
                    return (T) allocateInstance.invoke(unsafe, rawType);
                }

                @Override
                public String toString() {
                    return rawType.getName();
                }
            };
            unsafeFactories.put(rawType, factory);
            return factory;
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
            ClassFactory<T> factory = new ClassFactory<T>() {
                @SuppressWarnings("unchecked")
                @Override
                public T newInstance() throws InvocationTargetException, IllegalAccessException {
                    return (T) newInstance.invoke(null, rawType, constructorId);
                }

                @Override
                public String toString() {
                    return rawType.getName();
                }
            };
            unsafeFactories.put(rawType, factory);
            return factory;
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
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
            ClassFactory<T> factory = new ClassFactory<T>() {
                @SuppressWarnings("unchecked")
                @Override
                public T newInstance() throws InvocationTargetException, IllegalAccessException {
                    return (T) newInstance.invoke(null, rawType, Object.class);
                }

                @Override
                public String toString() {
                    return rawType.getName();
                }
            };
            unsafeFactories.put(rawType, factory);
            return factory;
        } catch (Exception ignored) {
        }

        throw new IllegalArgumentException("cannot construct instances of " + rawType.getName());
    }
}
