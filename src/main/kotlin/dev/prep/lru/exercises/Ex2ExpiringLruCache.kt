package dev.prep.lru.exercises

import dev.prep.lru.Cache
import dev.prep.lru.CacheStats
import dev.prep.lru.RemovalListener

/**
 * EXERCISE 2 — LRU + time-to-live (expire-after-write).
 *
 * Android angle: caching API responses (e.g. a driver's HOS clocks) that are only valid for N
 * seconds. Clock is injected so tests never sleep — say this out loud in the interview.
 *
 * Spec (tests: Ex2ExpiringLruCacheTest):
 * - An entry written at time `t` is expired once `clock() - t >= ttlMillis`.
 * - Expiry is lazy: an expired entry found by [get]/[peek]/[containsKey] is removed on the spot,
 *   reported with cause EXPIRED, and treated as absent ([get] counts a miss).
 * - [put] resets the write time. [get] does NOT extend it (expire-after-write, not after-access).
 * - When full, an expired entry is evicted (cause EXPIRED) in preference to the live LRU entry.
 * - [purgeExpired] eagerly removes every expired entry and returns how many it removed.
 * - [size] may include expired-but-not-yet-purged entries. [keys] must not.
 *
 * Hint: the LRU list orders by *access*, expiry orders by *write*. Those are different orders.
 * Is an O(n) scan on a full put acceptable? What would you use to make it O(log n) or O(1)?
 */
class ExpiringLruCache<K : Any, V : Any>(
    val capacity: Int,
    val ttlMillis: Long,
    private val clock: () -> Long,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
        require(ttlMillis > 0) { "ttlMillis must be > 0, was $ttlMillis" }
    }

    fun purgeExpired(): Int = TODO("Exercise 2")

    override val size: Int get() = TODO("Exercise 2")

    override fun get(key: K): V? = TODO("Exercise 2")

    override fun peek(key: K): V? = TODO("Exercise 2")

    override fun put(key: K, value: V): V? = TODO("Exercise 2")

    override fun remove(key: K): V? = TODO("Exercise 2")

    override fun containsKey(key: K): Boolean = TODO("Exercise 2")

    override fun clear(): Unit = TODO("Exercise 2")

    override fun keys(): List<K> = TODO("Exercise 2")

    override fun stats(): CacheStats = TODO("Exercise 2")
}
