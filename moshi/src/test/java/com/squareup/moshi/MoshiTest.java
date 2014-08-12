/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class MoshiTest {
  /** No nulls for int.class. */
  @Test public void intAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Integer> adapter = moshi.adapter(int.class).lenient();
    assertThat(adapter.fromJson("1")).isEqualTo(1);
    assertThat(adapter.toJson(2)).isEqualTo("2");

    try {
      adapter.fromJson("null");
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).isEqualTo("Expected an int but was NULL at path $");
    }

    try {
      moshi.adapter(int.class).toJson(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  /** Moshi supports nulls for Integer.class. */
  @Test public void integerAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Integer> adapter = moshi.adapter(Integer.class).lenient();
    assertThat(adapter.fromJson("1")).isEqualTo(1);
    assertThat(adapter.toJson(2)).isEqualTo("2");
    assertThat(adapter.fromJson("null")).isEqualTo(null);
    assertThat(adapter.toJson(null)).isEqualTo("null");
  }

  @Test public void stringAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<String> adapter = moshi.adapter(String.class).lenient();
    assertThat(adapter.fromJson("\"a\"")).isEqualTo("a");
    assertThat(adapter.toJson("b")).isEqualTo("\"b\"");
    assertThat(adapter.fromJson("null")).isEqualTo(null);
    assertThat(adapter.toJson(null)).isEqualTo("null");
  }

  @Test public void customJsonAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(Pizza.class, new PizzaAdapter())
        .build();

    JsonAdapter<Pizza> jsonAdapter = moshi.adapter(Pizza.class);
    assertThat(jsonAdapter.toJson(new Pizza(15, true)))
        .isEqualTo("{\"size\":15,\"extra cheese\":true}");
    assertThat(jsonAdapter.fromJson("{\"extra cheese\":true,\"size\":18}"))
        .isEqualTo(new Pizza(18, true));
  }

  @Test public void composingJsonAdapterFactory() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new MealDealAdapterFactory())
        .add(Pizza.class, new PizzaAdapter())
        .build();

    JsonAdapter<MealDeal> jsonAdapter = moshi.adapter(MealDeal.class);
    assertThat(jsonAdapter.toJson(new MealDeal(new Pizza(15, true), "Pepsi")))
        .isEqualTo("[{\"size\":15,\"extra cheese\":true},\"Pepsi\"]");
    assertThat(jsonAdapter.fromJson("[{\"extra cheese\":true,\"size\":18},\"Coke\"]"))
        .isEqualTo(new MealDeal(new Pizza(18, true), "Coke"));
  }

  @Uppercase
  static String uppercaseString;

  @Test public void delegatingJsonAdapterFactory() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new UppercaseAdapterFactory())
        .build();

    AnnotatedElement annotations = MoshiTest.class.getDeclaredField("uppercaseString");
    JsonAdapter<String> adapter = moshi.<String>adapter(String.class, annotations).lenient();
    assertThat(adapter.toJson("a")).isEqualTo("\"A\"");
    assertThat(adapter.fromJson("\"b\"")).isEqualTo("B");
  }

  @Test public void listJsonAdapter() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<List<String>> adapter = moshi.adapter(new TypeLiteral<List<String>>() {
    }.getType());
    assertThat(adapter.toJson(Arrays.asList("a", "b"))).isEqualTo("[\"a\",\"b\"]");
    assertThat(adapter.fromJson("[\"a\",\"b\"]")).isEqualTo(Arrays.asList("a", "b"));
  }

  @Test public void setJsonAdapter() throws Exception {
    Set<String> set = new LinkedHashSet<String>();
    set.add("a");
    set.add("b");

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Set<String>> adapter = moshi.adapter(new TypeLiteral<Set<String>>() {}.getType());
    assertThat(adapter.toJson(set)).isEqualTo("[\"a\",\"b\"]");
    assertThat(adapter.fromJson("[\"a\",\"b\"]")).isEqualTo(set);
  }

  @Test public void collectionJsonAdapter() throws Exception {
    Collection<String> collection = new ArrayDeque<String>();
    collection.add("a");
    collection.add("b");

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Collection<String>> adapter = moshi.adapter(
        new TypeLiteral<Collection<String>>() {}.getType());
    assertThat(adapter.toJson(collection)).isEqualTo("[\"a\",\"b\"]");
    assertThat(adapter.fromJson("[\"a\",\"b\"]")).containsExactly("a", "b");
  }

  @Uppercase
  static List<String> uppercaseStrings;

  @Test public void collectionsDoNotKeepAnnotations() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new UppercaseAdapterFactory())
        .build();

    Field uppercaseStringsField = MoshiTest.class.getDeclaredField("uppercaseStrings");
    JsonAdapter<List<String>> adapter = moshi.adapter(uppercaseStringsField.getGenericType(),
        uppercaseStringsField);
    assertThat(adapter.toJson(Arrays.asList("a"))).isEqualTo("[\"a\"]");
    assertThat(adapter.fromJson("[\"b\"]")).isEqualTo(Arrays.asList("b"));
  }

  @Test public void objectArray() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<String[]> adapter = moshi.adapter(String[].class);
    assertThat(adapter.toJson(new String[] {"a", "b"})).isEqualTo("[\"a\",\"b\"]");
    assertThat(adapter.fromJson("[\"a\",\"b\"]")).containsExactly("a", "b");
  }

  @Test public void primitiveArray() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<int[]> adapter = moshi.adapter(int[].class);
    assertThat(adapter.toJson(new int[] {1, 2})).isEqualTo("[1,2]");
    assertThat(adapter.fromJson("[2,3]")).containsExactly(2, 3);
  }

  static class Pizza {
    final int diameter;
    final boolean extraCheese;

    Pizza(int diameter, boolean extraCheese) {
      this.diameter = diameter;
      this.extraCheese = extraCheese;
    }

    @Override public boolean equals(Object o) {
      return o instanceof Pizza
          && ((Pizza) o).diameter == diameter
          && ((Pizza) o).extraCheese == extraCheese;
    }

    @Override public int hashCode() {
      return diameter * (extraCheese ? 31 : 1);
    }
  }

  static class MealDeal {
    final Pizza pizza;
    final String drink;

    MealDeal(Pizza pizza, String drink) {
      this.pizza = pizza;
      this.drink = drink;
    }

    @Override public boolean equals(Object o) {
      return o instanceof MealDeal
          && ((MealDeal) o).pizza.equals(pizza)
          && ((MealDeal) o).drink.equals(drink);
    }

    @Override public int hashCode() {
      return pizza.hashCode() + (31 * drink.hashCode());
    }
  }

  static class PizzaAdapter extends JsonAdapter<Pizza> {
    @Override public Pizza fromJson(JsonReader reader) throws IOException {
      int diameter = 13;
      boolean extraCheese = false;
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (name.equals("size")) {
          diameter = reader.nextInt();
        } else if (name.equals("extra cheese")) {
          extraCheese = reader.nextBoolean();
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return new Pizza(diameter, extraCheese);
    }

    @Override public void toJson(JsonWriter writer, Pizza value) throws IOException {
      writer.beginObject();
      writer.name("size").value(value.diameter);
      writer.name("extra cheese").value(value.extraCheese);
      writer.endObject();
    }
  }

  static class MealDealAdapterFactory implements JsonAdapter.Factory {
    @Override public JsonAdapter<?> create(
        Type type, AnnotatedElement annotations, Moshi moshi) {
      if (!type.equals(MealDeal.class)) return null;

      final JsonAdapter<Pizza> pizzaAdapter = moshi.adapter(Pizza.class);
      final JsonAdapter<String> drinkAdapter = moshi.adapter(String.class);
      return new JsonAdapter<MealDeal>() {
        @Override public MealDeal fromJson(JsonReader reader) throws IOException {
          reader.beginArray();
          Pizza pizza = pizzaAdapter.fromJson(reader);
          String drink = drinkAdapter.fromJson(reader);
          reader.endArray();
          return new MealDeal(pizza, drink);
        }

        @Override public void toJson(JsonWriter writer, MealDeal value) throws IOException {
          writer.beginArray();
          pizzaAdapter.toJson(writer, value.pizza);
          drinkAdapter.toJson(writer, value.drink);
          writer.endArray();
        }
      };
    }
  }

  @Retention(RUNTIME)
  public @interface Uppercase {
  }

  static class UppercaseAdapterFactory implements JsonAdapter.Factory {
    @Override public JsonAdapter<?> create(
        Type type, AnnotatedElement annotations, Moshi moshi) {
      if (!type.equals(String.class)) return null;
      if (!annotations.isAnnotationPresent(Uppercase.class)) return null;

      final JsonAdapter<String> stringAdapter = moshi.nextAdapter(this, String.class, annotations);
      return new JsonAdapter<String>() {
        @Override public String fromJson(JsonReader reader) throws IOException {
          String s = stringAdapter.fromJson(reader);
          return s.toUpperCase(Locale.US);
        }

        @Override public void toJson(JsonWriter writer, String value) throws IOException {
          stringAdapter.toJson(writer, value.toUpperCase());
        }
      };
    }
  }
}
