package dev.prep.lru

/**
 * The public contract every cache in this repo implements.
 *
 * Recency rules (the part interviewers poke at):
 * - [get] and [put] count as a "use" and move the entry to most-recently-used.
 * - [peek], [containsKey] and [keys] are read-only inspections; they never change order.
 *
 * Not thread-safe unless an implementation says otherwise.
 */
interface Cache<K : Any, V : Any> {

    /** Number of entries currently held. */
    val size: Int

    /** Returns the value and marks the entry as most-recently-used, or null on a miss. */
    operator fun get(key: K): V?

    /** Returns the value without touching recency or stats. */
    fun peek(key: K): V?

    /**
     * Inserts or replaces. The entry becomes most-recently-used.
     * May evict the least-recently-used entry to stay within bounds.
     * @return the previous value for [key], or null if there was none.
     */
    fun put(key: K, value: V): V?

    /** Removes the entry. @return the removed value, or null if absent. */
    fun remove(key: K): V?

    fun containsKey(key: K): Boolean

    /** Removes everything. Each entry is reported to the listener as [RemovalCause.EXPLICIT]. */
    fun clear()

    /** Snapshot of keys ordered most-recently-used first. */
    fun keys(): List<K>

    fun stats(): CacheStats
}

/** Why an entry left the cache (or had its value replaced). */
enum class RemovalCause {
    /** Pushed out to respect the size bound. */
    EVICTED,

    /** Removed by [Cache.remove] or [Cache.clear]. */
    EXPLICIT,

    /** Value overwritten by [Cache.put] on an existing key. */
    REPLACED,

    /** Time-to-live elapsed (used by the expiring-cache exercise). */
    EXPIRED,
}

/**
 * Called after the cache's internal state is already consistent, so a listener may safely
 * read the cache. Android's `LruCache.entryRemoved` plays the same role (e.g. recycling a Bitmap).
 */
fun interface RemovalListener<K, V> {
    fun onRemoved(key: K, value: V, cause: RemovalCause)
}

data class CacheStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
) {
    val requests: Long get() = hits + misses
    val hitRate: Double get() = if (requests == 0L) 0.0 else hits.toDouble() / requests
}
