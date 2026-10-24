package dev.prep.lru.exercises

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("exercise")
class Ex4LoadingLruCacheTest {

    /**
     * Loads run here, not in the caller. SupervisorJob so one failed load doesn't take the
     * scope down; same scheduler as the test so runCurrent() drives it.
     */
    private fun TestScope.loadScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @Test
    fun `loads on miss, then serves from cache`() = runTest {
        val scope = loadScope()
        var calls = 0
        val cache = LoadingLruCache<String, String>(capacity = 10, scope = scope) { calls++; it.uppercase() }

        assertNull(cache.getIfPresent("a"))
        assertEquals("A", cache.get("a"))
        assertEquals("A", cache.get("a"))
        assertEquals("A", cache.getIfPresent("a"))
        assertEquals(1, calls)
        scope.cancel()
    }

    @Test
    fun `concurrent requests for one key share a single load`() = runTest {
        val scope = loadScope()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val cache = LoadingLruCache<String, String>(capacity = 10, scope = scope) {
            calls++
            gate.await()
            it.uppercase()
        }

        val results = List(10) { async { cache.get("a") } }
        runCurrent()
        assertEquals(1, calls)

        gate.complete(Unit)
        assertEquals(List(10) { "A" }, results.awaitAll())
        assertEquals(1, calls)
        scope.cancel()
    }

    @Test
    fun `different keys load concurrently`() = runTest {
        val scope = loadScope()
        val gate = CompletableDeferred<Unit>()
        val started = mutableSetOf<String>()
        val cache = LoadingLruCache<String, String>(capacity = 10, scope = scope) {
            started += it
            gate.await()
            it.uppercase()
        }

        val a = async { cache.get("a") }
        val b = async { cache.get("b") }
        runCurrent()
        assertEquals(setOf("a", "b"), started, "a slow load for one key blocked another key")

        gate.complete(Unit)
        assertEquals("A", a.await())
        assertEquals("B", b.await())
        scope.cancel()
    }

    @Test
    fun `failure propagates and is not cached`() = runTest {
        val scope = loadScope()
        var calls = 0
        var fail = true
        val cache = LoadingLruCache<String, String>(capacity = 10, scope = scope) {
            calls++
            if (fail) throw IOException("network down")
            it.uppercase()
        }

        assertFailsWith<IOException> { cache.get("a") }
        assertNull(cache.getIfPresent("a"))

        fail = false
        assertEquals("A", cache.get("a"))
        assertEquals(2, calls)
        scope.cancel()
    }

    @Test
    fun `cancelling one waiter does not cancel the shared load`() = runTest {
        val scope = loadScope()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val cache = LoadingLruCache<String, String>(capacity = 10, scope = scope) {
            calls++
            gate.await()
            it.uppercase()
        }

        val first = async { cache.get("a") }
        val second = async { cache.get("a") }
        runCurrent()

        first.cancel()
        runCurrent()
        gate.complete(Unit)

        assertEquals("A", second.await())
        assertEquals(1, calls)
        scope.cancel()
    }

    @Test
    fun `holds at most capacity values`() = runTest {
        val scope = loadScope()
        val cache = LoadingLruCache<String, String>(capacity = 2, scope = scope) { it.uppercase() }

        cache.get("a")
        cache.get("b")
        cache.get("c")

        assertNull(cache.getIfPresent("a"))
        assertEquals("B", cache.getIfPresent("b"))
        assertEquals("C", cache.getIfPresent("c"))
        scope.cancel()
    }
}
