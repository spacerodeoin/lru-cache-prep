package dev.prep.lru.exercises

import dev.prep.lru.RemovalCause
import dev.prep.lru.RemovalListener
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("exercise")
class Ex2ExpiringLruCacheTest {

    private var now = 0L
    private val removals = mutableListOf<Triple<String, Int, RemovalCause>>()

    private fun cache(capacity: Int = 3) = ExpiringLruCache<String, Int>(
        capacity = capacity,
        ttlMillis = 100,
        clock = { now },
        listener = RemovalListener { k, v, c -> removals += Triple(k, v, c) },
    )

    @Test
    fun `entry is live just before ttl`() {
        val cache = cache()
        cache.put("a", 1)
        now = 99

        assertEquals(1, cache["a"])
    }

    @Test
    fun `entry expires exactly at ttl and counts as a miss`() {
        val cache = cache()
        cache.put("a", 1)
        now = 100

        assertNull(cache["a"])
        assertFalse(cache.containsKey("a"))
        assertEquals(listOf(Triple("a", 1, RemovalCause.EXPIRED)), removals)
        assertEquals(1, cache.stats().misses)
        assertEquals(0, cache.stats().hits)
    }

    @Test
    fun `get does not extend the ttl`() {
        val cache = cache()
        cache.put("a", 1)
        now = 50
        cache["a"]
        now = 100

        assertNull(cache["a"])
    }

    @Test
    fun `put resets the write time`() {
        val cache = cache()
        cache.put("a", 1)
        now = 50
        cache.put("a", 2)
        now = 120

        assertEquals(2, cache["a"])
    }

    @Test
    fun `peek and containsKey also respect expiry`() {
        val cache = cache()
        cache.put("a", 1)
        cache.put("b", 2)
        now = 100

        assertNull(cache.peek("a"))
        assertFalse(cache.containsKey("b"))
        assertEquals(2, removals.count { it.third == RemovalCause.EXPIRED })
    }

    @Test
    fun `when full an expired entry is dropped before the live LRU entry`() {
        val cache = cache(capacity = 2)
        cache.put("a", 1)
        now = 50
        cache.put("b", 2)
        now = 60
        cache["a"] // a is now MRU, b is LRU — but a is older by write time
        now = 101  // a expired (101 >= 100), b still live (51 < 100)

        cache.put("c", 3)

        assertEquals(listOf("c", "b"), cache.keys())
        assertTrue(Triple("a", 1, RemovalCause.EXPIRED) in removals)
        assertFalse(removals.any { it.third == RemovalCause.EVICTED })
    }

    @Test
    fun `falls back to LRU eviction when nothing has expired`() {
        val cache = cache(capacity = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        assertEquals(listOf("c", "b"), cache.keys())
        assertEquals(listOf(Triple("a", 1, RemovalCause.EVICTED)), removals)
    }

    @Test
    fun `purgeExpired removes every expired entry and reports the count`() {
        val cache = cache()
        cache.put("a", 1)
        now = 10
        cache.put("b", 2)
        now = 60
        cache.put("c", 3)
        now = 115

        assertEquals(2, cache.purgeExpired())
        assertEquals(1, cache.size)
        assertEquals(listOf("c"), cache.keys())
    }

    @Test
    fun `keys never include expired entries`() {
        val cache = cache()
        cache.put("a", 1)
        now = 50
        cache.put("b", 2)
        now = 120

        assertEquals(listOf("b"), cache.keys())
    }
}
