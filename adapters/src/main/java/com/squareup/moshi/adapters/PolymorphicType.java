package com.squareup.moshi.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that a given type is a Polymorphic type. This annotation should be applied on to the
 * base type. Moshi's Kotlin support (reflective or code gen) can read this annotation and
 * automatically infer/manage these polymorphic types without any custom wiring.
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
public @interface PolymorphicType {
  /**
   * The key in the JSON object whose value determines the type to which to map the * JSON object.
   */
  String typeLabel();

  /**
   * If you want to have a fallback type for when decoding encounters an unknown label, you can
   * specify it here. Note that the expectation of the Kotlin support for this is that the default
   * type is a Kotlin {@code object} class. If you require a dynamically instantiated class, you
   * must manually wire this up via {@link PolymorphicJsonAdapterFactory}.
   */
  Class<?> fallbackType() default void.class;
}
