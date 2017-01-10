package coroutines.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockFreeLinkedListTest {
    data class IntNode(val i: Int) : LockFreeLinkedListNode()

    @Test
    fun testSimpleAddFirst() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        val n1 = list.addFirst(IntNode(1))
        assertContents(list, 1)
        val n2 = list.addFirst(IntNode(2))
        assertContents(list, 2, 1)
        val n3 = list.addFirst(IntNode(3))
        assertContents(list, 3, 2, 1)
        val n4 = list.addFirst(IntNode(4))
        assertContents(list, 4, 3, 2, 1)
        n1.remove()
        assertContents(list, 4, 3, 2)
        n3.remove()
        assertContents(list, 4, 2)
        n4.remove()
        assertContents(list, 2)
        n2.remove()
        assertContents(list)
    }

    @Test
    fun testSimpleAddLast() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        val n1 = list.addLast(IntNode(1))
        assertContents(list, 1)
        val n2 = list.addLast(IntNode(2))
        assertContents(list, 1, 2)
        val n3 = list.addLast(IntNode(3))
        assertContents(list, 1, 2, 3)
        val n4 = list.addLast(IntNode(4))
        assertContents(list, 1, 2, 3, 4)
        n1.remove()
        assertContents(list, 2, 3, 4)
        n3.remove()
        assertContents(list, 2, 4)
        n4.remove()
        assertContents(list, 2)
        n2.remove()
        assertContents(list)
    }

    @Test
    fun testCondOps() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        assertTrue(list.addLastIf(IntNode(1)) { true } != null)
        assertContents(list, 1)
        assertTrue(list.addLastIf(IntNode(2)) { false } == null)
        assertContents(list, 1)
        assertTrue(list.addFirstIf(IntNode(3)) { true } != null)
        assertContents(list, 3, 1)
        assertTrue(list.addFirstIf(IntNode(4)) { false } == null)
        assertContents(list, 3, 1)
    }

    private fun assertContents(list: LockFreeLinkedListHead, vararg expected: Int) {
        list.validate()
        val n = expected.size
        val actual = IntArray(n)
        var index = 0
        list.forEach<IntNode> { actual[index++] = it.i }
        assertEquals(n, index)
        for (i in 0 until n) assertEquals("item i", expected[i], actual[i])
    }
}