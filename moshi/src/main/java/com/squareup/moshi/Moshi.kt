/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi

import com.squareup.moshi.Types.createJsonQualifierImplementation
import com.squareup.moshi.internal.NO_ANNOTATIONS
import com.squareup.moshi.internal.NonNullJsonAdapter
import com.squareup.moshi.internal.NullSafeJsonAdapter
import com.squareup.moshi.internal.canonicalize
import com.squareup.moshi.internal.isAnnotationPresent
import com.squareup.moshi.internal.removeSubtypeWildcard
import com.squareup.moshi.internal.toStringWithAnnotations
import com.squareup.moshi.internal.typesMatch
import java.lang.reflect.Type
import javax.annotation.CheckReturnValue
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * Coordinates binding between JSON values and Java objects.
 *
 * Moshi instances are thread-safe, meaning multiple threads can safely use a single instance
 * concurrently.
 */
public class Moshi internal constructor(builder: Builder) {
  private val factories = buildList {
    addAll(builder.factories)
    addAll(BUILT_IN_FACTORIES)
  }
  private val lastOffset = builder.lastOffset
  private val lookupChainThreadLocal = ThreadLocal<LookupChain>()
  private val adapterCache = LinkedHashMap<Any?, JsonAdapter<*>?>()

  /** Returns a JSON adapter for `type`, creating it if necessary. */
  @CheckReturnValue
  public fun <T> adapter(type: Type): JsonAdapter<T> = adapter(type, NO_ANNOTATIONS)

  @CheckReturnValue
  public fun <T> adapter(type: Class<T>): JsonAdapter<T> = adapter(type, NO_ANNOTATIONS)

  @CheckReturnValue
  public fun <T> adapter(type: Type, annotationType: Class<out Annotation>): JsonAdapter<T> =
    adapter(type, setOf(createJsonQualifierImplementation(annotationType)))

  @CheckReturnValue
  public fun <T> adapter(type: Type, vararg annotationTypes: Class<out Annotation>): JsonAdapter<T> {
    if (annotationTypes.size == 1) {
      return adapter(type, annotationTypes[0])
    }
    val annotations = buildSet(annotationTypes.size) {
      for (annotationType in annotationTypes) {
        add(createJsonQualifierImplementation(annotationType)!!)
      }
    }
    return adapter(type, annotations)
  }

  @CheckReturnValue
  public fun <T> adapter(type: Type, annotations: Set<Annotation>): JsonAdapter<T> =
    adapter(type, annotations, fieldName = null)

  /**
   * @return a [JsonAdapter] for [T], creating it if necessary. Note that while nullability of [T]
   *         itself is handled, nested types (such as in generics) are not resolved.
   */
  @CheckReturnValue
  @ExperimentalStdlibApi
  public inline fun <reified T> adapter(): JsonAdapter<T> = adapter(typeOf<T>())

  /**
   * @return a [JsonAdapter] for [ktype], creating it if necessary. Note that while nullability of
   *         [ktype] itself is handled, nested types (such as in generics) are not resolved.
   */
  @CheckReturnValue
  @ExperimentalStdlibApi
  public fun <T> adapter(ktype: KType): JsonAdapter<T> {
    val adapter = adapter<T>(ktype.javaType)
    return if (adapter is NullSafeJsonAdapter || adapter is NonNullJsonAdapter) {
      // TODO CR - Assume that these know what they're doing? Or should we defensively avoid wrapping for matching nullability?
      adapter
    } else if (ktype.isMarkedNullable) {
      adapter.nullSafe()
    } else {
      adapter.nonNull()
    }
  }

  /**
   * @param fieldName An optional field name associated with this type. The field name is used as a
   * hint for better adapter lookup error messages for nested structures.
   */
  @CheckReturnValue
  public fun <T> adapter(
    type: Type,
    annotations: Set<Annotation>,
    fieldName: String?
  ): JsonAdapter<T> {
    val cleanedType = type.canonicalize().removeSubtypeWildcard()

    // If there's an equivalent adapter in the cache, we're done!
    val cacheKey = cacheKey(cleanedType, annotations)
    synchronized(adapterCache) {
      val result = adapterCache[cacheKey]
      @Suppress("UNCHECKED_CAST")
      if (result != null) return result as JsonAdapter<T>
    }
    var lookupChain = lookupChainThreadLocal.get()
    if (lookupChain == null) {
      lookupChain = LookupChain()
      lookupChainThreadLocal.set(lookupChain)
    }
    var success = false
    val adapterFromCall = lookupChain.push<T>(cleanedType, fieldName, cacheKey)
    try {
      if (adapterFromCall != null) return adapterFromCall

      // Ask each factory to create the JSON adapter.
      for (i in factories.indices) {
        @Suppress("UNCHECKED_CAST") // Factories are required to return only matching JsonAdapters.
        val result = factories[i].create(cleanedType, annotations, this) as JsonAdapter<T>? ?: continue

        // Success! Notify the LookupChain so it is cached and can be used by re-entrant calls.
        lookupChain.adapterFound(result)
        success = true
        return result
      }
      throw IllegalArgumentException("No JsonAdapter for ${type.toStringWithAnnotations(annotations)}")
    } catch (e: IllegalArgumentException) {
      throw lookupChain.exceptionWithLookupStack(e)
    } finally {
      lookupChain.pop(success)
    }
  }

  @CheckReturnValue
  public fun <T> nextAdapter(
    skipPast: JsonAdapter.Factory,
    type: Type,
    annotations: Set<Annotation>
  ): JsonAdapter<T> {
    val cleanedType = type.canonicalize().removeSubtypeWildcard()
    val skipPastIndex = factories.indexOf(skipPast)
    require(skipPastIndex != -1) { "Unable to skip past unknown factory $skipPast" }
    for (i in (skipPastIndex + 1) until factories.size) {
      @Suppress("UNCHECKED_CAST") // Factories are required to return only matching JsonAdapters.
      val result = factories[i].create(cleanedType, annotations, this) as JsonAdapter<T>?
      if (result != null) return result
    }
    throw IllegalArgumentException("No next JsonAdapter for ${cleanedType.toStringWithAnnotations(annotations)}")
  }

  /** Returns a new builder containing all custom factories used by the current instance. */
  @CheckReturnValue
  public fun newBuilder(): Builder {
    val result = Builder()
    // Runs to reuse var names
    run {
      val limit = lastOffset
      for (i in 0 until limit) {
        result.add(factories[i])
      }
    }
    run {
      val limit = factories.size - BUILT_IN_FACTORIES.size
      for (i in lastOffset until limit) {
        result.addLast(factories[i])
      }
    }
    return result
  }

  /** Returns an opaque object that's equal if the type and annotations are equal. */
  private fun cacheKey(type: Type, annotations: Set<Annotation>): Any {
    return if (annotations.isEmpty()) type else listOf(type, annotations)
  }

  public class Builder {
    internal val factories = mutableListOf<JsonAdapter.Factory>()
    internal var lastOffset = 0

    @CheckReturnValue
    @ExperimentalStdlibApi
    public inline fun <reified T> addAdapter(adapter: JsonAdapter<T>): Builder = add(typeOf<T>().javaType, adapter)

    public fun <T> add(type: Type, jsonAdapter: JsonAdapter<T>): Builder = apply {
      add(newAdapterFactory(type, jsonAdapter))
    }

    public fun <T> add(
      type: Type,
      annotation: Class<out Annotation>,
      jsonAdapter: JsonAdapter<T>
    ): Builder = apply {
      add(newAdapterFactory(type, annotation, jsonAdapter))
    }

    public fun add(factory: JsonAdapter.Factory): Builder = apply {
      factories.add(lastOffset++, factory)
    }

    public fun add(adapter: Any): Builder = apply {
      add(AdapterMethodsFactory.get(adapter))
    }

    @Suppress("unused")
    public fun <T> addLast(type: Type, jsonAdapter: JsonAdapter<T>): Builder = apply {
      addLast(newAdapterFactory(type, jsonAdapter))
    }

    @Suppress("unused")
    public fun <T> addLast(
      type: Type,
      annotation: Class<out Annotation>,
      jsonAdapter: JsonAdapter<T>
    ): Builder = apply {
      addLast(newAdapterFactory(type, annotation, jsonAdapter))
    }

    public fun addLast(factory: JsonAdapter.Factory): Builder = apply {
      factories.add(factory)
    }

    @Suppress("unused")
    public fun addLast(adapter: Any): Builder = apply {
      addLast(AdapterMethodsFactory.get(adapter))
    }

    @CheckReturnValue
    public fun build(): Moshi = Moshi(this)
  }

  /**
   * A possibly-reentrant chain of lookups for JSON adapters.
   *
   * We keep track of the current stack of lookups: we may start by looking up the JSON adapter
   * for Employee, re-enter looking for the JSON adapter of HomeAddress, and re-enter again looking
   * up the JSON adapter of PostalCode. If any of these lookups fail we can provide a stack trace
   * with all of the lookups.
   *
   * Sometimes a JSON adapter factory depends on its own product; either directly or indirectly.
   * To make this work, we offer a JSON adapter stub while the final adapter is being computed. When
   * it is ready, we wire the stub to that finished adapter. This is necessary in self-referential
   * object models, such as an `Employee` class that has a `List<Employee>` field for an
   * organization's management hierarchy.
   *
   * This class defers putting any JSON adapters in the cache until the topmost JSON adapter has
   * successfully been computed. That way we don't pollute the cache with incomplete stubs, or
   * adapters that may transitively depend on incomplete stubs.
   */
  internal inner class LookupChain {
    private val callLookups = mutableListOf<Lookup<*>>()
    private val stack = ArrayDeque<Lookup<*>>()
    private var exceptionAnnotated = false

    /**
     * Returns a JSON adapter that was already created for this call, or null if this is the first
     * time in this call that the cache key has been requested in this call. This may return a
     * lookup that isn't yet ready if this lookup is reentrant.
     */
    fun <T> push(type: Type, fieldName: String?, cacheKey: Any): JsonAdapter<T>? {
      // Try to find a lookup with the same key for the same call.
      var i = 0
      val size = callLookups.size
      while (i < size) {
        val lookup = callLookups[i]
        if (lookup.cacheKey == cacheKey) {
          @Suppress("UNCHECKED_CAST")
          val hit = lookup as Lookup<T>
          stack += hit
          return if (hit.adapter != null) hit.adapter else hit
        }
        i++
      }

      // We might need to know about this cache key later in this call. Prepare for that.
      val lookup = Lookup<Any>(type, fieldName, cacheKey)
      callLookups += lookup
      stack += lookup
      return null
    }

    /** Sets the adapter result of the current lookup. */
    fun <T> adapterFound(result: JsonAdapter<T>) {
      @Suppress("UNCHECKED_CAST")
      val currentLookup = stack.last() as Lookup<T>
      currentLookup.adapter = result
    }

    /**
     * Completes the current lookup by removing a stack frame.
     *
     * @param success true if the adapter cache should be populated if this is the topmost lookup.
     */
    fun pop(success: Boolean) {
      stack.removeLast()
      if (!stack.isEmpty()) return
      lookupChainThreadLocal.remove()
      if (success) {
        synchronized(adapterCache) {
          var i = 0
          val size = callLookups.size
          while (i < size) {
            val lookup = callLookups[i]
            val replaced = adapterCache.put(lookup.cacheKey, lookup.adapter)
            if (replaced != null) {
              @Suppress("UNCHECKED_CAST")
              (lookup as Lookup<Any>).adapter = replaced as JsonAdapter<Any>
              adapterCache[lookup.cacheKey] = replaced
            }
            i++
          }
        }
      }
    }

    fun exceptionWithLookupStack(e: IllegalArgumentException): IllegalArgumentException {
      // Don't add the lookup stack to more than one exception; the deepest is sufficient.
      if (exceptionAnnotated) return e
      exceptionAnnotated = true
      val size = stack.size
      if (size == 1 && stack.first().fieldName == null) return e
      val errorMessage = buildString {
        append(e.message)
        for (lookup in stack.asReversed()) {
          append("\nfor ").append(lookup.type)
          if (lookup.fieldName != null) {
            append(' ').append(lookup.fieldName)
          }
        }
      }
      return IllegalArgumentException(errorMessage, e)
    }
  }

  /** This class implements `JsonAdapter` so it can be used as a stub for re-entrant calls. */
  internal class Lookup<T>(val type: Type, val fieldName: String?, val cacheKey: Any) : JsonAdapter<T>() {
    var adapter: JsonAdapter<T>? = null

    override fun fromJson(reader: JsonReader) = withAdapter { fromJson(reader) }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // TODO remove after JsonAdapter is migrated
    override fun toJson(writer: JsonWriter, value: T?) = withAdapter { toJson(writer, value) }

    private inline fun <R> withAdapter(body: JsonAdapter<T>.() -> R): R =
      checkNotNull(adapter) { "JsonAdapter isn't ready" }.body()

    override fun toString() = adapter?.toString() ?: super.toString()
  }

  internal companion object {
    @JvmField
    val BUILT_IN_FACTORIES: List<JsonAdapter.Factory> = buildList(6) {
      add(StandardJsonAdapters)
      add(CollectionJsonAdapter.Factory)
      add(MapJsonAdapter.Factory)
      add(ArrayJsonAdapter.Factory)
      add(RecordJsonAdapter.Factory)
      add(ClassJsonAdapter.FACTORY)
    }

    fun <T> newAdapterFactory(
      type: Type,
      jsonAdapter: JsonAdapter<T>
    ): JsonAdapter.Factory {
      return JsonAdapter.Factory { targetType, annotations, _ ->
        if (annotations.isEmpty() && typesMatch(type, targetType)) jsonAdapter else null
      }
    }

    fun <T> newAdapterFactory(
      type: Type,
      annotation: Class<out Annotation>,
      jsonAdapter: JsonAdapter<T>
    ): JsonAdapter.Factory {
      require(annotation.isAnnotationPresent(JsonQualifier::class.java)) { "$annotation does not have @JsonQualifier" }
      require(annotation.declaredMethods.isEmpty()) { "Use JsonAdapter.Factory for annotations with elements" }
      return JsonAdapter.Factory { targetType, annotations, _ ->
        if (typesMatch(type, targetType) && annotations.size == 1 && annotations.isAnnotationPresent(annotation)) {
          jsonAdapter
        } else {
          null
        }
      }
    }
  }
}
