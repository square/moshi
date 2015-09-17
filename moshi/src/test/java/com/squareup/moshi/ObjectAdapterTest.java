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

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class ObjectAdapterTest {
  @Test public void toJsonUsesRuntimeType() throws Exception {
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

  @Test public void toJsonJavaLangObject() throws Exception {
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

  @Test public void toJsonCoercesRuntimeTypeForCollections() throws Exception {
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

  @Test public void toJsonCoercesRuntimeTypeForLists() throws Exception {
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

  @Test public void toJsonCoercesRuntimeTypeForSets() throws Exception {
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

  @Ignore // We don't support raw maps, like Map<Object, Object>. (Even if the keys are strings!)
  @Test public void toJsonCoercesRuntimeTypeForMaps() throws Exception {
    Map<String, Boolean> map = new AbstractMap<String, Boolean>() {
      @Override public Set<Entry<String, Boolean>> entrySet() {
        return Collections.singletonMap("A", true).entrySet();
      }
    };

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Object> adapter = moshi.adapter(Object.class);
    assertThat(adapter.toJson(map)).isEqualTo("{\"A\":true}");
  }

  @Test public void toJsonUsesTypeAdapters() throws Exception {
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

  static class Delivery {
    String address;
    List<Object> items;
  }

  static class Pizza {
    int diameter;
    boolean extraCheese;
  }
}
