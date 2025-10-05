/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.caches

import kotlin.reflect.KProperty

@RequiresOptIn("This API exposes FIR cache internals. It should not used in production.")
annotation class FirCacheInternals

/**
 * A cache class with an embedded value computation strategy. It uses key [K] and the passed context
 * [CONTEXT] to compute and cache the value [V].
 *
 * **IMPORTANT**: While this cache uses both the key and the context to compute and cache the value [V],
 * it retrieves the cached value using **only the key**, and **ignores the passed context**.
 *
 * Because of that, you cannot use a single unique key with different non-unique contexts.
 * If you do, one of the contexts will be used to compute the cached value, and the others
 * will effectively be ignored.
 *
 * @see FirCachesFactory
 *
 *
 * 값 계산 전략이 내장된 캐시 클래스입니다. 이 클래스는 키 [K]와 전달된 컨텍스트 [CONTEXT]를
 * 사용해 값 [V]를 계산하고 캐시합니다.
 *
 * 중요: 이 캐시는 값 [V]를 계산하고 캐시할 때는 키와 컨텍스트를 모두 사용하지만,
 * 저장된 값을 조회할 때는 오직 키만 사용하며, 전달된 컨텍스트는 무시합니다.
 *
 * 따라서 하나의 고유 키에 서로 다른 비고유 컨텍스트를 함께 사용할 수 없습니다.
 * 그렇게 하면 컨텍스트 중 하나만 사용되어 캐시된 값이 계산되고, 나머지는 무시됩니다.
 *
 * @see FirCachesFactory
 */
abstract class FirCache<in K : Any, out V, in CONTEXT> {
  abstract fun getValue(key: K, context: CONTEXT): V
  abstract fun getValueIfComputed(key: K): V?

  /**
   * Returns a snapshot of all non-null values in the cache. Changes to the cache do
   * not reflect in the resulting collection.
   *
   * 캐시에 있는 모든 null이 아닌 값의 스냅샷을 반환합니다. 캐시에 변경이 발생하더라도,
   * 그 변경은 반환된 컬렉션에 반영되지 않습니다.
   */
  @FirCacheInternals
  abstract val cachedValues: Collection<V>
}

@Suppress("NOTHING_TO_INLINE")
inline fun <K : Any, V> FirCache<K, V, Nothing?>.getValue(key: K): V =
  getValue(key, null)

operator fun <K : Any, V> FirCache<K, V, Nothing>.contains(key: K): Boolean {
  return getValueIfComputed(key) != null
}

abstract class FirLazyValue<out V> {
  abstract fun getValue(): V
}

operator fun <V> FirLazyValue<V>.getValue(thisRef: Any?, property: KProperty<*>): V {
  return getValue()
}
