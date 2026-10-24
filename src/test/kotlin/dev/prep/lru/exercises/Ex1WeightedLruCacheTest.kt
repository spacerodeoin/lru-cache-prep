package dev.prep.lru.exercises

import dev.prep.lru.RemovalCause
import dev.prep.lru.RemovalListener
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Tag("exercise")
class Ex1WeightedLruCacheTest {

    private val removals = mutableListOf<Triple<String, String, RemovalCause>>()

    /** Weight = string length, so "xxxx" weighs 4. */
    private fun cache(maxWeight: Int = 10) = WeightedLruCache<String, String>(
        maxWeight = maxWeight,
        sizeOf = { _, v -> v.length },
        listener = RemovalListener { k, v, c -> removals += Triple(k, v, c) },
    )

    private fun w(n: Int) = "x".repeat(n)

    @Test
    fun `tracks current weight`() {
        val cache = cache()
        cache.put("a", w(3))
        cache.put("b", w(2))

        assertEquals(5, cache.currentWeight)
        assertEquals(2, cache.size)
    }

    @Test
    fun `one heavy insert evicts several LRU entries`() {
        val cache = cache()
        cache.put("a", w(4))
        cache.put("b", w(4))
        cache.put("c", w(8))

        assertEquals(listOf("c"), cache.keys())
        assertEquals(8, cache.currentWeight)
        assertEquals(
            listOf(Triple("a", w(4), RemovalCause.EVICTED), Triple("b", w(4), RemovalCause.EVICTED)),
            removals,
        )
    }

    @Test
    fun `get refreshes recency`() {
        val cache = cache()
        cache.put("a", w(4))
        cache.put("b", w(4))
        cache["a"]
        cache.put("c", w(4))

        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
    }

    @Test
    fun `replacing a value re-weighs it and may trigger eviction`() {
        val cache = cache()
        cache.put("a", w(4))
        cache.put("b", w(4))
        cache.put("b", w(8))

        assertEquals(listOf("b"), cache.keys())
        assertEquals(8, cache.currentWeight)
    }

    @Test
    fun `replacing with a lighter value frees weight`() {
        val cache = cache()
        cache.put("a", w(8))
        cache.put("a", w(2))

        assertEquals(2, cache.currentWeight)
    }

    @Test
    fun `oversized entry is not cached and drops the stale value for that key`() {
        val cache = cache()
        cache.put("a", w(2))
        cache.put("b", w(3))

        cache.put("a", w(11))

        assertNull(cache["a"])
        assertFalse(cache.containsKey("a"))
        assertEquals(listOf("b"), cache.keys())
        assertEquals(3, cache.currentWeight)
        assertTrue(Triple("a", w(2), RemovalCause.REPLACED) in removals)
    }

    @Test
    fun `oversized entry for a new key leaves the cache untouched`() {
        val cache = cache()
        cache.put("a", w(2))

        cache.put("z", w(11))

        assertEquals(listOf("a"), cache.keys())
        assertEquals(2, cache.currentWeight)
        assertTrue(removals.isEmpty())
    }

    @Test
    fun `negative sizeOf is a programming error`() {
        val cache = WeightedLruCache<String, Int>(maxWeight = 10, sizeOf = { _, v -> v })
        assertThrows<IllegalStateException> { cache.put("a", -1) }
    }

    @Test
    fun `trimToWeight evicts LRU first until under target`() {
        val cache = cache()
        cache.put("a", w(3))
        cache.put("b", w(3))
        cache.put("c", w(3))

        cache.trimToWeight(4)
        assertEquals(listOf("c"), cache.keys())
        assertEquals(3, cache.currentWeight)

        cache.trimToWeight(0)
        assertEquals(0, cache.size)
        assertEquals(0, cache.currentWeight)
        assertEquals(3, removals.count { it.third == RemovalCause.EVICTED })
    }

    @Test
    fun `remove and clear release weight`() {
        val cache = cache()
        cache.put("a", w(3))
        cache.put("b", w(4))

        assertEquals(w(3), cache.remove("a"))
        assertEquals(4, cache.currentWeight)

        cache.clear()
        assertEquals(0, cache.currentWeight)
        assertEquals(0, cache.size)
    }
}
