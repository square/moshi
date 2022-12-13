package com.squareup.moshi.kotlin.inline

import org.intellij.lang.annotations.Language

val jvmInlineString = JvmInlineString("exampleValue")
val jvmInlineInt = JvmInlineInt(10)
val jvmInlineDouble = JvmInlineDouble(0.5)
val exampleNestedClass = JvmInlineComplexClass.ExampleNestedClass(
  stringValue = "a string",
  intValue = 10
)
val jvmInlineComplexClass = JvmInlineComplexClass(value = exampleNestedClass)
val jvmInlineListInt = JvmInlineListInt(list = listOf(0, 2, 99))
val jvmInlineMapStringNullableInt = JvmInlineMapStringNullableInt(mapOf("first" to 1, "missing" to null))
val jvmInlineMapComplexClass = JvmInlineMapComplexClass(mapOf("key" to jvmInlineComplexClass))
val jvmInlineComplexClassWithParameterizedField = JvmInlineComplexClassWithParameterizedField(
  value = JvmInlineComplexClassWithParameterizedField.ExampleNestedClassWithParameterizedField(
    strings = listOf("i", "have", "strings"),
    ints = listOf(5, 10)
  )
)

val jvmInlineStringMultipleConstructorUsage = JvmInlineString("base", "Appended")
val jvmInlineNotNullNullableString = JvmInlineNullableString("notNull")
val jvmInlineNullNullableString = JvmInlineNullableString(null)
val jvmInlineUInt = JvmInlineUInt(unsignedValue = 99u)
val dataClassWithUInt = DataClassWithUInt(Int.MAX_VALUE.toUInt() + Short.MAX_VALUE.toUInt())
val dataClassWithULong = DataClassWithULong(Long.MAX_VALUE.toULong() + Int.MAX_VALUE.toUInt())
val dataClassWithUShort = DataClassWithUShort(
  (Short.MAX_VALUE.toUShort() + Byte.MAX_VALUE.toUShort()).toUShort()
)
val dataClassWithUByte = DataClassWithUByte((Byte.MAX_VALUE.toUByte() + 10u).toUByte())
val dataClassWithUIntAndString = DataClassWithUIntAndString(
  stringValue = "foo",
  unsignedValue = dataClassWithUInt.uInt
)

@Language("JSON")
val instanceToJsonStringMap: MutableMap<Any, String> = mutableMapOf(
  jvmInlineString to """"${jvmInlineString.value}"""",
  jvmInlineInt to "${jvmInlineInt.value}",
  jvmInlineDouble to "${jvmInlineDouble.value}",
  jvmInlineComplexClass to
    """{"stringValue":"${jvmInlineComplexClass.value.stringValue}",""" +
    """"intValue":${jvmInlineComplexClass.value.intValue}}""",
  jvmInlineListInt to """[0,2,99]""",
  jvmInlineMapStringNullableInt to """{"first":${jvmInlineMapStringNullableInt.map["first"]},"missing":null}""",
  jvmInlineMapComplexClass to
    """{"key":{"stringValue":"${jvmInlineComplexClass.value.stringValue}",""" +
    """"intValue":${jvmInlineComplexClass.value.intValue}}}""",
  jvmInlineStringMultipleConstructorUsage to """"${jvmInlineStringMultipleConstructorUsage.value}"""",
  jvmInlineNotNullNullableString to """"${jvmInlineNotNullNullableString.value}"""",
  jvmInlineNullNullableString to """null""",
  jvmInlineComplexClassWithParameterizedField to """{"strings":["i","have","strings"],"ints":[5,10]}""",
  jvmInlineUInt to """${jvmInlineUInt.unsignedValue}""",
  dataClassWithULong to """{"uLong":${dataClassWithULong.uLong}}""",
  dataClassWithUInt to """{"uInt":${dataClassWithUInt.uInt}}""",
  dataClassWithUShort to """{"uShort":${dataClassWithUShort.uShort}}""",
  dataClassWithUByte to """{"uByte":${dataClassWithUByte.uByte}}""",
  dataClassWithUIntAndString to
    """{"stringValue":"${dataClassWithUIntAndString.stringValue}",""" +
    """"unsignedValue":${dataClassWithUIntAndString.unsignedValue}}"""
)
