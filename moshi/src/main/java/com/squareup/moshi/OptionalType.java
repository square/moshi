package com.squareup.moshi;

import java.lang.reflect.Type;

public interface OptionalType extends Type {
  boolean isOptional();
  Type getRawType();
}
