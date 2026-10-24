package dev.prep.lru

/**
 * Reference LRU cache: HashMap for O(1) lookup + intrusive doubly linked list for O(1) reordering.
 *
 *     index:  key -> Node ──┐
 *                           ▼
 *     order:  head <-> [MRU] <-> ... <-> [LRU] <-> tail
 *
 * Every operation is O(1). Not thread-safe (see exercise 3).
 */
class LruCache<K : Any, V : Any>(
    val capacity: Int,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val index = HashMap<K, Node<K, V>>()
    private val order = DoublyLinkedList<K, V>()

    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    override val size: Int get() = index.size

    override fun get(key: K): V? {
        val node = index[key]
        if (node == null) {
            misses++
            return null
        }
        hits++
        order.moveToFront(node)
        return node.value
    }

    override fun peek(key: K): V? = index[key]?.value

    override fun put(key: K, value: V): V? {
        val existing = index[key]
        if (existing != null) {
            // Update in place: no allocation, no eviction, size unchanged.
            val old = existing.value
            existing.value = value
            order.moveToFront(existing)
            listener.onRemoved(key, old, RemovalCause.REPLACED)
            return old
        }

        // Make room *before* inserting so we never exceed capacity, even transiently.
        val evicted = if (index.size == capacity) evictEldest() else null

        val node = Node(key, value)
        order.addFirst(node)
        index[key] = node

        // Notify last: the cache is consistent if the listener reads or throws.
        evicted?.let { listener.onRemoved(it.key, it.value, RemovalCause.EVICTED) }
        return null
    }

    override fun remove(key: K): V? {
        val node = index.remove(key) ?: return null
        order.unlink(node)
        listener.onRemoved(node.key, node.value, RemovalCause.EXPLICIT)
        return node.value
    }

    override fun containsKey(key: K): Boolean = index.containsKey(key)

    override fun clear() {
        // Snapshot LRU-first, wipe state, then notify — same "consistent before callbacks" rule.
        val removed = order.mostRecentFirst().toList().asReversed()
        index.clear()
        order.clear()
        removed.forEach { listener.onRemoved(it.key, it.value, RemovalCause.EXPLICIT) }
    }

    override fun keys(): List<K> = order.mostRecentFirst().map { it.key }.toList()

    override fun stats(): CacheStats = CacheStats(hits, misses, evictions)

    private fun evictEldest(): Node<K, V> {
        val eldest = checkNotNull(order.removeLast()) { "evicting from an empty cache" }
        index.remove(eldest.key)
        evictions++
        return eldest
    }
}
