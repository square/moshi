package com.squareup.moshi.kotlin

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import org.junit.Test

class DefaultConstructorTest {

  @Test fun minimal() {
    val expected = TestClass("requiredClass")
    val json = """{"required":"requiredClass"}"""
    val instance = Moshi.Builder().build().adapter<TestClass>(TestClass::class.java)
        .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun allSet() {
    val expected = TestClass("requiredClass", "customOptional", 4, "setDynamic", 5, 6)
    val json = """{"required":"requiredClass","optional":"customOptional","optional2":4,"dynamicSelfReferenceOptional":"setDynamic","dynamicOptional":5,"dynamicInlineOptional":6}"""
    val instance = Moshi.Builder().build().adapter<TestClass>(TestClass::class.java)
        .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun customDynamic() {
    val expected = TestClass("requiredClass", "customOptional")
    val json = """{"required":"requiredClass","optional":"customOptional"}"""
    val instance = Moshi.Builder().build().adapter<TestClass>(TestClass::class.java)
        .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }
}

@JsonClass(generateAdapter = true)
data class TestClass(
    val required: String,
    val optional: String = "optional",
    val optional2: Int = 2,
    val dynamicSelfReferenceOptional: String = required,
    val dynamicOptional: Int = createInt(),
    val dynamicInlineOptional: Int = createInlineInt()
)

// Regression test for https://github.com/square/moshi/issues/905
// Just needs to compile
@JsonClass(generateAdapter = true)
data class GenericTestClassWithDefaults<T>(
    val input: String = "",
    val genericInput: T
)

private fun createInt(): Int {
  return 3
}

@Suppress("NOTHING_TO_INLINE")
private inline fun createInlineInt(): Int {
  return 3
}
