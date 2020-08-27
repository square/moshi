@file:Suppress("UNUSED", "UNUSED_PARAMETER")

package com.squareup.moshi.kotlin.codegen

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.adapter
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Test

class ComplexGenericsInheritanceTest {

  private val moshi = Moshi.Builder().build()

  @Test
  fun simple() {
    val adapter = moshi.adapter<PersonResponse>()

    @Language("JSON")
    val json = """{"data":{"name":"foo"},"data2":"bar","data3":"baz"}"""

    val instance = adapter.fromJson(json)!!
    val testInstance = PersonResponse().apply {
      data = Person("foo")
    }
    assertThat(instance).isEqualTo(testInstance)
    assertThat(adapter.toJson(instance)).isEqualTo(json)
  }

  @Test
  fun nested() {
    val adapter = moshi.adapter<NestedPersonResponse>()

    @Language("JSON")
    val json = """{"data":{"name":"foo"},"data2":"bar","data3":"baz"}"""

    val instance = adapter.fromJson(json)!!
    val testInstance = NestedPersonResponse().apply {
      data = Person("foo")
    }
    assertThat(instance).isEqualTo(testInstance)
    assertThat(adapter.toJson(instance)).isEqualTo(json)
  }

  @Test
  fun untyped() {
    val adapter = moshi.adapter<UntypedNestedPersonResponse<Person>>()

    @Language("JSON")
    val json = """{"data":{"name":"foo"},"data2":"bar","data3":"baz"}"""

    val instance = adapter.fromJson(json)!!
    val testInstance = UntypedNestedPersonResponse<Person>().apply {
      data = Person("foo")
    }
    assertThat(instance).isEqualTo(testInstance)
    assertThat(adapter.toJson(instance)).isEqualTo(json)
  }

  @Test
  fun complex() {
    val adapter = moshi.adapter<Layer4<Person, UntypedNestedPersonResponse<Person>>>()

    @Language("JSON")
    val json = """{"layer4E":{"name":"layer4E"},"layer4F":{"data":{"name":"layer4F"},"data2":"layer4F","data3":"layer4F"},"layer3C":[1,2,3],"layer3D":"layer3D","layer2":"layer2","layer1":"layer1"}"""

    val instance = adapter.fromJson(json)!!
    val testInstance = Layer4(
        layer4E = Person("layer4E"),
        layer4F = UntypedNestedPersonResponse<Person>().apply {
          data = Person("layer4F")
          data2 = "layer4F"
          data3 = "layer4F"
        }
    ).apply {
      layer3C = listOf(1, 2, 3)
      layer3D = "layer3D"
      layer2 = "layer2"
      layer1 = "layer1"
    }
    assertThat(instance).isEqualTo(testInstance)
    assertThat(adapter.toJson(testInstance)).isEqualTo(json)
  }
}

open class ResponseWithSettableProperty<T, R> {
  var data: T? = null
  var data2: R? = null
  var data3: R? = null
}

interface Personable

@JsonClass(generateAdapter = true)
data class Person(val name: String) : Personable

@JsonClass(generateAdapter = true)
data class PersonResponse(
    val extra: String? = null) : ResponseWithSettableProperty<Person, String>()

abstract class NestedResponse<T : Personable> : ResponseWithSettableProperty<T, String>()

@JsonClass(generateAdapter = true)
data class NestedPersonResponse(val extra: String? = null) : NestedResponse<Person>()

@JsonClass(generateAdapter = true)
data class UntypedNestedPersonResponse<T : Personable>(
    val extra: String? = null
) : NestedResponse<T>()

interface LayerInterface<I>

abstract class Layer1<A> {
  var layer1: A? = null
}

abstract class Layer2<B> : Layer1<B>(), LayerInterface<B> {
  var layer2: B? = null
}

abstract class Layer3<C, D> : Layer2<D>() {
  var layer3C: C? = null
  var layer3D: D? = null
}

@JsonClass(generateAdapter = true)
data class Layer4<E : Personable, F>(
    val layer4E: E,
    val layer4F: F? = null
) : Layer3<List<Int>, String>(), LayerInterface<String>
