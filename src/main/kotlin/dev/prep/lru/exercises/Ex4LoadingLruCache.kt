package dev.prep.lru.exercises

import dev.prep.lru.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class LoadingLruCache<K : Any, V : Any>(
    val capacity: Int,
    private val scope: CoroutineScope,
    private val loader: suspend (K) -> V,
) {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val lock = Any()
    private val cache = LruCache<K, V>(capacity)
    private val inFlight = HashMap<K, Deferred<V>>()

    suspend fun get(key: K): V {
        var created: Deferred<V>? = null
        val deferred = synchronized(lock) {
            cache[key]?.let { return it }
            inFlight.getOrPut(key) {
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        val v = loader(key)
                        synchronized(lock) { cache.put(key, v) }
                        v
                    } finally {
                        synchronized(lock) { inFlight.remove(key) }
                    }
                }.also { created = it }
            }
        }
        created?.start()
        return deferred.await()
    }

    fun getIfPresent(key: K): V? = synchronized(lock) { cache.peek(key) }
}
