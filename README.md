# LRU Cache — Low-Level Design (Senior Android interview prep)

A worked LRU cache in pure Kotlin/JVM, written the way you'd present it in a 45–60 minute LLD round:
clarify, design the API, choose data structures, code, test, then extend. After the reference
solution there are four **practice exercises** with red tests for you to make green.

```
./gradlew test        # reference implementation — 35 tests, green
./gradlew exercises   # your practice — 27 tests, red until you implement them
```

| Branch      | What's on it                                                                   |
|-------------|--------------------------------------------------------------------------------|
| `main`      | Reference core + exercise stubs (red). Start here.                             |
| `drill`     | Core `LruCache` + `DoublyLinkedList` emptied to `TODO()`. Rewrite from memory. |
| `solutions` | One working answer for every exercise. Don't open it until you're green.       |

---

## Contents

1. [Clarify requirements](#step-1--clarify-requirements-first-5-minutes)
2. [Design the API](#step-2--design-the-api)
3. [Choose the data structures](#step-3--choose-the-data-structures)
4. [The linked list, with sentinels](#step-4--the-linked-list-with-sentinels)
5. [The cache operations](#step-5--the-cache-operations)
6. [Complexity](#step-6--complexity)
7. [Testing strategy](#step-7--testing-strategy)
8. [The standard-library shortcut](#step-8--the-standard-library-shortcut)
9. [Android angle](#step-9--android-angle-what-makes-this-a-senior-answer)
10. [Practice exercises](#practice-exercises)
11. [Follow-up questions to rehearse](#follow-up-questions-to-rehearse)

```
src/main/kotlin/dev/prep/lru/
├── Cache.kt                    interface, RemovalCause, RemovalListener, CacheStats
├── DoublyLinkedList.kt         intrusive list with sentinels (internal)
├── LruCache.kt                 ★ reference: HashMap + doubly linked list
├── LinkedHashMapLruCache.kt    the stdlib version, for contrast
└── exercises/                  Ex1..Ex4 stubs — yours to implement
src/test/kotlin/dev/prep/lru/
├── CacheContractTest.kt        one spec, run against both implementations
├── DoublyLinkedListTest.kt
└── exercises/                  @Tag("exercise") — run with ./gradlew exercises
```

---

## Step 1 — Clarify requirements (first 5 minutes)

Don't start coding. Ask, then write the answers on the board. Each one changes the design.

| Question | Answer we chose | Why it matters |
|---|---|---|
| Bounded by entry **count** or by **size** (bytes)? | Count. Size is Exercise 1. | Size-bounded means one insert can evict *many* entries. |
| Does a **read** count as a use? | Yes — `get` refreshes recency. | This is the "R" in LRU. It also means `get` *mutates*, which matters for threading. |
| Need a read that **doesn't** refresh? | Yes — `peek`. | Debugging, metrics, and UI "is it cached?" checks shouldn't disturb order. |
| Does `put` on an existing key refresh recency? | Yes, and never evicts. | Updating a value is a use; size doesn't change, so nothing should be evicted. |
| Notify on removal? | Yes — `RemovalListener` with a cause. | On Android you recycle Bitmaps / close resources when they leave. |
| Thread-safe? | Not in the core. Exercise 3. | Start simple, then show you know where the lock goes and why. |
| Expiry (TTL)? | Not in the core. Exercise 2. | LRU order ≠ age order. Needs a second ordering. |
| Nulls allowed? | No (`K : Any, V : Any`). | `get` returns `null` for a miss, so a `null` value would be ambiguous. |
| Target complexity? | **O(1)** for get / put / remove. | The whole design follows from this. |

> Say out loud: "I'll build the single-threaded O(1) core first, prove it with tests, then layer
> on size-awareness, TTL and concurrency as extensions." Interviewers want to see you scope.

---

## Step 2 — Design the API

See [`Cache.kt`](src/main/kotlin/dev/prep/lru/Cache.kt).

```kotlin
interface Cache<K : Any, V : Any> {
    val size: Int
    operator fun get(key: K): V?     // use: refreshes recency, counts hit/miss
    fun peek(key: K): V?             // inspect: no recency change, no stats
    fun put(key: K, value: V): V?    // use: returns previous value
    fun remove(key: K): V?
    fun containsKey(key: K): Boolean // inspect
    fun clear()
    fun keys(): List<K>              // snapshot, MRU first — great for tests
    fun stats(): CacheStats          // hits, misses, evictions, hitRate
}
```

Design decisions worth narrating:

- **An interface, not just a class.** Lets us run one contract test against several
  implementations, and lets callers swap in a fake.
- **`put` returns the old value** — same convention as `MutableMap.put` and `android.util.LruCache`.
- **`keys()` returns a snapshot `List`,** not a live view. A live view over a structure that
  reorders on every `get` is a footgun (ConcurrentModificationException waiting to happen).
- **`RemovalCause` enum** (`EVICTED`, `EXPLICIT`, `REPLACED`, `EXPIRED`) instead of a boolean
  `evicted` flag like Android's `entryRemoved(evicted: Boolean, …)`. More expressive, and it's
  extensible: TTL adds a cause without changing the signature.
- **Stats** — a senior engineer asks "how do we know the cache is working?" Hit rate is the answer.

---

## Step 3 — Choose the data structures

We need two things in O(1):

1. **Find** an entry by key → a **hash map**.
2. **Reorder** — move any entry to the front, and drop from the back → a **doubly linked list**.

Neither alone is enough:

| Structure | Lookup | Move-to-front | Evict LRU |
|---|---|---|---|
| HashMap only | O(1) | — (no order) | O(n) scan for oldest timestamp |
| Array / ArrayList | O(n) | O(n) shift | O(1) |
| Singly linked list | O(n) | O(n) — need the predecessor | O(n) |
| **HashMap → doubly-linked node** | **O(1)** | **O(1)** | **O(1)** |
| Min-heap on last-access time | O(1) via map | O(log n) | O(log n) |

The trick: **the map's values are the list nodes themselves.** Once you have the node you have
its `prev` and `next`, so unlinking it is pointer surgery — no search.

```
 index (HashMap<K, Node>)
   "a" ─────────────┐
   "b" ───────┐     │
   "c" ─┐     │     │
        ▼     ▼     ▼
 head ⇄ [c] ⇄ [b] ⇄ [a] ⇄ tail
        MRU               LRU
```

Why must the list be **doubly** linked? To remove a node in O(1) you must rewire its
predecessor's `next`. A singly linked list can't reach the predecessor without walking from head.

Why does the node store its **key**? When we evict the tail node, we must also delete it from the
map — and the map is keyed by `K`. Without the key on the node, eviction is O(n). This is the
single most common bug in whiteboard LRUs.

---

## Step 4 — The linked list, with sentinels

See [`DoublyLinkedList.kt`](src/main/kotlin/dev/prep/lru/DoublyLinkedList.kt).

Two dummy nodes, `head` and `tail`, that never hold data:

```
empty:   head ⇄ tail
one:     head ⇄ [a] ⇄ tail
```

Every real node therefore always has a non-null `prev` and `next`. That deletes every
"is this the first node? the last? the only one?" branch — fewer branches, fewer bugs.

A Kotlin-specific detail: a generic sentinel would need a fake `K` and `V`, which you can't
create. So we split it:

```kotlin
internal open class Link<K, V> { var prev: Link<K, V>?; var next: Link<K, V>? }   // sentinels
internal class Node<K, V>(val key: K, var value: V) : Link<K, V>()               // real entries
```

The four operations:

```kotlin
fun addFirst(node)  // head ⇄ node ⇄ oldFirst
fun unlink(node)    // prev ⇄ next, then null out node's pointers
fun moveToFront(node) = unlink + addFirst   (short-circuit if already first)
fun removeLast()    // tail.prev, unless it's head
```

Walk through `unlink(b)` on `head ⇄ a ⇄ b ⇄ c ⇄ tail`:

```
before = b.prev   // a
after  = b.next   // c
before.next = after   // a → c
after.prev  = before  // a ← c
b.prev = b.next = null   // defensive: a stale node can't corrupt the list later
```

`removeLast()` reads `tail.prev as? Node` — the cast fails (returns `null`) exactly when
`tail.prev` is the `head` sentinel, i.e. when the list is empty. Same trick in
`mostRecentFirst()`: iteration stops naturally at `tail` because it isn't a `Node`.

---

## Step 5 — The cache operations

See [`LruCache.kt`](src/main/kotlin/dev/prep/lru/LruCache.kt).

### `get(key)`
```
node = index[key] ?: (misses++; return null)
hits++
order.moveToFront(node)
return node.value
```

### `put(key, value)` — two cases
```
if key exists:
    swap value in place          // no allocation, no size change
    moveToFront
    notify REPLACED(old)
    return old

// new key
if size == capacity: evicted = evictEldest()   // BEFORE insert: never exceed capacity, even briefly
node = Node(key, value); addFirst(node); index[key] = node
notify EVICTED(evicted) if any                 // AFTER state is consistent
return null
```

### `evictEldest()`
```
eldest = order.removeLast()
index.remove(eldest.key)     // ← this is why the node stores its key
evictions++
```

### `remove(key)`
```
node = index.remove(key) ?: return null
order.unlink(node)
notify EXPLICIT
```

### The listener rule: mutate first, notify last

Every callback fires **after** both the map and the list agree. If the listener reads the cache,
re-enters it, or throws, the cache is still valid. Android's `LruCache` follows the same rule
(it calls `entryRemoved` outside its lock). In Exercise 3 this becomes "notify outside the lock".

### Edge cases the tests pin down

- capacity ≤ 0 → `IllegalArgumentException` at construction
- capacity 1 → every new key evicts the previous one
- `put` on an existing key when full → **no eviction**
- `peek` / `containsKey` → do **not** change order and do **not** count as hits
- `remove` of a missing key → `null`, no callback
- `clear` → every entry reported as `EXPLICIT`

---

## Step 6 — Complexity

| Operation | Time | Notes |
|---|---|---|
| `get` | O(1) avg | hash lookup + 4 pointer writes |
| `put` | O(1) avg | + at most one eviction |
| `remove` | O(1) avg | |
| `peek`, `containsKey` | O(1) avg | |
| `keys()` | O(n) | snapshot copy |
| Space | O(capacity) | one map entry + one node (2 pointers + key + value) per entry |

"Average" because HashMap is O(1) expected. Since Java 8, heavy collisions in one bucket turn
into a red-black tree (best when keys are `Comparable`), so the worst case is closer to
O(log n) than O(n) — nice detail to mention.

---

## Step 7 — Testing strategy

See [`CacheContractTest.kt`](src/test/kotlin/dev/prep/lru/CacheContractTest.kt).

- **Contract test.** One abstract test class, one subclass per implementation. Both `LruCache`
  and `LinkedHashMapLruCache` run the same 15 tests. When you rewrite (the `drill` branch) or
  add an implementation, you get the whole spec for free.
- **Observe through `keys()`** (MRU first) rather than poking internals. Order *is* the behaviour.
- **Capture listener calls** into a list and assert the exact `(key, value, cause)` sequence.
- **Pure unit tests, no sleeps.** Time is injected as `clock: () -> Long` in Exercise 2;
  coroutines use `runTest` + a test dispatcher in Exercise 4.
- The data structure has its own small test (`DoublyLinkedListTest`) — test the hard part in isolation.

---

## Step 8 — The standard-library shortcut

See [`LinkedHashMapLruCache.kt`](src/main/kotlin/dev/prep/lru/LinkedHashMapLruCache.kt).

```kotlin
object : LinkedHashMap<K, V>(16, 0.75f, /* accessOrder = */ true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > capacity
}
```

`LinkedHashMap` already threads a doubly linked list through its entries. `accessOrder = true`
makes `get`/`put` move the entry to the end; `removeEldestEntry` is called after each insert.
That's exactly how `android.util.LruCache` is implemented.

**How to use this in the interview:** mention it in the first two minutes ("in production I'd
reach for `LruCache`/`LinkedHashMap`"), then ask "would you like me to build the underlying
structure?" They almost always say yes. You've shown you wouldn't reinvent the wheel at work
*and* that you understand the wheel.

Its limits, which justify owning the list:
- No read that skips the reorder → `peek` is **O(n)** here.
- Iteration is LRU→MRU (oldest first).
- The eviction hook fires *inside* `put`, before the eldest entry is gone.

---

## Step 9 — Android angle (what makes this a senior answer)

- **Bitmap memory cache.** `LruCache<String, Bitmap>` sized to ~1/8 of
  `ActivityManager.memoryClass`, with `sizeOf = bitmap.allocationByteCount / 1024`. That's
  Exercise 1.
- **Respond to memory pressure.** `ComponentCallbacks2.onTrimMemory(level)` → `trimToSize(...)`
  or `evictAll()`. Mention that `TRIM_MEMORY_UI_HIDDEN` is the moment to shrink.
- **Two-level cache.** Memory LRU in front of a disk LRU (`DiskLruCache`), in front of the
  network. Coil and Glide do exactly this, plus an "active resources" layer for bitmaps currently
  on screen (held by weak reference, so the LRU can't recycle a bitmap that's being drawn).
- **De-duplicate in-flight requests.** Ten `RecyclerView`/`LazyColumn` rows asking for the same
  avatar should cause one download. That's Exercise 4 (single-flight).
- **Main-thread safety.** `get` is a write (it relinks), so a cache shared between the UI thread
  and a background loader needs a lock — and never do the slow work (decode, network) while
  holding it. That's Exercise 3.
- **Process death.** A memory cache is gone after process death; don't treat it as a source of
  truth. Rebuild from disk/DB.
- **Better than LRU?** A one-off scroll through 1,000 thumbnails flushes your hot entries
  ("scan pollution"). Caffeine uses **W-TinyLFU** (frequency sketch + small LRU window) to resist
  that. Knowing the name and the problem it solves is enough.

---

## Practice exercises

Each stub in [`src/main/kotlin/dev/prep/lru/exercises/`](src/main/kotlin/dev/prep/lru/exercises/)
has its spec in the KDoc and its tests in `src/test/.../exercises/`. Run one at a time:

```
./gradlew exercises --tests "dev.prep.lru.exercises.Ex1*"
```

You may reuse the `internal` `DoublyLinkedList` / `Node` — same module.

### Exercise 1 — Weighted LRU (`WeightedLruCache`) · 10 tests
Bound by total weight via `sizeOf(key, value)` instead of entry count; add `trimToWeight(target)`.
**Traps:** one insert evicting many; replacing a value changes its weight; an entry heavier than
the whole cache — and the *stale old value* it must not leave behind; negative weights.
**Discuss:** store weight on the node vs. a side map? What does Android's `LruCache` do when an
item is bigger than `maxSize`?

### Exercise 2 — LRU + TTL (`ExpiringLruCache`) · 9 tests
Expire-after-write with an injected clock, lazy expiry on access, `purgeExpired()`, and prefer
evicting an expired entry over the live LRU one.
**Traps:** `get` must not extend the TTL; access order ≠ write order.
**Discuss:** the full-cache path scans in O(n). How would you make it O(log n) (a second
structure ordered by write time — `TreeMap`/heap) or O(1) (with a *fixed* TTL, write order equals
insertion order, so a second linked list works — that's Caffeine's "write-order deque")?
Expire-after-access instead? Wall clock vs `SystemClock.elapsedRealtime()`?

### Exercise 3 — Thread-safe (`SynchronizedLruCache`) · 2 tests (stress + deadlock)
**Traps:** a `ReadWriteLock` with `get` on the read lock is **wrong** — `get` mutates the list.
The listener must run *after* releasing the lock (the test proves it with a second thread).
**Discuss:** `synchronized` vs `ReentrantLock` vs `Mutex` (and why not `Mutex` for a
non-suspending API); lock striping; why `ConcurrentHashMap` alone doesn't give you LRU;
Caffeine's read buffer.

### Exercise 4 — Loading cache with single-flight (`LoadingLruCache`) · 6 tests
`suspend fun get(key)` loads on miss; concurrent callers for one key share **one** load;
different keys load in parallel; failures aren't cached; cancelling one waiter doesn't cancel the
load for others.
**Traps:** awaiting inside the lock (serializes every key); running the load in the caller's
coroutine (one cancel kills everyone); forgetting to clear the in-flight entry on failure.
**Discuss:** should a failure be cached briefly (negative caching) to avoid a thundering herd?
How does this map onto a Repository + `StateFlow` in an app?

### Suggested routine
1. Read Steps 1–9 once.
2. `git switch drill` and rewrite `LruCache` + `DoublyLinkedList` from scratch until
   `./gradlew test` is green. Target: **under 25 minutes**, talking out loud.
3. Back on `main`, do Exercises 1 → 4.
4. Only then compare with `git diff main solutions -- src/main`.

### Self-review checklist (use on your own code)
- [ ] Every path that removes from the list also removes from the map (and vice versa).
- [ ] No path exceeds capacity, even for an instant.
- [ ] Listener runs after state is consistent (and outside any lock).
- [ ] `peek`/`containsKey` don't reorder or count stats.
- [ ] Replacing a value never evicts (count-bounded) or re-weighs correctly (weight-bounded).
- [ ] Every new test you write fails before your fix.

---

## Follow-up questions to rehearse

1. Why a doubly linked list and not a singly linked one? Why does the node keep its key?
2. What do the sentinels buy you? Write `unlink` without them — count the branches.
3. Is `get` a read or a write? What does that imply for locking?
4. Your listener calls `cache.put(...)`. What happens? (In our core: it works, because state is
   consistent before the callback — but you can recurse into another eviction. Should you allow it?)
5. How would you size an image cache on a 2 GB device vs. an 8 GB one?
6. LRU vs LFU vs FIFO vs W-TinyLFU — when does LRU behave badly?
7. How would you test the thread-safe version? Can a test ever *prove* thread-safety?
8. How would you add metrics without slowing the hot path?
9. Memory cache + disk cache: what's the read path, the write path, and what invalidates both?
10. You're asked for O(1) LFU. Sketch it. (Hint: map of frequency → doubly linked list, plus `minFreq`.)
