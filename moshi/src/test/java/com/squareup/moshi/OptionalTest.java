/*
 * Copyright (C) 2021 Square, Inc.
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
package com.squareup.moshi;

import static com.google.common.truth.Truth.assertThat;

import com.squareup.moshi.adapters.OptionalJsonAdapter;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;

public final class OptionalTest {

  private final Moshi moshi = new Moshi.Builder().add(OptionalJsonAdapter.FACTORY).build();

  @Test
  public void adapterBehavior() throws IOException {
    JsonAdapter<Optional<String>> adapter =
        moshi.adapter(Types.newParameterizedType(Optional.class, String.class));
    assertThat(adapter.fromJson("\"foo\"")).isEqualTo(Optional.of("foo"));
    assertThat(adapter.fromJson("null")).isEqualTo(Optional.empty());
  }

  @Test
  public void smokeTest() throws IOException {
    JsonAdapter<TestClass> adapter = moshi.adapter(TestClass.class);
    assertThat(adapter.fromJson("{\"optionalString\":\"foo\",\"regular\":\"bar\"}"))
        .isEqualTo(new TestClass(Optional.of("foo"), "bar"));
    assertThat(adapter.fromJson("{\"optionalString\":null,\"regular\":\"bar\"}"))
        .isEqualTo(new TestClass(Optional.empty(), "bar"));
    assertThat(adapter.fromJson("{\"regular\":\"bar\"}"))
        .isEqualTo(new TestClass(Optional.empty(), "bar"));

    assertThat(adapter.toJson(new TestClass(Optional.of("foo"), "bar")))
        .isEqualTo("{\"optionalString\":\"foo\",\"regular\":\"bar\"}");
    assertThat(adapter.toJson(new TestClass(Optional.empty(), "bar")))
        .isEqualTo("{\"regular\":\"bar\"}");
    assertThat(adapter.toJson(new TestClass(Optional.empty(), "bar")))
        .isEqualTo("{\"regular\":\"bar\"}");
  }

  static final class TestClass {

    Optional<String> optionalString;
    String regular;

    public TestClass(Optional<String> optionalString, String regular) {
      this.optionalString = optionalString;
      this.regular = regular;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TestClass testClass = (TestClass) o;
      return optionalString.equals(testClass.optionalString)
          && Objects.equals(regular, testClass.regular);
    }

    @Override
    public int hashCode() {
      return Objects.hash(optionalString, regular);
    }

    @Override
    public String toString() {
      return "TestClass{"
          + "optionalString="
          + optionalString
          + ", regular='"
          + regular
          + '\''
          + '}';
    }
  }
}
