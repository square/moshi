package com.squareup.moshi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** annotation for nullable item in collection. */
@Target(value = {ElementType.FIELD, ElementType.LOCAL_VARIABLE})
@Retention(value = RetentionPolicy.RUNTIME)
@JsonQualifier
public @interface NullableValues {
}
