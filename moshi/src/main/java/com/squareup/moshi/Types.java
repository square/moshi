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

import com.squareup.moshi.internal.Util.GenericArrayTypeImpl;
import com.squareup.moshi.internal.Util.ParameterizedTypeImpl;
import com.squareup.moshi.internal.Util.WildcardTypeImpl;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

import static com.squareup.moshi.internal.Util.EMPTY_TYPE_ARRAY;
import static com.squareup.moshi.internal.Util.getGenericSupertype;
import static com.squareup.moshi.internal.Util.resolve;

/** Factory methods for types. */
@CheckReturnValue
public final class Types {
  private Types() {
  }

  /**
   * Resolves the generated {@link JsonAdapter} fully qualified class name for a given
   * {@link JsonClass JsonClass-annotated} {@code clazz}. This is the same lookup logic used by
   * both the Moshi code generation as well as lookup for any JsonClass-annotated classes. This can
   * be useful if generating your own JsonAdapters without using Moshi's first party code gen.
   *
   * @param clazz the class to calculate a generated JsonAdapter name for.
   * @return the resolved fully qualified class name to the expected generated JsonAdapter class.
   *         Note that this name will always be a top-level class name and not a nested class.
   */
  public static String generatedJsonAdapterName(Class<?> clazz) {
    if (clazz.getAnnotation(JsonClass.class) == null) {
      throw new IllegalArgumentException("Class does not have a JsonClass annotation: " + clazz);
    }
    return generatedJsonAdapterName(clazz.getName());
  }

  /**
   * Resolves the generated {@link JsonAdapter} fully qualified class name for a given
   * {@link JsonClass JsonClass-annotated} {@code className}. This is the same lookup logic used by
   * both the Moshi code generation as well as lookup for any JsonClass-annotated classes. This can
   * be useful if generating your own JsonAdapters without using Moshi's first party code gen.
   *
   * @param className the fully qualified class to calculate a generated JsonAdapter name for.
   * @return the resolved fully qualified class name to the expected generated JsonAdapter class.
   *         Note that this name will always be a top-level class name and not a nested class.
   */
  public static String generatedJsonAdapterName(String className) {
    return className.replace("$", "_") + "JsonAdapter";
  }

  /**
   * Checks if {@code annotations} contains {@code jsonQualifier}.
   * Returns the subset of {@code annotations} without {@code jsonQualifier}, or null if {@code
   * annotations} does not contain {@code jsonQualifier}.
   */
  public static @Nullable Set<? extends Annotation> nextAnnotations(
      Set<? extends Annotation> annotations,
      Class<? extends Annotation> jsonQualifier) {
    if (!jsonQualifier.isAnnotationPresent(JsonQualifier.class)) {
      throw new IllegalArgumentException(jsonQualifier + " is not a JsonQualifier.");
    }
    if (annotations.isEmpty()) {
      return null;
    }
    for (Annotation annotation : annotations) {
      if (jsonQualifier.equals(annotation.annotationType())) {
        Set<? extends Annotation> delegateAnnotations = new LinkedHashSet<>(annotations);
        delegateAnnotations.remove(annotation);
        return Collections.unmodifiableSet(delegateAnnotations);
      }
    }
    return null;
  }

  /**
   * Returns a new parameterized type, applying {@code typeArguments} to {@code rawType}. Use this
   * method if {@code rawType} is not enclosed in another type.
   */
  public static ParameterizedType newParameterizedType(Type rawType, Type... typeArguments) {
    if (typeArguments.length == 0) {
      throw new IllegalArgumentException("Missing type arguments for " + rawType);
    }
    return new ParameterizedTypeImpl(null, rawType, typeArguments);
  }

  /**
   * Returns a new parameterized type, applying {@code typeArguments} to {@code rawType}. Use this
   * method if {@code rawType} is enclosed in {@code ownerType}.
   */
  public static ParameterizedType newParameterizedTypeWithOwner(
      Type ownerType, Type rawType, Type... typeArguments) {
    if (typeArguments.length == 0) {
      throw new IllegalArgumentException("Missing type arguments for " + rawType);
    }
    return new ParameterizedTypeImpl(ownerType, rawType, typeArguments);
  }

  /** Returns an array type whose elements are all instances of {@code componentType}. */
  public static GenericArrayType arrayOf(Type componentType) {
    return new GenericArrayTypeImpl(componentType);
  }

  /**
   * Returns a type that represents an unknown type that extends {@code bound}. For example, if
   * {@code bound} is {@code CharSequence.class}, this returns {@code ? extends CharSequence}. If
   * {@code bound} is {@code Object.class}, this returns {@code ?}, which is shorthand for {@code
   * ? extends Object}.
   */
  public static WildcardType subtypeOf(Type bound) {
    Type[] upperBounds;
    if (bound instanceof WildcardType) {
      upperBounds = ((WildcardType) bound).getUpperBounds();
    } else {
      upperBounds = new Type[] { bound };
    }
    return new WildcardTypeImpl(upperBounds, EMPTY_TYPE_ARRAY);
  }

  /**
   * Returns a type that represents an unknown supertype of {@code bound}. For example, if {@code
   * bound} is {@code String.class}, this returns {@code ? super String}.
   */
  public static WildcardType supertypeOf(Type bound) {
    Type[] lowerBounds;
    if (bound instanceof WildcardType) {
      lowerBounds = ((WildcardType) bound).getLowerBounds();
    } else {
      lowerBounds = new Type[] { bound };
    }
    return new WildcardTypeImpl(new Type[] { Object.class }, lowerBounds);
  }

  public static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      // type is a normal class.
      return (Class<?>) type;

    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;

      // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
      // suspects some pathological case related to nested classes exists.
      Type rawType = parameterizedType.getRawType();
      return (Class<?>) rawType;

    } else if (type instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) type).getGenericComponentType();
      return Array.newInstance(getRawType(componentType), 0).getClass();

    } else if (type instanceof TypeVariable) {
      // We could use the variable's bounds, but that won't work if there are multiple. having a raw
      // type that's more general than necessary is okay.
      return Object.class;

    } else if (type instanceof WildcardType) {
      return getRawType(((WildcardType) type).getUpperBounds()[0]);

    } else {
      String className = type == null ? "null" : type.getClass().getName();
      throw new IllegalArgumentException("Expected a Class, ParameterizedType, or "
          + "GenericArrayType, but <" + type + "> is of type " + className);
    }
  }

  /**
   * Returns the element type of this collection type.
   * @throws IllegalArgumentException if this type is not a collection.
   */
  public static Type collectionElementType(Type context, Class<?> contextRawType) {
    Type collectionType = getSupertype(context, contextRawType, Collection.class);

    if (collectionType instanceof WildcardType) {
      collectionType = ((WildcardType) collectionType).getUpperBounds()[0];
    }
    if (collectionType instanceof ParameterizedType) {
      return ((ParameterizedType) collectionType).getActualTypeArguments()[0];
    }
    return Object.class;
  }

  /** Returns true if {@code a} and {@code b} are equal. */
  public static boolean equals(@Nullable Type a, @Nullable Type b) {
    if (a == b) {
      return true; // Also handles (a == null && b == null).

    } else if (a instanceof Class) {
      if (b instanceof GenericArrayType) {
        return equals(((Class) a).getComponentType(),
            ((GenericArrayType) b).getGenericComponentType());
      }
      return a.equals(b); // Class already specifies equals().

    } else if (a instanceof ParameterizedType) {
      if (!(b instanceof ParameterizedType)) return false;
      ParameterizedType pa = (ParameterizedType) a;
      ParameterizedType pb = (ParameterizedType) b;
      Type[] aTypeArguments = pa instanceof ParameterizedTypeImpl
          ? ((ParameterizedTypeImpl) pa).typeArguments
          : pa.getActualTypeArguments();
      Type[] bTypeArguments = pb instanceof ParameterizedTypeImpl
          ? ((ParameterizedTypeImpl) pb).typeArguments
          : pb.getActualTypeArguments();
      return equals(pa.getOwnerType(), pb.getOwnerType())
          && pa.getRawType().equals(pb.getRawType())
          && Arrays.equals(aTypeArguments, bTypeArguments);

    } else if (a instanceof GenericArrayType) {
      if (b instanceof Class) {
        return equals(((Class) b).getComponentType(),
            ((GenericArrayType) a).getGenericComponentType());
      }
      if (!(b instanceof GenericArrayType)) return false;
      GenericArrayType ga = (GenericArrayType) a;
      GenericArrayType gb = (GenericArrayType) b;
      return equals(ga.getGenericComponentType(), gb.getGenericComponentType());

    } else if (a instanceof WildcardType) {
      if (!(b instanceof WildcardType)) return false;
      WildcardType wa = (WildcardType) a;
      WildcardType wb = (WildcardType) b;
      return Arrays.equals(wa.getUpperBounds(), wb.getUpperBounds())
          && Arrays.equals(wa.getLowerBounds(), wb.getLowerBounds());

    } else if (a instanceof TypeVariable) {
      if (!(b instanceof TypeVariable)) return false;
      TypeVariable<?> va = (TypeVariable<?>) a;
      TypeVariable<?> vb = (TypeVariable<?>) b;
      return va.getGenericDeclaration() == vb.getGenericDeclaration()
          && va.getName().equals(vb.getName());

    } else {
      // This isn't a supported type.
      return false;
    }
  }

  /**
   * @param clazz the target class to read the {@code fieldName} field annotations from.
   * @param fieldName the target field name on {@code clazz}.
   * @return a set of {@link JsonQualifier}-annotated {@link Annotation} instances retrieved from
   *         the targeted field. Can be empty if none are found.
   */
  public static Set<? extends Annotation> getFieldJsonQualifierAnnotations(Class<?> clazz,
      String fieldName) {
    try {
      Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      Annotation[] fieldAnnotations = field.getDeclaredAnnotations();
      Set<Annotation> annotations = new LinkedHashSet<>(fieldAnnotations.length);
      for (Annotation annotation : fieldAnnotations) {
        if (annotation.annotationType().isAnnotationPresent(JsonQualifier.class)) {
          annotations.add(annotation);
        }
      }
      return Collections.unmodifiableSet(annotations);
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException("Could not access field "
          + fieldName
          + " on class "
          + clazz.getCanonicalName(),
          e);
    }
  }

  @SuppressWarnings("unchecked")
  static <T extends Annotation> T createJsonQualifierImplementation(final Class<T> annotationType) {
    if (!annotationType.isAnnotation()) {
      throw new IllegalArgumentException(annotationType + " must be an annotation.");
    }
    if (!annotationType.isAnnotationPresent(JsonQualifier.class)) {
      throw new IllegalArgumentException(annotationType + " must have @JsonQualifier.");
    }
    if (annotationType.getDeclaredMethods().length != 0) {
      throw new IllegalArgumentException(annotationType + " must not declare methods.");
    }
    return (T) Proxy.newProxyInstance(annotationType.getClassLoader(),
        new Class<?>[] { annotationType }, new InvocationHandler() {
          @Override public Object invoke(Object proxy, Method method, Object[] args)
              throws Throwable {
            String methodName = method.getName();
            switch (methodName) {
              case "annotationType":
                return annotationType;
              case "equals":
                Object o = args[0];
                return annotationType.isInstance(o);
              case "hashCode":
                return 0;
              case "toString":
                return "@" + annotationType.getName() + "()";
              default:
                return method.invoke(proxy, args);
            }
          }
        });
  }

  /**
   * Returns a two element array containing this map's key and value types in positions 0 and 1
   * respectively.
   */
  static Type[] mapKeyAndValueTypes(Type context, Class<?> contextRawType) {
    // Work around a problem with the declaration of java.util.Properties. That class should extend
    // Hashtable<String, String>, but it's declared to extend Hashtable<Object, Object>.
    if (context == Properties.class) return new Type[] { String.class, String.class };

    Type mapType = getSupertype(context, contextRawType, Map.class);
    if (mapType instanceof ParameterizedType) {
      ParameterizedType mapParameterizedType = (ParameterizedType) mapType;
      return mapParameterizedType.getActualTypeArguments();
    }
    return new Type[] { Object.class, Object.class };
  }

  /**
   * Returns the generic form of {@code supertype}. For example, if this is {@code
   * ArrayList<String>}, this returns {@code Iterable<String>} given the input {@code
   * Iterable.class}.
   *
   * @param supertype a superclass of, or interface implemented by, this.
   */
  static Type getSupertype(Type context, Class<?> contextRawType, Class<?> supertype) {
    if (!supertype.isAssignableFrom(contextRawType)) throw new IllegalArgumentException();
    return resolve(context, contextRawType,
        getGenericSupertype(context, contextRawType, supertype));
  }

  static Type getGenericSuperclass(Type type) {
    Class<?> rawType = Types.getRawType(type);
    return resolve(type, rawType, rawType.getGenericSuperclass());
  }

  /**
   * Returns the element type of {@code type} if it is an array type, or null if it is not an
   * array type.
   */
  static Type arrayComponentType(Type type) {
    if (type instanceof GenericArrayType) {
      return ((GenericArrayType) type).getGenericComponentType();
    } else if (type instanceof Class) {
      return ((Class<?>) type).getComponentType();
    } else {
      return null;
    }
  }
}
