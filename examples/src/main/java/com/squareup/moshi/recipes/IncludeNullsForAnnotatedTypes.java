/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.recipes;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Set;

public final class IncludeNullsForAnnotatedTypes {
  public void run() throws Exception {
    Moshi moshi = new Moshi.Builder().add(new AlwaysSerializeNullsFactory()).build();

    JsonAdapter<Driver> driverAdapter = moshi.adapter(Driver.class);

    Car car = new Car();
    car.make = "Ford";
    car.model = "Mach-E";
    car.color = null; // This null will show up in the JSON because Car has @AlwaysSerializeNulls.

    Driver driver = new Driver();
    driver.name = "Jesse";
    driver.emailAddress = null; // This null will be omitted.
    driver.favoriteCar = car;

    System.out.println(driverAdapter.toJson(driver));
  }

  @Target(TYPE)
  @Retention(RUNTIME)
  public @interface AlwaysSerializeNulls {}

  @AlwaysSerializeNulls
  static class Car {
    String make;
    String model;
    String color;
  }

  static class Driver {
    String name;
    String emailAddress;
    Car favoriteCar;
  }

  static class AlwaysSerializeNullsFactory implements JsonAdapter.Factory {
    @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      Class<?> rawType = Types.getRawType(type);
      if (!rawType.isAnnotationPresent(AlwaysSerializeNulls.class)) {
        return null;
      }
      JsonAdapter<Object> delegate = moshi.nextAdapter(this, type, annotations);
      return delegate.serializeNulls();
    }
  }

  public static void main(String[] args) throws Exception {
    new IncludeNullsForAnnotatedTypes().run();
  }
}
