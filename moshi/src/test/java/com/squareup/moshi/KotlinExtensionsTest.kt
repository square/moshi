package com.squareup.moshi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.typeOf

@JsonQualifier
@Retention(RUNTIME)
annotation class TestAnnotation1

@JsonQualifier
@Retention(RUNTIME)
annotation class TestAnnotation2

@TestAnnotation1
@TestAnnotation2
class KotlinExtensionsTest {

  @Test
  fun nextAnnotationsShouldWork() {
    val annotations = KotlinExtensionsTest::class.java.annotations
      .filterTo(mutableSetOf()) {
        it.annotationClass.java.isAnnotationPresent(JsonQualifier::class.java)
      }
    assertEquals(2, annotations.size)
    val next = annotations.nextAnnotations<TestAnnotation2>()
    checkNotNull(next)
    assertEquals(1, next.size)
    assertTrue(next.first() is TestAnnotation1)
  }

  @Test
  fun arrayType() {
    val stringArray = String::class.asArrayType()
    check(stringArray.genericComponentType == String::class.java)

    val stringListType = typeOf<List<String>>()
    val stringListArray = stringListType.asArrayType()
    val expected = Types.arrayOf(Types.newParameterizedType(List::class.java, String::class.java))
    assertEquals(stringListArray, expected)
  }

  @Test
  fun addAdapterInferred() {
    // An adapter that always returns -1
    val customIntdapter = object : JsonAdapter<Int>() {
      override fun fromJson(reader: JsonReader): Int? {
        reader.skipValue()
        return -1
      }

      override fun toJson(writer: JsonWriter, value: Int?) {
        throw NotImplementedError()
      }
    }
    val moshi = Moshi.Builder()
      .addAdapter(customIntdapter)
      .build()

    assertEquals(-1, moshi.adapter<Int>().fromJson("5"))
  }
}
