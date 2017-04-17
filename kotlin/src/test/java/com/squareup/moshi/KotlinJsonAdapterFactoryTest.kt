package com.squareup.moshi

import org.junit.Test
import kotlin.test.assertEquals

class KotlinJsonAdapterFactoryTest {

  private val moshi = Moshi.Builder()
      .add(KotlinJsonAdapter.FACTORY)
      .build()

  private data class FooBar(val foo: String, @Json(name = "other_name") val named: String) {
    @Transient val otherProperty = ""
    @delegate:Transient val delegated by lazy { "" }
  }

  @Test fun test() {
    val adapter = moshi.adapter(FooBar::class.java)

    val json = "{\"foo\":\"foo\", \"other_name\": \"other_name\"}"
    val deserialized = adapter.fromJson(json)
    assertEquals("foo", deserialized.foo, "val deserialization without @Json annotation failed.")
    assertEquals("other_name", deserialized.named, "@Json(name = ...) was not respected.")
    assertEquals("", deserialized.otherProperty, "Transient property was not initialized. " +
        "Was the primary constructor called?")
    assertEquals("", deserialized.delegated, "Delegated property was not initialized. " +
        "Was the primary constructor called?")

    assertEquals(deserialized, adapter.fromJson(adapter.toJson(deserialized)),
        "Object should still be equal after serialization and deserialization.")
  }

  private data class TransientProperty(@Transient val nonBacking: String)

  @Test(expected = IllegalArgumentException::class) fun testTransient() {
    moshi.adapter(TransientProperty::class.java)
  }

  private open class Superclass {
    var x: String = ""
  }
  private data class NonTransientNonConstructorProperty(val ignored: String): Superclass() {
    var y: String = ""
  }

  @Test() fun testNonTransientNonConstructorProperty() {
    val adapter = moshi.adapter(NonTransientNonConstructorProperty::class.java)
    val test = adapter.fromJson("{\"ignored\": \"\", \"x\": \"\", \"y\": \"\"}")
    assertEquals("", test.x, "Non-constructor parameter from superclass should be deserialized correctly.")
    assertEquals("", test.y, "Non-constructor parameter should be deserialized correctly.")
    assertEquals(test, adapter.fromJson(adapter.toJson(test)), "Object should still be equal after serialization and deserialization.")
  }

  // def can be transient since a default value is provided
  data class DefaultParam(val x: String, @Transient val def: String = "")

  @Test fun testDefaultParam() {
    val json = "{\"x\":\"x\"}"
    val deserialized = moshi.adapter(DefaultParam::class.java).fromJson(json)
    assertEquals("x", deserialized.x, "Existing value should be used from JSON.")
    assertEquals("", deserialized.def, "Default value from constructor should be used.")
  }

  @Test(expected = JsonDataException::class) fun testMissingJsonValue() {
    val json = "{}"
    moshi.adapter(DefaultParam::class.java).fromJson(json)
  }

  data class Inner(val x: String)
  data class Outer(val inner: Inner)

  @Test fun testInnerOuter() {
    val json = "{\"inner\": {\"x\": \"\"}}"
    val deserialized = moshi.adapter(Outer::class.java).fromJson(json)
    assertEquals("", deserialized.inner.x, "Nested objects should be deserialized corretly.")
  }
}
