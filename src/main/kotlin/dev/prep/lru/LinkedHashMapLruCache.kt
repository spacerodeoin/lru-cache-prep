package dev.prep.lru

/**
 * The "I know the standard library" answer. `LinkedHashMap(accessOrder = true)` already keeps a
 * doubly linked list through its entries, and `removeEldestEntry` is the eviction hook.
 * `android.util.LruCache` and `androidx.collection.LruCache` (pre-KMP) are built this way.
 *
 * Say this first in an interview, then offer to hand-roll [LruCache] — interviewers usually
 * want the hand-rolled one, but knowing this exists is a senior signal.
 *
 * Trade-offs vs. [LruCache]:
 * - Iteration order is LRU -> MRU (oldest first), the reverse of our [keys] contract.
 * - There is no way to read without bumping recency, so [peek] is O(n). That's a real gap,
 *   and why the hand-rolled version owns its list.
 * - The listener fires from inside `put`, *before* the map has dropped the eldest entry.
 */
class LinkedHashMapLruCache<K : Any, V : Any>(
    val capacity: Int,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    private val map = object : LinkedHashMap<K, V>(16, 0.75f, /* accessOrder = */ true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            val evict = size > capacity
            if (evict) {
                evictions++
                listener.onRemoved(eldest.key, eldest.value, RemovalCause.EVICTED)
            }
            return evict
        }
    }

    override val size: Int get() = map.size

    override fun get(key: K): V? {
        val value = map[key]
        if (value == null) misses++ else hits++
        return value
    }

    // Iterating does not reorder an access-ordered LinkedHashMap; get() would.
    override fun peek(key: K): V? = map.entries.firstOrNull { it.key == key }?.value

    override fun put(key: K, value: V): V? {
        val old = map.put(key, value)
        if (old != null) listener.onRemoved(key, old, RemovalCause.REPLACED)
        return old
    }

    override fun remove(key: K): V? {
        val old = map.remove(key) ?: return null
        listener.onRemoved(key, old, RemovalCause.EXPLICIT)
        return old
    }

    override fun containsKey(key: K): Boolean = map.containsKey(key)

    override fun clear() {
        val removed = map.entries.map { it.key to it.value }
        map.clear()
        removed.forEach { (k, v) -> listener.onRemoved(k, v, RemovalCause.EXPLICIT) }
    }

    override fun keys(): List<K> = map.keys.toList().asReversed()

    override fun stats(): CacheStats = CacheStats(hits, misses, evictions)
}
