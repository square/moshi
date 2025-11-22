package com.squareup.moshi.kotlin.reflect

/** A simple [Map] that uses parameter indexes instead of sorting or hashing. */
internal class IndexedParameterMap(
  private val parameterKeys: List<KtParameter>,
  private val parameterValues: Array<Any?>,
) : AbstractMutableMap<KtParameter, Any?>() {

  override fun put(key: KtParameter, value: Any?): Any? = null

  override val entries: MutableSet<MutableMap.MutableEntry<KtParameter, Any?>>
    get() {
      val allPossibleEntries =
        parameterKeys.mapIndexed { index, value ->
          SimpleEntry<KtParameter, Any?>(value, parameterValues[index])
        }
      return allPossibleEntries.filterTo(mutableSetOf()) { it.value !== ABSENT_VALUE }
    }

  override fun containsKey(key: KtParameter) = parameterValues[key.index] !== ABSENT_VALUE

  override fun get(key: KtParameter): Any? {
    val value = parameterValues[key.index]
    return if (value !== ABSENT_VALUE) value else null
  }
}
