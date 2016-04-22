package com.squareup.moshi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

import static com.squareup.moshi.Util.NO_ANNOTATIONS;

public class AndroidAdapterMethodsTest {
  @Test
  public void matchAdapterWithIncorrectEqualsParameterizedTypeImplementation() throws Exception {
    List<AdapterMethodsFactory.AdapterMethod> toAdapters = new ArrayList<>();
    List<AdapterMethodsFactory.AdapterMethod> fromAdapters = new ArrayList<>();
    Type type = new IncorrectEqualsParameterizedType();
    AdapterMethodsFactory.AdapterMethod adapterMethod =
        new AdapterMethodsFactory.AdapterMethod(type, NO_ANNOTATIONS, null, null, false) {
        };
    toAdapters.add(adapterMethod);
    fromAdapters.add(adapterMethod);
    AdapterMethodsFactory adapterMethodsFactory =
        new AdapterMethodsFactory(toAdapters, fromAdapters);

    ParameterizedType parameterizedType = Types.newParameterizedType(Wrapper.class, Value.class);

    JsonAdapter<?> jsonAdapter =
        adapterMethodsFactory.create(parameterizedType, NO_ANNOTATIONS, null);

    Assert.assertNotNull(jsonAdapter);
  }
}

class Wrapper<T> {
}

class Value {
}

/** Emulates Android's org.apache.harmony.luni.lang.reflect.ImplForType implementation */
class IncorrectEqualsParameterizedType implements ParameterizedType {
  @Override public Type[] getActualTypeArguments() {
    return new Type[] {Value.class};
  }

  @Override public Type getRawType() {
    return Wrapper.class;
  }

  @Override public Type getOwnerType() {
    return null;
  }

  // no equals()

  // no hashCode()
}