package com.squareup.moshi.kotlin.adapters

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert
import org.junit.Test

class PolymorphicJsonAdapterFactoryTest {

  @Test
  fun baseTypeIsInterface() {
    // fix issue: [https://github.com/square/moshi/issues/874]

    val moshi = Moshi.Builder()
      .add(
        PolymorphicJsonAdapterFactory.of(DataItem::class.java, "type")
          .withSubtype(Text::class.java, "text")
          .withSubtype(Image::class.java, "image")
      )
      .addLast(KotlinJsonAdapterFactory())
      .build()

    val adapter = moshi.adapter<List<DataItem>>(
      Types.newParameterizedType(
        MutableList::class.java,
        DataItem::class.java
      )
    )

    val jsonString = adapter.toJson(
      listOf(
        Text("this is text"),
        Image("http://xxx.xxx/xxx.jpg", 1024, 1024)
      )
    )

    Assert.assertEquals(
      """[{"text":"this is text","type":"text"},{"url":"http://xxx.xxx/xxx.jpg","w":1024,"h":1024,"type":"image"}]""",
      jsonString
    )

    val list = adapter.fromJson(jsonString)
    Assert.assertNotNull(list)
    Assert.assertEquals(2, list!!.size)
    assertThat(list[0]).isInstanceOf(Text::class.java)
    assertThat(list[1]).isInstanceOf(Image::class.java)
  }
}


interface DataItem {
  val type: String
}

@JsonClass(generateAdapter = true)
data class Text(
  val text: String
) : DataItem {
  override val type: String = "text"
}

@JsonClass(generateAdapter = true)
data class Image(
  val url: String,
  val w: Int,
  val h: Int
) : DataItem {
  override val type: String = "image"
}
