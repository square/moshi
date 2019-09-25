package com.squareup.moshi.recipes.polymorphism

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.adapter
import com.squareup.moshi.recipes.polymorphism.Type.BooleanType
import com.squareup.moshi.recipes.polymorphism.Type.IntType
import com.squareup.moshi.recipes.polymorphism.Type.VoidType

/**
 * `object` types are useful in cases when receiving empty json objects (`{}`) or cases where its
 * type can be inferred by some delegating adapter that peeks its keys. They should only be used for
 * types that are sentinels and do not actually contain meaningful data.
 *
 * In this example, we have a [FunctionSpec] that defines the signature of a function, and the below
 * [Type] representations that can be used for its return type and parameter types. These are all
 * `object` types, so any contents are skipped in its serialization and only its `type` key is read
 * by the [PolymorphicJsonAdapterFactory] to determine its type.
 */
sealed class Type(val label: String) {
  object VoidType : Type("void")
  object BooleanType : Type("boolean")
  object IntType : Type("int")

  override fun toString() = label
}

data class FunctionSpec(
    val name: String,
    val returnType: Type,
    val parameters: Map<String, Type>
)

fun main() {

  val polymorphicAdapter = PolymorphicJsonAdapterFactory.of(Type::class.java, "type")
      .withSubtype(VoidType::class.java, "void")
      .withSubtype(BooleanType::class.java, "boolean")
      .withSubtype(IntType::class.java, "int")

  val moshi = Moshi.Builder()
      .add(polymorphicAdapter)
      .add(KotlinJsonAdapterFactory())
      .build()

  //language=json
  val json = """
    {
      "name": "tacoFactory",
      "returnType": { "type": "void" },
      "parameters": {
        "param1": { "type": "int" },
        "param2": { "type": "boolean" }
      }
    }
  """.trimIndent()

  val functionSpec = moshi.adapter<FunctionSpec>().fromJson(json)
  checkNotNull(functionSpec)
  println(functionSpec)
  // Prints FunctionSpec(name=tacoFactory, returnType=void, parameters={param1=int, param2=boolean})
}
