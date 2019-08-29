package com.squareup.moshi.kotlin

import com.squareup.moshi.internal.Util
import org.junit.Test

class DefaultConstructorTest {

  @Test fun minimal() {
    val expected = TestClass("requiredClass")
    val args = arrayOf("requiredClass", null, 0, null, 0, 0)
    val argPresentValues = booleanArrayOf(true, false, false, false, false, false)
    val instance = Util.invokeDefaultConstructor(TestClass::class.java, args, argPresentValues)
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun customDynamic() {
    val expected = TestClass("requiredClass", "customOptional", 4, "setDynamic", 5, 6)
    val args = arrayOf("requiredClass", "customOptional", 4, "setDynamic", 5, 6)
    val argPresentValues = booleanArrayOf(true, true, true, true, true, true)
    val instance = Util.invokeDefaultConstructor(TestClass::class.java, args, argPresentValues)
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun allSet() {
    val expected = TestClass("requiredClass", "customOptional")
    val args = arrayOf("requiredClass", "customOptional", 0, null, 0, 0)
    val argPresentValues = booleanArrayOf(true, true, false, false, false, false)
    val instance = Util.invokeDefaultConstructor(TestClass::class.java, args, argPresentValues)
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }
}

data class TestClass(
    val required: String,
    val optional: String = "optional",
    val optional2: Int = 2,
    val dynamicSelfReferenceOptional: String = required,
    val dynamicOptional: Int = createInt(),
    val dynamicInlineOptional: Int = createInlineInt()
)

private fun createInt(): Int {
  return 3
}

@Suppress("NOTHING_TO_INLINE")
private inline fun createInlineInt(): Int {
  return 3
}
