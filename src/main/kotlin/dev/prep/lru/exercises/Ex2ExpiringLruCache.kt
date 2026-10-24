package dev.prep.lru.exercises

import dev.prep.lru.Cache
import dev.prep.lru.CacheStats
import dev.prep.lru.DoublyLinkedList
import dev.prep.lru.Node
import dev.prep.lru.RemovalCause
import dev.prep.lru.RemovalListener

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

    private class Entry<V>(val value: V, val writtenAt: Long)

    private val index = HashMap<K, Node<K, Entry<V>>>()
    private val order = DoublyLinkedList<K, Entry<V>>()
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    private fun isExpired(n: Node<K, Entry<V>>) = clock() - n.value.writtenAt >= ttlMillis

    private fun expire(n: Node<K, Entry<V>>) {
        index.remove(n.key)
        order.unlink(n)
        listener.onRemoved(n.key, n.value.value, RemovalCause.EXPIRED)
    }

    private fun liveNode(key: K): Node<K, Entry<V>>? {
        val n = index[key] ?: return null
        if (isExpired(n)) {
            expire(n)
            return null
        }
        return n
    }

    fun purgeExpired(): Int {
        val expired = order.mostRecentFirst().filter(::isExpired).toList()
        expired.forEach(::expire)
        return expired.size
    }

    override val size: Int get() = index.size

    override fun get(key: K): V? {
        val n = liveNode(key) ?: run { misses++; return null }
        hits++
        order.moveToFront(n)
        return n.value.value
    }

    override fun peek(key: K): V? = liveNode(key)?.value?.value

    override fun put(key: K, value: V): V? {
        val existing = liveNode(key)
        if (existing != null) {
            val old = existing.value.value
            existing.value = Entry(value, clock())
            order.moveToFront(existing)
            listener.onRemoved(key, old, RemovalCause.REPLACED)
            return old
        }
        var evicted: Node<K, Entry<V>>? = null
        if (index.size == capacity && purgeExpired() == 0) {
            evicted = order.removeLast()!!
            index.remove(evicted.key)
            evictions++
        }
        val n = Node(key, Entry(value, clock()))
        order.addFirst(n)
        index[key] = n
        evicted?.let { listener.onRemoved(it.key, it.value.value, RemovalCause.EVICTED) }
        return null
    }

    override fun remove(key: K): V? {
        val n = index.remove(key) ?: return null
        order.unlink(n)
        listener.onRemoved(key, n.value.value, RemovalCause.EXPLICIT)
        return n.value.value
    }

    override fun containsKey(key: K): Boolean = liveNode(key) != null

    override fun clear() {
        val removed = order.mostRecentFirst().toList()
        index.clear(); order.clear()
        removed.forEach { listener.onRemoved(it.key, it.value.value, RemovalCause.EXPLICIT) }
    }

    override fun keys(): List<K> = order.mostRecentFirst().filterNot(::isExpired).map { it.key }.toList()

    override fun stats(): CacheStats = CacheStats(hits, misses, evictions)
}
