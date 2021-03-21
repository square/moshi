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
package com.squareup.moshi.kotlin.reflect

import com.google.common.truth.Truth
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Assert
import org.junit.Test

class ArgumentsJsonAdapterTest {

  data class CollectionsDataSingleParameter<T : Int?>(
    val collection: ParametrizedJavaDTO<T>
  )

  data class ResolveNonNullOneParameter(
    val collectionsData: CollectionsDataSingleParameter<Int>
  )

  data class ResolveNullableOneParameter(
    val collectionsData: CollectionsDataSingleParameter<Int?>
  )

  data class CollectionDataDoubleParameter<First : Int?, Second : Int?>(
    val first: ParametrizedJavaDTO<First>,
    val second: ParametrizedJavaDTO<Second>
  )

  data class ResolveNonNullDoubleParameter(
    val collectionsData: CollectionDataDoubleParameter<Int, Int>
  )

  data class ResolveNonNullArray(
    val array: Array<Int>
  )

  @Test
  fun nonNullInnerCollection_nullItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val adapter = moshi.adapter(ResolveNonNullOneParameter::class.java)
    try {
      adapter.fromJson("""{ "collectionsData": { "collection": { "items": [0, 1, 2, 3, null] } } } """)
      Assert.fail("Should have errored due nullable item in non null collection")
    } catch (ex: JsonDataException) {
      Truth.assertThat(ex).hasMessageThat().contains("Unexpected null at \$.collectionsData.collection.items[4]")
    }
  }

  @Test
  fun nonNullInnerCollection_nonNullItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val excepted = ResolveNonNullOneParameter(
      collectionsData = CollectionsDataSingleParameter(
        collection = ParametrizedJavaDTO(listOf(0, 1, 2, 3))
      )
    )
    val adapter = moshi.adapter(ResolveNonNullOneParameter::class.java)
    val resolved = adapter.fromJson("""{ "collectionsData": { "collection": { "items": [0, 1, 2, 3] } } } """)
    Truth.assertThat(resolved).isEqualTo(excepted)
  }

  @Test
  fun nullableInnerCollection_nonNullItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val excepted = ResolveNullableOneParameter(
      collectionsData = CollectionsDataSingleParameter(
        collection = ParametrizedJavaDTO(listOf(0, 1, 2, 3))
      )
    )
    val adapter = moshi.adapter(ResolveNullableOneParameter::class.java)
    val resolved = adapter.fromJson("""{ "collectionsData": { "collection": { "items": [0, 1, 2, 3] } } } """)
    Truth.assertThat(resolved).isEqualTo(excepted)
  }

  @Test
  fun nullableInnerCollection_nullableItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val excepted = ResolveNullableOneParameter(
      collectionsData = CollectionsDataSingleParameter(
        collection = ParametrizedJavaDTO(listOf(0, 1, null, 2, 3))
      )
    )
    val adapter = moshi.adapter(ResolveNullableOneParameter::class.java)
    val resolved = adapter.fromJson("""{ "collectionsData": { "collection": { "items": [0, 1, null, 2, 3] } } } """)
    Truth.assertThat(resolved).isEqualTo(excepted)
  }

  @Test
  fun nonNullInnerDoubleParameterCollection_firstCollectionNullItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val adapter = moshi.adapter(ResolveNonNullDoubleParameter::class.java)
    try {
      adapter.fromJson(
        """{ "collectionsData": {
        |"first": { "items": [0, 1, 2, 3, null] },
        |"second": { "items": [0, 1, 2, 3] }
| } } """.trimMargin()
      )
      Assert.fail("Should have errored due nullable item in non null collection")
    } catch (ex: JsonDataException) {
      Truth.assertThat(ex).hasMessageThat().contains("Unexpected null at \$.collectionsData.first.items[4]")
    }
  }

  @Test
  fun nonNullInnerDoubleParameterCollection_secondCollectionNullItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val adapter = moshi.adapter(ResolveNonNullDoubleParameter::class.java)
    try {
      adapter.fromJson(
        """{ "collectionsData": {
        |"first": { "items": [0, 1, 2, 3] },
        |"second": { "items": [0, 1, null, 2, 3] }
| } } """.trimMargin()
      )
      Assert.fail("Should have errored due nullable item in non null collection")
    } catch (ex: JsonDataException) {
      Truth.assertThat(ex).hasMessageThat().contains("Unexpected null at \$.collectionsData.second.items[2]")
    }
  }

  @ExperimentalStdlibApi
  @Test
  fun nonNullList_nullItem() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<List<Int>>()
    try {
      adapter.fromJson("""[0, 1, null, 2, 3]""".trimMargin())
      Assert.fail("Should have errored due nullable item in non null list")
    } catch (ex: JsonDataException) {
      Truth.assertThat(ex).hasMessageThat().contains("Unexpected null at \$[2]")
    }
  }

  @ExperimentalStdlibApi
  @Test
  fun nullableList_nullItem() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<List<Int?>>()
    val excepted = listOf(0, 1, null, 2, 3)
    val result = adapter.fromJson("""[0, 1, null, 2, 3]""".trimMargin())
    Truth.assertThat(result).isEqualTo(excepted)
  }

  @ExperimentalStdlibApi
  @Test
  fun nonNullMap_nullValue() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<Map<String, String>>()
    try {
      adapter.fromJson("""{ "zero":"0", "first":"1", "second":null }""".trimMargin())
      Assert.fail("Should have errored due nullable item in non null map")
    } catch (ex: JsonDataException) {
      Truth.assertThat(ex).hasMessageThat().contains("Unexpected null at \$.second")
    }
  }

  @ExperimentalStdlibApi
  @Test
  fun nonNullMap_nullKey() {
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<Map<String, String?>>()
    val excepted = mapOf<String?, String?>("zero" to "0", "first" to "1", "second" to null)
    val result = adapter.fromJson("""{ "zero":"0", "first":"1", "second": null }""".trimMargin())
    Truth.assertThat(result).isEqualTo(excepted)
  }

  @ExperimentalStdlibApi
  @Test
  fun nonNullArray_nullItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val adapter = moshi.adapter<ResolveNonNullArray>()
    try {
      adapter.fromJson("""{ "array" : [0, 1, 2, 3, null ] }""".trimMargin())
    } catch (ex: JsonDataException) {
      Truth.assertThat(ex).hasMessageThat().contains("Unexpected null at \$.array[4]")
    }
  }

  @ExperimentalStdlibApi
  @Test
  fun listStarProjectionNonNullList_nullItem() {
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val adapter = moshi.adapter<List<*>>()
    try {
      adapter.fromJson("""[0, 1, 2, 3, null ]""".trimMargin())
    } catch (ex: JsonDataException) {
      Truth.assertThat(ex).hasMessageThat().contains("Unexpected null at \$[4]")
    }
  }
}
