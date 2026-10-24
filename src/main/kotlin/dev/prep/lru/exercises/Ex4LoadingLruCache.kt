package dev.prep.lru.exercises

import kotlinx.coroutines.CoroutineScope

/**
 * EXERCISE 4 — read-through cache with single-flight loading (coroutines).
 *
 * Android angle: an image/profile repository. Ten list rows ask for the same avatar at once;
 * you want ONE network call, not ten. This is what Coil/Glide do for in-flight requests.
 *
 * Spec (tests: Ex4LoadingLruCacheTest):
 * - [get] returns the cached value, or calls [loader] on a miss, caches the result, returns it.
 * - Concurrent [get]s for the same missing key share ONE loader call (single-flight).
 * - Different keys load concurrently — one slow key must not block another.
 * - A loader failure is rethrown to every waiter and is NOT cached; the next [get] retries.
 * - Cancelling one waiter must not cancel the load for the others. Run loads in [scope],
 *   not in the caller's coroutine.
 * - At most [capacity] values are held (LRU).
 * - [getIfPresent] never loads.
 *
 * Hint: a map of key -> Deferred<V> for in-flight loads, guarded by a Mutex. Remove the
 * in-flight entry when the load completes, success or failure. Where does the `await()` go —
 * inside or outside the mutex? (Inside = every key serialized behind one slow load.)
 */
class LoadingLruCache<K : Any, V : Any>(
    val capacity: Int,
    private val scope: CoroutineScope,
    private val loader: suspend (K) -> V,
) {

    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    suspend fun get(key: K): V = TODO("Exercise 4")

    fun getIfPresent(key: K): V? = TODO("Exercise 4")
}
