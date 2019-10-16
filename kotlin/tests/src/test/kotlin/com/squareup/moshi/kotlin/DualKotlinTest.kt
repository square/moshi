package com.squareup.moshi.kotlin

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonAdapter.Factory
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.adapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Parameterized tests that test serialization with both [KotlinJsonAdapterFactory] and code gen.
 */
@RunWith(Parameterized::class)
class DualKotlinTest(useReflection: Boolean) {

  companion object {
    @Parameters(name = "reflective={0}")
    @JvmStatic
    fun parameters(): List<Array<*>> {
      return listOf(
          arrayOf(true),
          arrayOf(false)
      )
    }
  }

  @Suppress("UNCHECKED_CAST")
  private val moshi = Moshi.Builder()
      .apply {
        if (useReflection) {
          add(KotlinJsonAdapterFactory())
          add(object : Factory {
            override fun create(
                type: Type,
                annotations: MutableSet<out Annotation>,
                moshi: Moshi
            ): JsonAdapter<*>? {
              // Prevent falling back to generated adapter lookup
              val rawType = Types.getRawType(type)
              val metadataClass = Class.forName("kotlin.Metadata") as Class<out Annotation>
              check(!rawType.isAnnotationPresent(metadataClass)) {
                "Unhandled Kotlin type in reflective test! $rawType"
              }
              return moshi.nextAdapter<Any>(this, type, annotations)
            }
          })
        }
      }
      .build()


  @Test fun requiredValueAbsent() {
    val jsonAdapter = moshi.adapter<RequiredValueAbsent>()

    try {
      //language=JSON
      jsonAdapter.fromJson("""{"a":4}""")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Required value 'b' missing at $")
    }
  }

  @JsonClass(generateAdapter = true)
  class RequiredValueAbsent(var a: Int = 3, var b: Int)

  @Test fun requiredValueWithDifferentJsonNameAbsent() {
    val jsonAdapter = moshi.adapter<RequiredValueWithDifferentJsonNameAbsent>()

    try {
      //language=JSON
      jsonAdapter.fromJson("""{"a":4}""")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Required value 'b' (JSON name 'bPrime') missing at \$")
    }
  }

  @JsonClass(generateAdapter = true)
  class RequiredValueWithDifferentJsonNameAbsent(var a: Int = 3, @Json(name = "bPrime") var b: Int)

  @Test fun nonNullPropertySetToNullFailsWithJsonDataException() {
    val jsonAdapter = moshi.adapter<HasNonNullProperty>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":null}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Non-null value 'a' was null at \$.a")
    }
  }

  @Test fun nonNullPropertySetToNullFromAdapterFailsWithJsonDataException() {
    val jsonAdapter = moshi.newBuilder()
        .add(object {
          @Suppress("UNUSED_PARAMETER")
          @FromJson
          fun fromJson(string: String): String? = null
        })
        .build()
        .adapter<HasNonNullProperty>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":\"hello\"}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Non-null value 'a' was null at \$.a")
    }
  }

  @JsonClass(generateAdapter = true)
  class HasNonNullProperty {
    var a: String = ""
  }

  @Test fun nonNullPropertyWithJsonNameSetToNullFailsWithJsonDataException() {
    val jsonAdapter = moshi.adapter<HasNonNullPropertyDifferentJsonName>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"aPrime\":null}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Non-null value 'a' (JSON name 'aPrime') was null at \$.aPrime")
    }
  }

  @Test fun nonNullPropertyWithJsonNameSetToNullFromAdapterFailsWithJsonDataException() {
    val jsonAdapter = moshi.newBuilder()
        .add(object {
          @Suppress("UNUSED_PARAMETER")
          @FromJson
          fun fromJson(string: String): String? = null
        })
        .build()
        .adapter<HasNonNullPropertyDifferentJsonName>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"aPrime\":\"hello\"}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Non-null value 'a' (JSON name 'aPrime') was null at \$.aPrime")
    }
  }

  @JsonClass(generateAdapter = true)
  class HasNonNullPropertyDifferentJsonName {
    @Json(name = "aPrime") var a: String = ""
  }

  @Test fun nonNullConstructorParameterCalledWithNullFailsWithJsonDataException() {
    val jsonAdapter = moshi.adapter<HasNonNullConstructorParameter>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":null}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Non-null value 'a' was null at \$.a")
    }
  }

  @Test fun nonNullConstructorParameterCalledWithNullFromAdapterFailsWithJsonDataException() {
    val jsonAdapter = moshi.newBuilder()
        .add(object {
          @Suppress("UNUSED_PARAMETER")
          @FromJson
          fun fromJson(string: String): String? = null
        })
        .build()
        .adapter<HasNonNullConstructorParameter>()

    try {
      //language=JSON
      jsonAdapter.fromJson("{\"a\":\"hello\"}")
      fail()
    } catch (expected: JsonDataException) {
      assertThat(expected).hasMessage("Non-null value 'a' was null at \$.a")
    }
  }

  @Retention(RUNTIME)
  annotation class Nullable

  @JsonClass(generateAdapter = true)
  data class HasNonNullConstructorParameter(val a: String)

  @JsonClass(generateAdapter = true)
  data class HasNullableConstructorParameter(val a: String?)

  @Test fun delegatesToInstalledAdaptersBeforeNullChecking() {
    val localMoshi = moshi.newBuilder()
        .add(object {
          @FromJson
          fun fromJson(@Nullable string: String?): String {
            return string ?: "fallback"
          }

          @ToJson
          fun toJson(@Nullable value: String?): String {
            return value ?: "fallback"
          }
        })
        .build()

    val hasNonNullConstructorParameterAdapter =
        localMoshi.adapter<HasNonNullConstructorParameter>()
    assertThat(hasNonNullConstructorParameterAdapter
        //language=JSON
        .fromJson("{\"a\":null}")).isEqualTo(HasNonNullConstructorParameter("fallback"))

    val hasNullableConstructorParameterAdapter =
        localMoshi.adapter<HasNullableConstructorParameter>()
    assertThat(hasNullableConstructorParameterAdapter
        //language=JSON
        .fromJson("{\"a\":null}")).isEqualTo(HasNullableConstructorParameter("fallback"))
    assertThat(hasNullableConstructorParameterAdapter
        //language=JSON
        .toJson(HasNullableConstructorParameter(null))).isEqualTo("{\"a\":\"fallback\"}")
  }

  @JsonClass(generateAdapter = true)
  data class HasNullableTypeWithGeneratedAdapter(val a: HasNonNullConstructorParameter?)

  @Test fun delegatesToInstalledAdaptersBeforeNullCheckingWithGeneratedAdapter() {
    val adapter = moshi.adapter<HasNullableTypeWithGeneratedAdapter>()

    val encoded = HasNullableTypeWithGeneratedAdapter(null)
    //language=JSON
    assertThat(adapter.toJson(encoded)).isEqualTo("""{}""")
    //language=JSON
    assertThat(adapter.serializeNulls().toJson(encoded)).isEqualTo("""{"a":null}""")

    //language=JSON
    val decoded = adapter.fromJson("""{"a":null}""")!!
    assertThat(decoded.a).isEqualTo(null)
  }

  @Test fun inlineClass() {
    val adapter = moshi.adapter<InlineClass>()

    val inline = InlineClass(5)

    val expectedJson = """{"i":5}"""
    assertThat(adapter.toJson(inline)).isEqualTo(expectedJson)

    val testJson = """{"i":6}"""
    val result = adapter.fromJson(testJson)!!
    assertThat(result.i).isEqualTo(6)
  }

  @JsonClass(generateAdapter = true)
  data class InlineConsumer(val inline: InlineClass)

  @Test fun inlineClassConsumer() {
    val adapter = moshi.adapter<InlineConsumer>()

    val consumer = InlineConsumer(InlineClass(23))

    val expectedJson= """{"inline":{"i":23}}"""
    assertThat(adapter.toJson(consumer)).isEqualTo(expectedJson)

    val testJson = """{"inline":{"i":42}}"""
    val result = adapter.fromJson(testJson)!!
    assertThat(result.inline.i).isEqualTo(42)
  }
}

// Has to be outside since inline classes are only allowed on top level
@JsonClass(generateAdapter = true)
inline class InlineClass(val i: Int)
