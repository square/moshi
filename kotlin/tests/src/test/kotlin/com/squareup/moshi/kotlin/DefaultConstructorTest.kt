package com.squareup.moshi.kotlin

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.internal.Util
import org.junit.Test

class DefaultConstructorTest {

  @Test fun minimal() {
    val expected = TestClass("requiredClass")
    val args = arrayOf("requiredClass", null, 0, null, 0, 0)
    val mask = Util.createDefaultValuesParametersMask(true, false, false, false, false, false)
    val constructor = Util.lookupDefaultsConstructor(TestClass::class.java)
    val instance = Util.invokeDefaultConstructor(TestClass::class.java, constructor, mask, *args)
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun allSet() {
    val expected = TestClass("requiredClass", "customOptional", 4, "setDynamic", 5, 6)
    val args = arrayOf("requiredClass", "customOptional", 4, "setDynamic", 5, 6)
    val mask = Util.createDefaultValuesParametersMask(true, true, true, true, true, true)
    val constructor = Util.lookupDefaultsConstructor(TestClass::class.java)
    val instance = Util.invokeDefaultConstructor(TestClass::class.java, constructor, mask, *args)
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun customDynamic() {
    val expected = TestClass("requiredClass", "customOptional")
    val args = arrayOf("requiredClass", "customOptional", 0, null, 0, 0)
    val mask = Util.createDefaultValuesParametersMask(true, true, false, false, false, false)
    val constructor = Util.lookupDefaultsConstructor(TestClass::class.java)
    val instance = Util.invokeDefaultConstructor(TestClass::class.java, constructor, mask, *args)
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun minimal_codeGen() {
    val expected = TestClass("requiredClass")
    val json = """{"required":"requiredClass"}"""
    val instance = Moshi.Builder().build().adapter<TestClass>(TestClass::class.java)
        .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun allSet_codeGen() {
    val expected = TestClass("requiredClass", "customOptional", 4, "setDynamic", 5, 6)
    val json = """{"required":"requiredClass","optional":"customOptional","optional2":4,"dynamicSelfReferenceOptional":"setDynamic","dynamicOptional":5,"dynamicInlineOptional":6}"""
    val instance = Moshi.Builder().build().adapter<TestClass>(TestClass::class.java)
        .fromJson(json)!!
    check(instance == expected) {
      "No match:\nActual  : $instance\nExpected: $expected"
    }
  }

  @Test fun customDynamic_codeGen() {
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

private fun createInt(): Int {
  return 3
}

@Suppress("NOTHING_TO_INLINE")
private inline fun createInlineInt(): Int {
  return 3
}
