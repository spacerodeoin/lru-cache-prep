package dev.prep.lru.exercises

import dev.prep.lru.Cache
import dev.prep.lru.CacheStats
import dev.prep.lru.RemovalListener

/**
 * EXERCISE 1 — bound by weight, not entry count.
 *
 * This is what `android.util.LruCache.sizeOf` is for: a bitmap cache bounded to 1/8 of the
 * app's memory class, where each entry weighs `bitmap.allocationByteCount` bytes.
 *
 * Spec (tests: Ex1WeightedLruCacheTest):
 * - [currentWeight] is the sum of `sizeOf(key, value)` for every held entry.
 * - After any [put], `currentWeight <= maxWeight`; evict LRU entries until it holds.
 *   One big insert may evict several entries.
 * - `sizeOf` returning a negative number is a programming error: throw IllegalStateException.
 * - An entry heavier than [maxWeight] on its own is NOT cached. If the key already had a value,
 *   that old value must be dropped (cause REPLACED) — otherwise `get` returns a stale value.
 * - Replacing a value re-weighs the entry (the new value may be bigger or smaller).
 * - [trimToWeight] evicts LRU entries until `currentWeight <= target` (think `onTrimMemory`).
 *
 * Hint: you can reuse `internal` DoublyLinkedList/Node from the core package — they're in the
 * same module. Store the weight on the node, or in a side map; argue which in the interview.
 */
class WeightedLruCache<K : Any, V : Any>(
    val maxWeight: Int,
    private val sizeOf: (K, V) -> Int,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    init {
        require(maxWeight > 0) { "maxWeight must be > 0, was $maxWeight" }
    }

    val currentWeight: Int get() = TODO("Exercise 1")

    fun trimToWeight(target: Int): Unit = TODO("Exercise 1")

    override val size: Int get() = TODO("Exercise 1")

    override fun get(key: K): V? = TODO("Exercise 1")

    override fun peek(key: K): V? = TODO("Exercise 1")

    override fun put(key: K, value: V): V? = TODO("Exercise 1")

    override fun remove(key: K): V? = TODO("Exercise 1")

    override fun containsKey(key: K): Boolean = TODO("Exercise 1")

    override fun clear(): Unit = TODO("Exercise 1")

    override fun keys(): List<K> = TODO("Exercise 1")

    override fun stats(): CacheStats = TODO("Exercise 1")
}
