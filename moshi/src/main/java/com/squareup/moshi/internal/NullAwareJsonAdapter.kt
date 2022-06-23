package com.squareup.moshi.internal

import com.squareup.moshi.JsonAdapter

public abstract class NullAwareJsonAdapter<T> : JsonAdapter<T?>()
