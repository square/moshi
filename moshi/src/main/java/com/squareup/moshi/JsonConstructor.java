package com.squareup.moshi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotate a single constructor with this annotation
 * to force Moshi to call it with default values for
 * primitive types and empty instances for reference types.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonConstructor {
}
