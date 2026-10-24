package dev.prep.lru.exercises

import dev.prep.lru.RemovalListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Tag("exercise")
class Ex3SynchronizedLruCacheTest {

    @Test
    @Timeout(30)
    fun `hammering from many threads keeps every invariant`() {
        val capacity = 50
        val threads = 8
        val opsPerThread = 20_000
        val cache = SynchronizedLruCache<Int, Int>(capacity)
        val gets = AtomicLong()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)

        val futures = (0 until threads).map { seed ->
            pool.submit {
                val random = Random(seed)
                start.await()
                repeat(opsPerThread) {
                    val key = random.nextInt(200)
                    if (random.nextBoolean()) {
                        cache.put(key, key)
                    } else {
                        gets.incrementAndGet()
                        cache[key]?.let { check(it == key) { "value $it stored under key $key" } }
                    }
                    check(cache.size <= capacity) { "size ${cache.size} exceeded capacity" }
                }
            }
        }
        start.countDown()
        futures.forEach { it.get() } // rethrows anything a worker threw
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))

        val keys = cache.keys()
        assertTrue(cache.size <= capacity)
        assertEquals(cache.size, keys.size)
        assertEquals(keys.size, keys.toSet().size, "duplicate keys mean the list got corrupted")
        val stats = cache.stats()
        assertEquals(gets.get(), stats.hits + stats.misses, "lost stat updates mean a data race")
    }

    @Test
    @Timeout(10)
    fun `listener runs outside the lock`() {
        lateinit var cache: SynchronizedLruCache<String, Int>
        val calls = AtomicInteger()
        val listener = RemovalListener<String, Int> { _, _, _ ->
            calls.incrementAndGet()
            // Another thread touches the cache while we're inside the callback.
            // If the callback runs under the cache lock, that thread blocks and we fail.
            val other = thread(isDaemon = true) { cache.size }
            other.join(1_000)
            check(!other.isAlive) { "listener was invoked while holding the cache lock" }
        }
        cache = SynchronizedLruCache(capacity = 1, listener = listener)

        cache.put("a", 1)
        cache.put("b", 2) // evicts "a"
        cache.put("b", 3) // replaces "b"
        cache.remove("b")

        assertEquals(3, calls.get())
    }
}
