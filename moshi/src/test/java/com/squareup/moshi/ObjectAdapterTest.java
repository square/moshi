/*
 * Copyright (C) 2015 Square, Inc.
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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Ignore;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public final class ObjectAdapterTest {
  @Test public void toJsonUsesRuntimeType() {
    Delivery delivery = new Delivery();
    delivery.address = "1455 Market St.";
    Pizza pizza = new Pizza();
    pizza.diameter = 12;
    pizza.extraCheese = true;
    delivery.items = Arrays.asList(pizza, "Pepsi");

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(delivery)).isEqualTo("{"
        + "\"address\":\"1455 Market St.\","
        + "\"items\":["
        + "{\"diameter\":12,\"extraCheese\":true},"
        + "\"Pepsi\""
        + "]"
        + "}");
  }

  @Test public void toJsonJavaLangObject() {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(new Object())).isEqualTo("{}");
  }

  @Test public void fromJsonReturnsMapsAndLists() throws Exception {
    Map<Object, Object> delivery = new LinkedHashMap<>();
    delivery.put("address", "1455 Market St.");
    Map<Object, Object> pizza = new LinkedHashMap<>();
    pizza.put("diameter", 12d);
    pizza.put("extraCheese", true);
    delivery.put("items", Arrays.asList(pizza, "Pepsi"));

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.fromJson("{"
        + "\"address\":\"1455 Market St.\","
        + "\"items\":["
        + "{\"diameter\":12,\"extraCheese\":true},"
        + "\"Pepsi\""
        + "]"
        + "}")).isEqualTo(delivery);
  }

  @Test public void fromJsonUsesDoublesForNumbers() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.fromJson("[0, 1]")).isEqualTo(Arrays.asList(0d, 1d));
  }

  @Test public void fromJsonDoesNotFailOnNullValues() throws Exception {
    Map<Object, Object> emptyDelivery = new LinkedHashMap<>();
    emptyDelivery.put("address", null);
    emptyDelivery.put("items", null);

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.fromJson("{\"address\":null, \"items\":null}"))
        .isEqualTo(emptyDelivery);
  }

  @Test public void toJsonCoercesRuntimeTypeForCollections() {
    Collection<String> collection = new AbstractCollection<String>() {
      @Override public Iterator<String> iterator() {
        return Collections.singleton("A").iterator();
      }
      @Override public int size() {
        return 1;
      }
    };

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(collection)).isEqualTo("[\"A\"]");
  }

  @Test public void toJsonCoercesRuntimeTypeForLists() {
    List<String> list = new AbstractList<String>() {
      @Override public String get(int i) {
        return "A";
      }

      @Override public int size() {
        return 1;
      }
    };

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(list)).isEqualTo("[\"A\"]");
  }

  @Test public void toJsonCoercesRuntimeTypeForSets() {
    Set<String> set = new AbstractSet<String>() {
      @Override public Iterator<String> iterator() {
        return Collections.singleton("A").iterator();
      }
      @Override public int size() {
        return 1;
      }
    };

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(set)).isEqualTo("[\"A\"]");
  }

  @Test public void toJsonCoercesRuntimeTypeForMaps() {
    Map<String, Boolean> map = new AbstractMap<String, Boolean>() {
      @Override public Set<Entry<String, Boolean>> entrySet() {
        return Collections.singletonMap("A", true).entrySet();
      }
    };

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(map)).isEqualTo("{\"A\":true}");
  }

  @Test public void toJsonUsesTypeAdapters() {
    Object dateAdapter = new Object() {
      @ToJson Long dateToJson(Date d) {
        return d.getTime();
      }
      @FromJson Date dateFromJson(Long millis) {
        return new Date(millis);
      }
    };
    Moshi moshi = new Moshi.Builder()
        .add(dateAdapter)
        .build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(Arrays.asList(new Date(1), new Date(2)))).isEqualTo("[1,2]");
  }

  /**
   * Confirm that the built-in adapter for Object delegates to user-supplied adapters for JSON value
   * types like strings.
   */
  @Test public void objectAdapterDelegatesStringNamesAndValues() throws Exception {
    JsonAdapter<String> stringAdapter = new JsonAdapter<String>() {
      @Override public String fromJson(JsonReader reader) throws IOException {
        return reader.nextString().toUpperCase(Locale.US);
      }

      @Override public void toJson(JsonWriter writer, @Nullable String value) {
        throw new UnsupportedOperationException();
      }
    };

    Moshi moshi = new Moshi.Builder()
        .add(String.class, stringAdapter)
        .build();
    JsonAdapter<Object> objectAdapter = moshi.adapter(Object.class);
    Map<String, String> value
        = (Map<String, String>) objectAdapter.fromJson("{\"a\":\"b\", \"c\":\"d\"}");
    assertThat(value).containsExactly(new SimpleEntry<>("A", "B"), new SimpleEntry<>("C", "D"));
  }

  /**
   * Confirm that the built-in adapter for Object delegates to any user-supplied adapters for
   * Object. This is necessary to customize adapters for primitives like numbers.
   */
  @Test public void objectAdapterDelegatesObjects() throws Exception {
    JsonAdapter.Factory objectFactory = new JsonAdapter.Factory() {
      @Override public @Nullable JsonAdapter<?> create(
          Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if (type != Object.class) return null;

        final JsonAdapter<Object> delegate = moshi.nextAdapter(this, Object.class, annotations);
        return new JsonAdapter<Object>() {
          @Override public @Nullable Object fromJson(JsonReader reader) throws IOException {
            if (reader.peek() != JsonReader.Token.NUMBER) {
              return delegate.fromJson(reader);
            } else {
              return new BigDecimal(reader.nextString());
            }
          }

          @Override public void toJson(JsonWriter writer, @Nullable Object value) {
            throw new UnsupportedOperationException();
          }
        };
      }
    };

    Moshi moshi = new Moshi.Builder()
        .add(objectFactory)
        .build();
    JsonAdapter<Object> objectAdapter = moshi.adapter(Object.class);
    List<?> value = (List<?>) objectAdapter.fromJson("[0, 1, 2.0, 3.14]");
    assertThat(value).isEqualTo(Arrays.asList(new BigDecimal("0"), new BigDecimal("1"),
        new BigDecimal("2.0"), new BigDecimal("3.14")));
  }

  /** Confirm that the built-in adapter for Object delegates to user-supplied adapters for lists. */
  @Test public void objectAdapterDelegatesLists() throws Exception {
    JsonAdapter<List<?>> listAdapter = new JsonAdapter<List<?>>() {
      @Override public List<?> fromJson(JsonReader reader) throws IOException {
        reader.skipValue();
        return singletonList("z");
      }

      @Override public void toJson(JsonWriter writer, @Nullable List<?> value) {
        throw new UnsupportedOperationException();
      }
    };

    Moshi moshi = new Moshi.Builder()
        .add(List.class, listAdapter)
        .build();
    JsonAdapter<Object> objectAdapter = moshi.adapter(Object.class);
    Map<?, ?> mapOfList = (Map<?, ?>) objectAdapter.fromJson("{\"a\":[\"b\"]}");
    assertThat(mapOfList).isEqualTo(singletonMap("a", singletonList("z")));
  }

  /** Confirm that the built-in adapter for Object delegates to user-supplied adapters for maps. */
  @Test public void objectAdapterDelegatesMaps() throws Exception {
    JsonAdapter<Map<?, ?>> mapAdapter = new JsonAdapter<Map<?, ?>>() {
      @Override public Map<?, ?> fromJson(JsonReader reader) throws IOException {
        reader.skipValue();
        return singletonMap("x", "y");
      }

      @Override public void toJson(JsonWriter writer, @Nullable Map<?, ?> value) {
        throw new UnsupportedOperationException();
      }
    };

    Moshi moshi = new Moshi.Builder()
        .add(Map.class, mapAdapter)
        .build();
    JsonAdapter<Object> objectAdapter = moshi.adapter(Object.class);
    List<?> listOfMap = (List<?>) objectAdapter.fromJson("[{\"b\":\"c\"}]");
    assertThat(listOfMap).isEqualTo(singletonList(singletonMap("x", "y")));
  }

  static class Delivery {
    String address;
    List<Object> items;
  }

  static class Pizza {
    int diameter;
    boolean extraCheese;
  }
}
