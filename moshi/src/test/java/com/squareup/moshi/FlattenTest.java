/*
 * Copyright (C) 2018 Square, Inc.
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

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/** Note that this test makes heavy use of nested blocks, but these are for readability only. */
@RunWith(Parameterized.class)
public final class FlattenTest {
  @Parameter public JsonCodecFactory factory;

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return JsonCodecFactory.factories();
  }

  @Test public void flattenExample() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<List<Integer>> integersAdapter =
        moshi.adapter(Types.newParameterizedType(List.class, Integer.class));

    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    int token = writer.beginFlatten();
    writer.value(1);
    integersAdapter.toJson(writer, Arrays.asList(2, 3, 4));
    writer.value(5);
    writer.endFlatten(token);
    writer.endArray();

    assertThat(factory.json()).isEqualTo("[1,2,3,4,5]");
  }

  @Test public void flattenObject() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    {
      writer.name("a");
      writer.value("aaa");
      int token = writer.beginFlatten();
      {
        writer.beginObject();
        {
          writer.name("b");
          writer.value("bbb");
        }
        writer.endObject();
      }
      writer.endFlatten(token);
      writer.name("c");
      writer.value("ccc");
    }
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{\"a\":\"aaa\",\"b\":\"bbb\",\"c\":\"ccc\"}");
  }

  @Test public void flattenArray() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      writer.value("a");
      int token = writer.beginFlatten();
      {
        writer.beginArray();
        {
          writer.value("b");
        }
        writer.endArray();
      }
      writer.endFlatten(token);
      writer.value("c");
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\",\"b\",\"c\"]");
  }

  @Test public void recursiveFlatten() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      writer.value("a");
      int token1 = writer.beginFlatten();
      {
        writer.beginArray();
        {
          writer.value("b");
          int token2 = writer.beginFlatten();
          {
            writer.beginArray();
            {
              writer.value("c");
            }
            writer.endArray();
          }
          writer.endFlatten(token2);
          writer.value("d");
        }
        writer.endArray();
      }
      writer.endFlatten(token1);
      writer.value("e");
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\",\"b\",\"c\",\"d\",\"e\"]");
  }

  @Test public void flattenMultipleNested() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      writer.value("a");
      int token = writer.beginFlatten();
      {
        writer.beginArray();
        {
          writer.value("b");
        }
        writer.endArray();
        writer.beginArray();
        {
          writer.value("c");
        }
        writer.endArray();
      }
      writer.endFlatten(token);
      writer.value("d");
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\",\"b\",\"c\",\"d\"]");
  }

  @Test public void flattenIsOnlyOneLevelDeep() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      writer.value("a");
      int token = writer.beginFlatten();
      {
        writer.beginArray();
        {
          writer.value("b");
          writer.beginArray();
          {
            writer.value("c");
          }
          writer.endArray();
          writer.value("d");
        }
        writer.endArray();
      }
      writer.endFlatten(token);
      writer.value("e");
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\",\"b\",[\"c\"],\"d\",\"e\"]");
  }

  @Test public void flattenOnlySomeChildren() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      writer.value("a");
      int token = writer.beginFlatten();
      {
        writer.beginArray();
        {
          writer.value("b");
        }
        writer.endArray();
      }
      writer.endFlatten(token);
      writer.beginArray();
      {
        writer.value("c");
      }
      writer.endArray();
      writer.value("d");
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\",\"b\",[\"c\"],\"d\"]");
  }

  @Test public void multipleCallsToFlattenSameNesting() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      writer.value("a");
      int token1 = writer.beginFlatten();
      {
        writer.beginArray();
        {
          writer.value("b");
        }
        writer.endArray();
        int token2 = writer.beginFlatten();
        {
          writer.beginArray();
          {
            writer.value("c");
          }
          writer.endArray();
        }
        writer.endFlatten(token2);
        writer.beginArray();
        {
          writer.value("d");
        }
        writer.endArray();
      }
      writer.endFlatten(token1);
      writer.value("e");
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\",\"b\",\"c\",\"d\",\"e\"]");
  }

  @Test public void deepFlatten() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      int token1 = writer.beginFlatten();
      {
        writer.beginArray();
        {
          int token2 = writer.beginFlatten();
          {
            writer.beginArray();
            {
              int token3 = writer.beginFlatten();
              {
                writer.beginArray();
                {
                  writer.value("a");
                }
                writer.endArray();
              }
              writer.endFlatten(token3);
            }
            writer.endArray();
          }
          writer.endFlatten(token2);
        }
        writer.endArray();
      }
      writer.endFlatten(token1);
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[\"a\"]");
  }

  @Test public void flattenTopLevel() {
    JsonWriter writer = factory.newWriter();
    try {
      writer.beginFlatten();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Nesting problem.");
    }
  }

  @Test public void flattenDoesNotImpactOtherTypesInObjects() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginObject();
    {
      int token = writer.beginFlatten();
      writer.name("a");
      writer.beginArray();
      writer.value("aaa");
      writer.endArray();
      writer.beginObject();
      {
        writer.name("b");
        writer.value("bbb");
      }
      writer.endObject();
      writer.name("c");
      writer.beginArray();
      writer.value("ccc");
      writer.endArray();
      writer.endFlatten(token);
    }
    writer.endObject();
    assertThat(factory.json()).isEqualTo("{\"a\":[\"aaa\"],\"b\":\"bbb\",\"c\":[\"ccc\"]}");
  }

  @Test public void flattenDoesNotImpactOtherTypesInArrays() throws Exception {
    JsonWriter writer = factory.newWriter();
    writer.beginArray();
    {
      int token = writer.beginFlatten();
      {
        writer.beginObject();
        {
          writer.name("a");
          writer.value("aaa");
        }
        writer.endObject();
        writer.beginArray();
        {
          writer.value("bbb");
        }
        writer.endArray();
        writer.value("ccc");
        writer.beginObject();
        {
          writer.name("d");
          writer.value("ddd");
        }
        writer.endObject();
      }
      writer.endFlatten(token);
    }
    writer.endArray();
    assertThat(factory.json()).isEqualTo("[{\"a\":\"aaa\"},\"bbb\",\"ccc\",{\"d\":\"ddd\"}]");
  }
}
