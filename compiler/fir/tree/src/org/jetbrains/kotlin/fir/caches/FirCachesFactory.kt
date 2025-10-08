/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.caches

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import kotlin.time.Duration

abstract class FirCachesFactory : FirSessionComponent {
  /**
   * Creates a cache which returns a value by key on demand if it is computed. Otherwise,
   * computes the value in [createValue] and caches it for future invocations.
   *
   * [FirCache.getValue] should not be called inside [createValue].
   *
   * Note that [createValue] might be called multiple times for the same value, but all
   * threads will always get the same value.
   *
   * Where: [CONTEXT] -- type of value which be used to create value by [createValue].
   *
   * Consider using [org.jetbrains.kotlin.fir.caches.createCache] shortcut if your cache
   * does not need any kind of [CONTEXT] parameter.
   *
   *
   * 요청된 키에 해당하는 값이 이미 계산되어 있다면 그 값을 반환하고, 그렇지 않으면
   * [createValue]에서 값을 계산한 뒤 캐시에 저장하여 이후 호출에서 재사용합니다.
   *
   * [createValue] 내부에서는 [FirCache.getValue]를 호출하면 안 됩니다.
   *
   * 같은 값에 대해 [createValue]가 여러 번 호출될 수 있지만, 모든 스레드는 항상
   * 동일한 값을 얻게 됩니다.
   *
   * 여기서 [CONTEXT]는 [createValue]로 값을 생성할 때 사용되는 타입을 의미합니다.
   *
   * 캐시에 [CONTEXT] 매개변수가 필요하지 않다면, [org.jetbrains.kotlin.fir.caches.createCache]
   * 단축 함수를 사용하는 것을 권장합니다.
   */
  abstract fun <K : Any, V, CONTEXT> createCache(createValue: (K, CONTEXT) -> V): FirCache<K, V, CONTEXT>

  /**
   * Creates a cache which returns a value by key on demand if it is computed. Otherwise,
   * computes the value in [createValue] and caches it for future invocations.
   *
   * [FirCache.getValue] should not be called inside [createValue].
   *
   * Where: [CONTEXT] -- type of value which be used to create value by [createValue].
   *
   * @param initialCapacity initial capacity for the underlying cache map
   * @param loadFactor loadFactor for the underlying cache map
   *
   *
   * 요청된 키에 해당하는 값이 이미 계산되어 있다면 그 값을 반환하고, 그렇지 않으면
   * [createValue]에서 값을 계산한 뒤 캐시에 저장하여 이후 호출에서 재사용합니다.
   *
   * [createValue] 내부에서는 [FirCache.getValue]를 호출하면 안 됩니다.
   *
   * 여기서 [CONTEXT]는 [createValue]로 값을 생성할 때 사용되는 타입을 의미합니다.
   *
   * @param initialCapacity 내부 캐시 맵의 초기 용량입니다.
   * @param loadFactor 내부 캐시 맵의 부하 계수입니다.
   */
  abstract fun <K : Any, V, CONTEXT> createCache(
    initialCapacity: Int,
    loadFactor: Float,
    createValue: (K, CONTEXT) -> V,
  ): FirCache<K, V, CONTEXT>

  /**
   * Creates a cache which returns a caches value on demand if it is computed. Otherwise,
   * computes the value in two phases:
   *
   *  - [createValue] -- creates values and stores value of type [V] to cache and passes
   *                     [V] & [DATA] to [postCompute]
   *  - [postCompute] -- performs some operations on computed value after it placed into map
   *
   * [FirCache.getValue] can be safely called in [postCompute] from the same thread and the
   * correct value computed by [createValue] will be returned.
   *
   * [FirCache.getValue] should not be called inside [createValue].
   *
   * Where:
   *  [CONTEXT] -- type of value which be used to create value by [createValue]
   *  [DATA] -- type of additional data which will be passed from [createValue] to [postCompute]
   *
   *
   * 요청된 키에 해당하는 값이 이미 계산되어 있다면 그 값을 반환하고, 그렇지 않으면 [createValue]에서
   * 두 단계로 값을 계산한 뒤 캐시에 저장합니다.
   *
   * - [createValue]: 값을 생성하고, 생성된 [V]를 캐시에 저장한 뒤 [V]와 [DATA]를 [postCompute]에
   *                  전달합니다.
   * - [postCompute]: 값이 맵에 저장된 후, 계산된 값에 대해 추가 작업을 수행합니다.
   *
   * [postCompute] 내부에서는 동일한 스레드에서 [FirCache.getValue]를 안전하게 호출할 수 있으며,
   * [createValue]로 계산된 올바른 값을 반환합니다.
   *
   * [createValue] 내부에서는 [FirCache.getValue]를 호출하면 안 됩니다.
   *
   * [CONTEXT]: [createValue]를 호출할 때 사용하는 컨텍스트의 타입입니다.
   * [DATA]: [createValue]에서 생성되어 [postCompute]로 전달되는 추가 데이터의 타입입니다.
   */
  abstract fun <K : Any, V, CONTEXT, DATA> createCacheWithPostCompute(
    createValue: (K, CONTEXT) -> Pair<V, DATA>,
    postCompute: (K, V, DATA) -> Unit,
  ): FirCache<K, V, CONTEXT>

  enum class KeyReferenceStrength {
    /**
     * An ordinary strong reference.
     */
    STRONG,

    /**
     * @see java.lang.ref.WeakReference
     */
    WEAK,
  }

  enum class ValueReferenceStrength {
    /**
     * An ordinary strong reference.
     */
    STRONG,

    /**
     * @see java.lang.ref.SoftReference
     */
    SOFT,

    /**
     * @see java.lang.ref.WeakReference
     */
    WEAK,
  }

  /**
   * Creates a cache which returns a value by key on demand if it is computed. Otherwise,
   * computes the value in [createValue] and caches it for future invocations.
   *
   * [FirCache.getValue] should not be called inside [createValue].
   *
   * The cache may be limited in various dimensions, such as time, size, and the choice of
   * references. Limits should be understood as *suggestions*. Whether the suggested limit
   * is applied is up to the cache factory implementation. Hence, it is legal for a cache
   * factory to construct an entirely unlimited cache.
   *
   * Where: [CONTEXT] -- type of value which be used to create value by [createValue].
   *
   * @param expirationAfterAccess The cache evicts entries after they haven't been accessed
   *  for a set amount of time. The cache is not required to register scheduled maintenance,
   *  so expiration of cache entries may require active cache access.
   * @param maximumSize If the cache exceeds the maximum size, it evicts entries based on a
   *  least-usage strategy.
   * @param keyStrength The strength of the key reference.
   * @param valueStrength The strength of the value reference.
   *
   *
   * 요청된 키에 해당하는 값이 이미 계산되어 있다면 그 값을 반환하고, 그렇지 않으면
   * [createValue]에서 값을 계산한 뒤 캐시에 저장하여 이후 호출에서 재사용합니다.
   *
   * [createValue] 내부에서는 [FirCache.getValue]를 호출하면 안 됩니다.
   *
   * 이 캐시는 시간, 크기, 참조 방식 등 다양한 기준으로 제한될 수 있습니다. 다만 이러한
   * 제한은 권장사항일 뿐이며, 실제로 제한을 적용할지는 캐시 팩토리 구현에 따라 달라집니다.
   * 따라서 제한이 전혀 없는 캐시를 생성하는 구현도 허용됩니다.
   *
   * [CONTEXT]: [createValue]를 호출할 때 사용하는 컨텍스트의 타입입니다.
   *
   * @param expirationAfterAccess 지정된 시간 동안 접근되지 않은 항목을 캐시에서 제거합니다.
   *  이 캐시는 주기적인 유지보수를 등록하지 않을 수도 있으므로, 항목의 만료는 캐시에
   *  접근할 때 발생할 수 있습니다.
   * @param maximumSize 캐시 크기가 지정된 최대 크기를 초과하면, 사용 빈도에 따라 항목을
   *  제거합니다.
   * @param keyStrength 키 참조의 강도입니다.
   * @param valueStrength 값 참조의 강도입니다.
   */
  abstract fun <K : Any, V, CONTEXT> createCacheWithSuggestedLimits(
    expirationAfterAccess: Duration? = null,
    maximumSize: Long? = null,
    keyStrength: KeyReferenceStrength = KeyReferenceStrength.STRONG,
    valueStrength: ValueReferenceStrength = ValueReferenceStrength.STRONG,
    createValue: (K, CONTEXT) -> V,
  ): FirCache<K, V, CONTEXT>

  abstract fun <V> createLazyValue(createValue: () -> V): FirLazyValue<V>

  /**
   * Creates a [FirLazyValue] which possibly references its value softly. If the referenced
   * value is garbage-collected, it will be recomputed with the [createValue] function.
   *
   * The lazy value doesn't make any guarantees regarding the number of invocations of
   * [createValue] or the threads it is invoked in.
   *
   * Whether the lazy value actually references its value softly depends on the cache factory
   * implementation. The cache factory may create a lazy value which strongly references its
   * value.
   *
   *
   * [createValue] 함수를 사용해 값을 계산하며, 계산된 값을 소프트 참조로 보관할 수 있는
   * [FirLazyValue]를 생성합니다. 참조된 값이 가비지 컬렉션으로 제거되면, [createValue]를 다시
   * 호출해 값을 재계산합니다.
   *
   * 이 lazy 값은 [createValue]가 몇 번 호출되는지나, 어떤 스레드에서 호출되는지에 대해
   * 아무런 보장을 하지 않습니다.
   *
   * 실제로 값이 소프트 참조로 저장되는지는 캐시 팩토리 구현에 따라 달라집니다. 일부 캐시
   * 팩토리는 값을 강한 참조로 유지하는 lazy 값을 생성할 수도 있습니다.
   */
  abstract fun <V> createPossiblySoftLazyValue(createValue: () -> V): FirLazyValue<V>

  @RequiresOptIn("This API is performance wise and should not be used in general code")
  annotation class PerformanceWise

  @PerformanceWise
  abstract val isThreadSafe: Boolean
}

val FirSession.firCachesFactory: FirCachesFactory by FirSession.sessionComponentAccessor()

inline fun <K : Any, V> FirCachesFactory.createCache(
  crossinline createValue: (K) -> V,
): FirCache<K, V, Nothing?> = createCache(
  createValue = { key, _ -> createValue(key) },
)

/**
 * @see FirCachesFactory.createCacheWithSuggestedLimits
 */
inline fun <K : Any, V> FirCachesFactory.createCacheWithSuggestedLimits(
  expirationAfterAccess: Duration? = null,
  maximumSize: Long? = null,
  keyHardness: FirCachesFactory.KeyReferenceStrength = FirCachesFactory.KeyReferenceStrength.STRONG,
  valueHardness: FirCachesFactory.ValueReferenceStrength = FirCachesFactory.ValueReferenceStrength.STRONG,
  crossinline createValue: (K) -> V,
): FirCache<K, V, Nothing?> =
  createCacheWithSuggestedLimits(expirationAfterAccess, maximumSize, keyHardness, valueHardness) { key, _ -> createValue(key) }
