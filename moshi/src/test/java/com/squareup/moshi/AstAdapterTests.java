/*
 * Copyright (C) 2024 Square, Inc.
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

import com.squareup.moshi.ast.*;
import java.io.IOException;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

@SuppressWarnings({"KotlinInternalInJava", "rawtypes"})
public class AstAdapterTests {

  @Test
  public void toAndFromJson() throws IOException {
    Moshi moshi = new Moshi.Builder().add(JValue.class, new AstAdapter()).build();
    JsonAdapter<JValue> astAdapter = moshi.adapter(JValue.class);

    JObject subject =
        new JObject(
            new JField("alpha", new JString("apple")),
            new JField(
                "beta",
                new JObject(
                    new JField("alpha", new JString("bacon")),
                    new JField("charlie", new JString("i'm a masseuse")))));

    @Language("JSON")
    String jsonValue =
        "{\"alpha\":\"apple\",\"beta\":{\"alpha\":\"bacon\",\"charlie\":\"i'm a masseuse\"}}";

    assertThat(astAdapter.toJson(subject)).isEqualTo(jsonValue);

    JValue<?> fromJson = astAdapter.fromJson(jsonValue);
    assertThat(fromJson).isEqualTo(subject);
  }

  @Test
  public void invalidDocumentTests() throws IOException {
    Moshi moshi = new Moshi.Builder().add(JValue.class, new AstAdapter()).build();
    JsonAdapter<JValue> astAdapter = moshi.adapter(JValue.class).lenient();

    assertThat(astAdapter.fromJson(",.")).isEqualTo(JNothing.INSTANCE);
    assertThat(astAdapter.fromJson("null")).isEqualTo(JNull.INSTANCE);
    assertThat(astAdapter.fromJson("true")).isInstanceOf(JBoolean.class);
    assertThat(astAdapter.fromJson("false")).isInstanceOf(JBoolean.class);
    assertThat(astAdapter.fromJson("0")).isInstanceOf(JInt.class);
    assertThat(astAdapter.fromJson("1")).isInstanceOf(JInt.class);
    assertThat(astAdapter.fromJson("1.0")).isInstanceOf(JDouble.class);
    assertThat(astAdapter.fromJson("\"\"")).isInstanceOf(JString.class);
    assertThat(astAdapter.fromJson("[]")).isInstanceOf(JArray.class);
    assertThat(astAdapter.fromJson("{}")).isInstanceOf(JObject.class);
  }
}
