package dev.prep.lru

/**
 * Plain link with no payload. Used directly for the two sentinels, so they never need a
 * fake key/value (which generics would make awkward: you can't conjure a `K`).
 */
internal open class Link<K, V> {
    var prev: Link<K, V>? = null
    var next: Link<K, V>? = null
}

/** A real entry. The cache's HashMap points straight at these, which is what makes moves O(1). */
internal class Node<K, V>(val key: K, var value: V) : Link<K, V>()

/**
 * Intrusive doubly linked list ordered MRU (head side) -> LRU (tail side).
 *
 *     head <-> [MRU] <-> ... <-> [LRU] <-> tail
 *
 * Sentinels mean every real node always has a non-null prev and next, so no operation
 * needs an "is this the first/last node?" branch.
 */
internal class DoublyLinkedList<K, V> {

    private val head = Link<K, V>()
    private val tail = Link<K, V>()

    init {
        head.next = tail
        tail.prev = head
    }

    var size: Int = 0
        private set

    fun addFirst(node: Node<K, V>) {
        val first = head.next!!
        node.prev = head
        node.next = first
        first.prev = node
        head.next = node
        size++
    }

    fun unlink(node: Node<K, V>) {
        val before = node.prev!!
        val after = node.next!!
        before.next = after
        after.prev = before
        // Clear the dangling pointers so a stale node can't corrupt the list if reused by mistake.
        node.prev = null
        node.next = null
        size--
    }

    fun moveToFront(node: Node<K, V>) {
        if (head.next === node) return
        unlink(node)
        addFirst(node)
    }

    /** Least-recently-used node, or null when empty. */
    fun last(): Node<K, V>? = tail.prev as? Node<K, V>

    fun removeLast(): Node<K, V>? = last()?.also { unlink(it) }

    fun clear() {
        head.next = tail
        tail.prev = head
        size = 0
    }

    /** Walks MRU -> LRU. The tail sentinel is not a [Node], so the sequence stops there. */
    fun mostRecentFirst(): Sequence<Node<K, V>> =
        generateSequence(head.next as? Node<K, V>) { it.next as? Node<K, V> }
}
