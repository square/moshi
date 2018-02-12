package com.squareup.moshi.kotshi

import com.squareup.moshi.MoshiSerializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@MoshiSerializable //(useAdaptersForPrimitives = PrimitiveAdapters.ENABLED)
data class ClassWithDefaultValues(
//        @JsonDefaultValue
    val v1: WithCompanionFunction,
//        @JsonDefaultValue
    val v2: WithStaticFunction,
//        @JsonDefaultValue
    val v3: WithCompanionProperty,
//        @JsonDefaultValue
    val v4: WithStaticProperty,
//        @JsonDefaultValue
    val v5: GenericClassWithDefault<String>,
//        @JsonDefaultValue
    val v6: GenericClassWithDefault<Int>,
//        @JsonDefaultValue
    val v7: LocalDate,
//        @JsonDefaultValue
    val v8: LocalTime,
//        @JsonDefaultValue
    val v9: LocalDateTime,
    val v10: WithCompanionFunction, // No annotations, should not get a default value
    @OtherJsonDefaultValue
    val v11: WithCompanionFunction,
//        @JsonDefaultValue
    val v12: ClassWithConstructorAsDefault,
//        @JsonDefaultValue
    val v13: GenericClassWithConstructorAsDefault<String>,
//        @JsonDefaultValue
    val v14: Int?,
//        @JsonDefaultValue
    val v15: SomeEnum,
//        @JsonDefaultValue
    val v16: Map<String, Int>
)

@MoshiSerializable
data class WithCompanionFunction(val v: String?) {
  companion object {
    //        @JsonDefaultValue
    fun provideDefault(): WithCompanionFunction = WithCompanionFunction("WithCompanionFunction")

    @OtherJsonDefaultValue
    fun provideQualifiedDefault() = WithCompanionFunction("OtherJsonDefaultValue")
  }
}

@MoshiSerializable
data class WithStaticFunction(val v: String?) {
  companion object {
    //        @JsonDefaultValue
    @JvmStatic
    fun provideDefault() = WithStaticFunction("WithStaticFunction")
  }
}

@MoshiSerializable
data class WithCompanionProperty(val v: String?) {
  companion object {
    //        @JsonDefaultValue
    val defaultValue = WithCompanionProperty("WithCompanionProperty")
  }
}

@MoshiSerializable
data class WithStaticProperty(val v: String?) {
  companion object {
    @JvmField
//        @JsonDefaultValue
    val defaultValue = WithStaticProperty("WithStaticProperty")
  }
}

@MoshiSerializable
data class GenericClassWithDefault<out T>(val v: T?) {
  companion object {
    //        @JsonDefaultValue
    fun <T> provideDefault() = GenericClassWithDefault<T>(null)

    //        @JsonDefaultValue
    fun provideIntDefault() = GenericClassWithDefault(4711)
  }
}

object DefaultProvider {
  //    @JsonDefaultValue
  fun provideDefaultLocalDate(): LocalDate = LocalDate.MIN

  //    @JsonDefaultValue
  @JvmStatic
  fun provideDefaultLocalTime(): LocalTime = LocalTime.MIN
}

class OtherDefaultProvider private constructor() {
  //    @JsonDefaultValue
  fun provideDefaultLocalDateTime(): LocalDateTime = LocalDateTime.MIN

  companion object {
    @JvmStatic
    val instance = OtherDefaultProvider()
  }
}

@MoshiSerializable
data class ClassWithConstructorAsDefault(val v: String?) {
  //    @JsonDefaultValue
  constructor() : this("ClassWithConstructorAsDefault")
}

@MoshiSerializable
data class GenericClassWithConstructorAsDefault<T : CharSequence>(val v: T?) {
  //    @JsonDefaultValue
  constructor() : this(null)
}

//@JsonDefaultValue
fun provideIntDefault(): Int? = 4711

//@JsonDefaultValue
fun <K, V> provideDefaultMap() = emptyMap<K, V>()

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
//@JsonDefaultValue
annotation class OtherJsonDefaultValue
