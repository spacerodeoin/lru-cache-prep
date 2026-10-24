package dev.prep.lru

// DRILL: rebuild this from memory. Tests: DoublyLinkedListTest. README Step 4 if stuck.

internal open class Link<K, V> {
    var prev: Link<K, V>? = null
    var next: Link<K, V>? = null
}

internal class Node<K, V>(val key: K, var value: V) : Link<K, V>()

/** MRU (head side) -> LRU (tail side), with sentinel head and tail. */
internal class DoublyLinkedList<K, V> {

    var size: Int = 0
        private set

    fun addFirst(node: Node<K, V>): Unit = TODO("drill")

    fun unlink(node: Node<K, V>): Unit = TODO("drill")

    fun moveToFront(node: Node<K, V>): Unit = TODO("drill")

    fun last(): Node<K, V>? = TODO("drill")

    fun removeLast(): Node<K, V>? = TODO("drill")

    fun clear(): Unit = TODO("drill")

    fun mostRecentFirst(): Sequence<Node<K, V>> = TODO("drill")
}
