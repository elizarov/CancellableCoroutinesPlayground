package coroutines.util

import org.junit.Assert.*
import org.junit.Test

class LockFreeLinkedListTest {
    data class IntNode(val i: Int) : LockFreeLinkedListNode()

    @Test
    fun testSimpleSequential() {
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

    private fun assertContents(list: LockFreeLinkedListHead, vararg expected: Int) {
        val n = expected.size
        val actual = IntArray(n)
        var index = 0
        list.forEach<IntNode> { actual[index++] = it.i }
        assertEquals(n, index)
        for (i in 0 until n) assertEquals("item i", expected[i], actual[i])
    }
}