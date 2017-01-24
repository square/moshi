/*
 * Copyright (C) 2017 Square, Inc.
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
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.fail;

public final class ObjectJsonWriterTest {
  @SuppressWarnings("unchecked")
  @Test public void array() throws Exception {
    ObjectJsonWriter writer = new ObjectJsonWriter();

    writer.beginArray();
    writer.value("s");
    writer.value(1.5d);
    writer.value(true);
    writer.nullValue();
    writer.endArray();

    assertThat((List<Object>) writer.root()).containsExactly("s", 1.5d, true, null);
  }

  @Test public void object() throws Exception {
    ObjectJsonWriter writer = new ObjectJsonWriter();
    writer.setSerializeNulls(true);

    writer.beginObject();
    writer.name("a").value("s");
    writer.name("b").value(1.5d);
    writer.name("c").value(true);
    writer.name("d").nullValue();
    writer.endObject();

    assertThat((Map<?, ?>) writer.root()).containsExactly(
        entry("a", "s"), entry("b", 1.5d), entry("c", true), entry("d", null));
  }

  @Test public void repeatedNameThrows() throws IOException {
    ObjectJsonWriter writer = new ObjectJsonWriter();
    writer.beginObject();
    writer.name("a").value(1L);
    try {
      writer.name("a").value(2L);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage(
          "Map key 'a' has multiple values at path $.a: 1 and 2");
    }
  }
}

