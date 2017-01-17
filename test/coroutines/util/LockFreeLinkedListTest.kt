package coroutines.util

import kotlinx.coroutines.experimental.util.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.util.LockFreeLinkedListNode
import org.junit.Assert.*
import org.junit.Test

class LockFreeLinkedListTest {
    data class IntNode(val i: Int) : LockFreeLinkedListNode()

    @Test
    fun testSimpleAddFirst() {
        val list = LockFreeLinkedListHead()
        assertContents(list)
        val n1 = IntNode(1).apply { list.addFirst(this) }
        assertContents(list, 1)
        val n2 = IntNode(2).apply { list.addFirst(this) }
        assertContents(list, 2, 1)
        val n3 = IntNode(3).apply { list.addFirst(this) }
        assertContents(list, 3, 2, 1)
        val n4 = IntNode(4).apply { list.addFirst(this) }
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
        val n1 = IntNode(1).apply { list.addLast(this) }
        assertContents(list, 1)
        val n2 = IntNode(2).apply { list.addLast(this) }
        assertContents(list, 1, 2)
        val n3 = IntNode(3).apply { list.addLast(this) }
        assertContents(list, 1, 2, 3)
        val n4 = IntNode(4).apply { list.addLast(this) }
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
        assertTrue(list.addLastIf(IntNode(1)) { true })
        assertContents(list, 1)
        assertFalse(list.addLastIf(IntNode(2)) { false })
        assertContents(list, 1)
        assertTrue(list.addFirstIf(IntNode(3)) { true })
        assertContents(list, 3, 1)
        assertFalse(list.addFirstIf(IntNode(4)) { false })
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