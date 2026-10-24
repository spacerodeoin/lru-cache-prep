package dev.prep.lru

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * One behavioural spec, run against every implementation. Add a subclass and you get the whole
 * suite for free — that is also how you'd test a rewrite in an interview ("same contract, new impl").
 */
abstract class CacheContractTest {

    protected val removals = mutableListOf<Triple<String, Int, RemovalCause>>()

    protected abstract fun newCache(
        capacity: Int,
        listener: RemovalListener<String, Int> = RemovalListener { k, v, c -> removals += Triple(k, v, c) },
    ): Cache<String, Int>

    @Test
    fun `miss returns null`() {
        val cache = newCache(2)
        assertNull(cache["a"])
    }

    @Test
    fun `put then get returns value`() {
        val cache = newCache(2)
        cache.put("a", 1)
        assertEquals(1, cache["a"])
        assertEquals(1, cache.size)
    }

    @Test
    fun `evicts least recently used when full`() {
        val cache = newCache(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        assertNull(cache["a"])
        assertEquals(2, cache["b"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size)
    }

    @Test
    fun `get refreshes recency`() {
        val cache = newCache(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache["a"]
        cache.put("c", 3)

        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
    }

    @Test
    fun `peek does not refresh recency`() {
        val cache = newCache(2)
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache.peek("a"))
        cache.put("c", 3)

        assertFalse(cache.containsKey("a"))
    }

    @Test
    fun `containsKey does not refresh recency`() {
        val cache = newCache(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.containsKey("a")
        cache.put("c", 3)

        assertFalse(cache.containsKey("a"))
    }

    @Test
    fun `put on existing key replaces value, refreshes recency and never evicts`() {
        val cache = newCache(2)
        cache.put("a", 1)
        cache.put("b", 2)

        assertEquals(1, cache.put("a", 10))
        assertEquals(2, cache.size)
        assertEquals(listOf("a", "b"), cache.keys())

        cache.put("c", 3)
        assertEquals(listOf("c", "a"), cache.keys())
        assertEquals(10, cache["a"])
    }

    @Test
    fun `put on new key returns null`() {
        val cache = newCache(2)
        assertNull(cache.put("a", 1))
    }

    @Test
    fun `remove returns value and frees a slot`() {
        val cache = newCache(2)
        cache.put("a", 1)
        cache.put("b", 2)

        assertEquals(1, cache.remove("a"))
        assertNull(cache.remove("a"))
        cache.put("c", 3)

        assertEquals(setOf("b", "c"), cache.keys().toSet())
    }

    @Test
    fun `capacity of one keeps only the latest entry`() {
        val cache = newCache(1)
        cache.put("a", 1)
        cache.put("b", 2)

        assertEquals(listOf("b"), cache.keys())
    }

    @Test
    fun `keys are ordered most recently used first`() {
        val cache = newCache(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        cache["a"]

        assertEquals(listOf("a", "c", "b"), cache.keys())
    }

    @Test
    fun `clear empties the cache and reports every entry`() {
        val cache = newCache(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.clear()

        assertEquals(0, cache.size)
        assertEquals(emptyList(), cache.keys())
        assertEquals(
            setOf(Triple("a", 1, RemovalCause.EXPLICIT), Triple("b", 2, RemovalCause.EXPLICIT)),
            removals.toSet(),
        )
    }

    @Test
    fun `listener sees eviction, replacement and explicit removal`() {
        val cache = newCache(1)
        cache.put("a", 1)
        cache.put("a", 2)
        cache.put("b", 3)
        cache.remove("b")

        assertEquals(
            listOf(
                Triple("a", 1, RemovalCause.REPLACED),
                Triple("a", 2, RemovalCause.EVICTED),
                Triple("b", 3, RemovalCause.EXPLICIT),
            ),
            removals,
        )
    }

    @Test
    fun `stats count hits, misses and evictions but not peeks`() {
        val cache = newCache(1)
        cache.put("a", 1)
        cache["a"]
        cache["missing"]
        cache.peek("a")
        cache.put("b", 2)

        assertEquals(CacheStats(hits = 1, misses = 1, evictions = 1), cache.stats())
        assertEquals(0.5, cache.stats().hitRate)
    }

    @Test
    fun `rejects non-positive capacity`() {
        assertThrows<IllegalArgumentException> { newCache(0) }
        assertThrows<IllegalArgumentException> { newCache(-1) }
    }
}

class LruCacheTest : CacheContractTest() {
    override fun newCache(capacity: Int, listener: RemovalListener<String, Int>): Cache<String, Int> =
        LruCache(capacity, listener)
}

class LinkedHashMapLruCacheTest : CacheContractTest() {
    override fun newCache(capacity: Int, listener: RemovalListener<String, Int>): Cache<String, Int> =
        LinkedHashMapLruCache(capacity, listener)
}
