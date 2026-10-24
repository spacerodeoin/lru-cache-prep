package dev.prep.lru

// DRILL: rebuild this from memory, talking out loud. Target < 25 min.
// Tests: CacheContractTest (LruCacheTest). README Step 5 if stuck.

class LruCache<K : Any, V : Any>(
    val capacity: Int,
    private val listener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
) : Cache<K, V> {

    override val size: Int get() = TODO("drill")

    override fun get(key: K): V? = TODO("drill")

    override fun peek(key: K): V? = TODO("drill")

    override fun put(key: K, value: V): V? = TODO("drill")

    override fun remove(key: K): V? = TODO("drill")

    override fun containsKey(key: K): Boolean = TODO("drill")

    override fun clear(): Unit = TODO("drill")

    override fun keys(): List<K> = TODO("drill")

    override fun stats(): CacheStats = TODO("drill")
}
