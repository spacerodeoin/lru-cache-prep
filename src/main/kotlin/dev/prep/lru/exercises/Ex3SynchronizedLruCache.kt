package dev.prep.lru.exercises

import dev.prep.lru.Cache
import dev.prep.lru.CacheStats
import dev.prep.lru.RemovalListener

/**
 * EXERCISE 3 — make it thread-safe.
 *
 * The trap: `get` looks like a read, but in an LRU it *writes* (it relinks the list).
 * So a ReadWriteLock with `get` under the read lock is wrong. Explain why before you code.
 *
 * Spec (tests: Ex3SynchronizedLruCacheTest):
 * - Safe for concurrent use from many threads; never exceeds [capacity]; stats stay exact.
 * - The [listener] is invoked AFTER the lock is released. A listener that hands work to
 *   another thread which then touches the cache must not deadlock. (android.util.LruCache
 *   calls `entryRemoved` outside its `synchronized` block for the same reason.)
 *
 * Hint: wrapping the core [dev.prep.lru.LruCache] is fine — but its listener fires inside
 * `put`, i.e. inside your lock. Collect removals while locked, dispatch after unlocking.
 *
 * Stretch (talk, don't code): lock striping / segmenting (Guava's old LocalCache),
 * or Caffeine's approach — record reads into a lossy ring buffer and replay them under a
 * tryLock, so reads never block on each other.
 */
class SynchronizedLruCache<K : Any, V : Any>(
    val capacity: Int,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    override val size: Int get() = TODO("Exercise 3")

    override fun get(key: K): V? = TODO("Exercise 3")

    override fun peek(key: K): V? = TODO("Exercise 3")

    override fun put(key: K, value: V): V? = TODO("Exercise 3")

    override fun remove(key: K): V? = TODO("Exercise 3")

    override fun containsKey(key: K): Boolean = TODO("Exercise 3")

    override fun clear(): Unit = TODO("Exercise 3")

    override fun keys(): List<K> = TODO("Exercise 3")

    override fun stats(): CacheStats = TODO("Exercise 3")
}
