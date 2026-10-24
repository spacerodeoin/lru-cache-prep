package dev.prep.lru.exercises

import dev.prep.lru.Cache
import dev.prep.lru.CacheStats
import dev.prep.lru.DoublyLinkedList
import dev.prep.lru.Node
import dev.prep.lru.RemovalCause
import dev.prep.lru.RemovalListener

class WeightedLruCache<K : Any, V : Any>(
    val maxWeight: Int,
    private val sizeOf: (K, V) -> Int,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    init {
        require(maxWeight > 0) { "maxWeight must be > 0, was $maxWeight" }
    }

    private val index = HashMap<K, Node<K, V>>()
    private val weights = HashMap<K, Int>()
    private val order = DoublyLinkedList<K, V>()
    private var weight = 0
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    val currentWeight: Int get() = weight

    private fun weigh(key: K, value: V): Int {
        val w = sizeOf(key, value)
        check(w >= 0) { "negative size $w for $key" }
        return w
    }

    fun trimToWeight(target: Int) {
        val evicted = mutableListOf<Node<K, V>>()
        while (weight > target) {
            val n = order.removeLast() ?: break
            index.remove(n.key)
            weight -= weights.remove(n.key)!!
            evictions++
            evicted += n
        }
        evicted.forEach { listener.onRemoved(it.key, it.value, RemovalCause.EVICTED) }
    }

    override val size: Int get() = index.size

    override fun get(key: K): V? {
        val n = index[key] ?: run { misses++; return null }
        hits++
        order.moveToFront(n)
        return n.value
    }

    override fun peek(key: K): V? = index[key]?.value

    override fun put(key: K, value: V): V? {
        val w = weigh(key, value)
        val existing = index[key]
        if (w > maxWeight) {
            if (existing == null) return null
            index.remove(key)
            order.unlink(existing)
            weight -= weights.remove(key)!!
            listener.onRemoved(key, existing.value, RemovalCause.REPLACED)
            return existing.value
        }
        val old: V?
        if (existing != null) {
            old = existing.value
            existing.value = value
            weight += w - weights.put(key, w)!!
            order.moveToFront(existing)
        } else {
            old = null
            val n = Node(key, value)
            order.addFirst(n)
            index[key] = n
            weights[key] = w
            weight += w
        }
        trimToWeight(maxWeight)
        if (old != null) listener.onRemoved(key, old, RemovalCause.REPLACED)
        return old
    }

    override fun remove(key: K): V? {
        val n = index.remove(key) ?: return null
        order.unlink(n)
        weight -= weights.remove(key)!!
        listener.onRemoved(key, n.value, RemovalCause.EXPLICIT)
        return n.value
    }

    override fun containsKey(key: K): Boolean = index.containsKey(key)

    override fun clear() {
        val removed = order.mostRecentFirst().toList()
        index.clear(); weights.clear(); order.clear(); weight = 0
        removed.forEach { listener.onRemoved(it.key, it.value, RemovalCause.EXPLICIT) }
    }

    override fun keys(): List<K> = order.mostRecentFirst().map { it.key }.toList()

    override fun stats(): CacheStats = CacheStats(hits, misses, evictions)
}
