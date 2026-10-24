package dev.prep.lru

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class DoublyLinkedListTest {

    private val list = DoublyLinkedList<String, Int>()

    private fun keys() = list.mostRecentFirst().map { it.key }.toList()

    @Test
    fun `empty list has no last node`() {
        assertNull(list.last())
        assertNull(list.removeLast())
        assertEquals(0, list.size)
    }

    @Test
    fun `addFirst puts newest at the front`() {
        list.addFirst(Node("a", 1))
        list.addFirst(Node("b", 2))

        assertEquals(listOf("b", "a"), keys())
        assertEquals("a", list.last()?.key)
    }

    @Test
    fun `moveToFront reorders without changing size`() {
        val a = Node("a", 1)
        list.addFirst(a)
        list.addFirst(Node("b", 2))
        list.addFirst(Node("c", 3))

        list.moveToFront(a)

        assertEquals(listOf("a", "c", "b"), keys())
        assertEquals(3, list.size)
    }

    @Test
    fun `unlink a middle node stitches neighbours`() {
        val b = Node("b", 2)
        list.addFirst(Node("a", 1))
        list.addFirst(b)
        list.addFirst(Node("c", 3))

        list.unlink(b)

        assertEquals(listOf("c", "a"), keys())
        assertNull(b.prev)
        assertNull(b.next)
    }

    @Test
    fun `removeLast pops the least recent`() {
        list.addFirst(Node("a", 1))
        list.addFirst(Node("b", 2))

        assertEquals("a", list.removeLast()?.key)
        assertEquals(listOf("b"), keys())
    }
}
