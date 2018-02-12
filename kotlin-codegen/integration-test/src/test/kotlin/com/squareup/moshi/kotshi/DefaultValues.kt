package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@MoshiSerializable //(useAdaptersForPrimitives = PrimitiveAdapters.ENABLED)
data class ClassWithDefaultValues(
    val v1: WithCompanionFunction = WithCompanionFunction("WithCompanionFunction"),
    val v2: WithStaticFunction = WithStaticFunction("WithStaticFunction"),
    val v3: WithCompanionProperty = WithCompanionProperty("WithCompanionProperty"),
    val v4: WithStaticProperty = WithStaticProperty("WithStaticProperty"),
    val v5: GenericClassWithDefault<String> = GenericClassWithDefault<String>(null),
    val v6: GenericClassWithDefault<Int> = GenericClassWithDefault(4711),
    val v7: LocalDate = LocalDate.MIN,
    val v8: LocalTime = LocalTime.MIN,
    val v9: LocalDateTime = LocalDateTime.MIN,
    val v10: WithCompanionFunction, // No annotations, should not get a default value
    val v12: ClassWithConstructorAsDefault = ClassWithConstructorAsDefault(),
    val v13: GenericClassWithConstructorAsDefault<String> = GenericClassWithConstructorAsDefault(),
    val v14: Int? = 4711,
    val v15: SomeEnum = SomeEnum.VALUE3,
    val v16: Map<String, Int> = emptyMap()
)

@MoshiSerializable
data class WithCompanionFunction(val v: String?)

@MoshiSerializable
data class WithStaticFunction(val v: String?)

@MoshiSerializable
data class WithCompanionProperty(val v: String?)

@MoshiSerializable
data class WithStaticProperty(val v: String?)

@MoshiSerializable
data class GenericClassWithDefault<out T>(val v: T?)

@MoshiSerializable
data class ClassWithConstructorAsDefault(val v: String?) {
  constructor() : this("ClassWithConstructorAsDefault")
}

@MoshiSerializable
data class GenericClassWithConstructorAsDefault<T : CharSequence>(val v: T?) {
  constructor() : this(null)
}
