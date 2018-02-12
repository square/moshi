package com.squareup.moshi.kotshi

import com.squareup.moshi.Json
import com.squareup.moshi.MoshiSerializable

abstract class SuperClass {
  val someSuperProperty: String = "Hello"
  abstract val abstractProperty: String
}

@MoshiSerializable
data class TestClass(
    val string: String,
    val nullableString: String?,
    @JvmField
    val integer: Int,
    val nullableInt: Int?,
    val isBoolean: Boolean,
    val isNullableBoolean: Boolean?,
    val aShort: Short,
    val nullableShort: Short?,
    val aByte: Byte,
    val nullableByte: Byte?,
    val aChar: Char,
    val nullableChar: Char?,
    val list: List<String>,
    val nestedList: List<Map<String, Set<String>>>,
    override val abstractProperty: String,
    @Json(name = "other_name")
    val customName: String,
    @Hello
    val annotated: String,
    @Hello
    val anotherAnnotated: String,
    val genericClass: GenericClass<String, List<String>>
) : SuperClass() {

  val constantProperty: String = "Hello"

  val virtualProperty: String
    get() = "Fake"

  val delegatedProperty: String by lazy { "Lazy" }
}
