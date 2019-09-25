/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.moshi.internal;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import java.io.IOException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class ObjectJsonAdapterTest {

  @Test public void basic() throws IOException {
    ObjectJsonAdapter<ObjectClass> adapter = new ObjectJsonAdapter<>(ObjectClass.INSTANCE);
    assertThat(adapter.toJson(ObjectClass.INSTANCE)).isEqualTo("{}");
    assertThat(adapter.fromJson("{}")).isSameAs(ObjectClass.INSTANCE);
  }

  @Test public void withJsonContent() throws IOException {
    ObjectJsonAdapter<ObjectClass> adapter = new ObjectJsonAdapter<>(ObjectClass.INSTANCE);
    assertThat(adapter.fromJson("{\"a\":6}")).isSameAs(ObjectClass.INSTANCE);
  }

  @Test public void withJsonContent_failsOnUnknown() throws IOException {
    JsonAdapter<ObjectClass> adapter = new ObjectJsonAdapter<>(ObjectClass.INSTANCE)
        .failOnUnknown();
    try {
      adapter.fromJson("{\"a\":6}");
      fail("Should fail on unknown a name");
    } catch (JsonDataException e) {
      assertThat(e).hasMessageContaining("Cannot skip unexpected NAME");
    }
  }

  static class ObjectClass {
    static final ObjectClass INSTANCE = new ObjectClass();

    private ObjectClass() {

    }
  }

}
