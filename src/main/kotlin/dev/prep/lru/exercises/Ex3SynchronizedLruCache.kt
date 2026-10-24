package dev.prep.lru.exercises

import dev.prep.lru.Cache
import dev.prep.lru.CacheStats
import dev.prep.lru.LruCache
import dev.prep.lru.RemovalCause
import dev.prep.lru.RemovalListener

class SynchronizedLruCache<K : Any, V : Any>(
    val capacity: Int,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val lock = Any()
    private val pending = ArrayList<Triple<K, V, RemovalCause>>()
    private val delegate = LruCache<K, V>(capacity) { k, v, c -> pending += Triple(k, v, c) }

    private inline fun <T> locked(block: () -> T): T {
        val (result, drained) = synchronized(lock) {
            val r = block()
            val d = pending.toList()
            pending.clear()
            r to d
        }
        drained.forEach { (k, v, c) -> listener.onRemoved(k, v, c) }
        return result
    }

    override val size: Int get() = synchronized(lock) { delegate.size }
    override fun get(key: K): V? = locked { delegate[key] }
    override fun peek(key: K): V? = locked { delegate.peek(key) }
    override fun put(key: K, value: V): V? = locked { delegate.put(key, value) }
    override fun remove(key: K): V? = locked { delegate.remove(key) }
    override fun containsKey(key: K): Boolean = locked { delegate.containsKey(key) }
    override fun clear() = locked { delegate.clear() }
    override fun keys(): List<K> = locked { delegate.keys() }
    override fun stats(): CacheStats = locked { delegate.stats() }
}
