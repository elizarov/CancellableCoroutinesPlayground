package coroutines.context

import org.junit.Assert.*
import org.junit.Test

class CoroutineContextTest {
    data class CtxA(val i: Int) : CoroutineContextElement {
        companion object : CoroutineContextKey<CtxA>
        override val contextKey get() = CtxA
    }

    data class CtxB(val i: Int) : CoroutineContextElement {
        companion object : CoroutineContextKey<CtxB>
        override val contextKey get() = CtxB
    }

    data class CtxC(val i: Int) : CoroutineContextElement {
        companion object : CoroutineContextKey<CtxC>
        override val contextKey get() = CtxC
    }

    @Test
    fun testGetPlusFold() {
        var ctx: CoroutineContext = EmptyCoroutineContext
        assertContents(ctx)
        assertEquals("EmptyCoroutineContext", ctx.toString())

        ctx += CtxA(1)
        assertContents(ctx, CtxA(1))
        assertEquals("CtxA(i=1)", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(null, ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx += CtxB(2)
        assertContents(ctx, CtxA(1), CtxB(2))
        assertEquals("[CtxA(i=1), CtxB(i=2)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx += CtxC(3)
        assertContents(ctx, CtxA(1), CtxB(2), CtxC(3))
        assertEquals("[CtxA(i=1), CtxB(i=2), CtxC(i=3)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx += CtxB(4)
        assertContents(ctx, CtxA(1), CtxC(3), CtxB(4))
        assertEquals("[CtxA(i=1), CtxC(i=3), CtxB(i=4)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx += CtxA(5)
        assertContents(ctx, CtxC(3), CtxB(4), CtxA(5))
        assertEquals("[CtxC(i=3), CtxB(i=4), CtxA(i=5)]", ctx.toString())
        assertEquals(CtxA(5), ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])
    }

    @Test
    fun testRemove() {
        var ctx: CoroutineContext = CtxA(1) + CtxB(2) + CtxC(3)
        assertContents(ctx, CtxA(1), CtxB(2), CtxC(3))
        assertEquals("[CtxA(i=1), CtxB(i=2), CtxC(i=3)]", ctx.toString())

        ctx = ctx.remove(CtxA)
        assertContents(ctx, CtxB(2), CtxC(3))
        assertEquals("[CtxB(i=2), CtxC(i=3)]", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx = ctx.remove(CtxC)
        assertContents(ctx, CtxB(2))
        assertEquals("CtxB(i=2)", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.remove(CtxC)
        assertContents(ctx, CtxB(2))
        assertEquals("CtxB(i=2)", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.remove(CtxB)
        assertContents(ctx)
        assertEquals("EmptyCoroutineContext", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(null, ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        assertEquals(EmptyCoroutineContext, ctx)
    }

    @Test
    fun testPlusCombined() {
        val ctx1 = CtxA(1) + CtxB(2)
        val ctx2 = CtxB(3) + CtxC(4)
        val ctx = ctx1 + ctx2
        assertContents(ctx, CtxA(1), CtxB(3), CtxC(4))
        assertEquals("[CtxA(i=1), CtxB(i=3), CtxC(i=4)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(3), ctx[CtxB])
        assertEquals(CtxC(4), ctx[CtxC])
    }

    @Test
    fun testEquals() {
        val ctx1 = CtxA(1) + CtxB(2) + CtxC(3)
        val ctx2 = CtxB(2) + CtxC(3) + CtxA(1) // same
        val ctx3 = CtxC(3) + CtxA(1) + CtxB(2) // same
        val ctx4 = CtxA(1) + CtxB(2) + CtxC(4) // different
        assertEquals(ctx1, ctx2)
        assertEquals(ctx1, ctx3)
        assertEquals(ctx2, ctx3)
        assertNotEquals(ctx1, ctx4)
        assertNotEquals(ctx2, ctx4)
        assertNotEquals(ctx3, ctx4)
    }

    private fun  assertContents(ctx: CoroutineContext, vararg elements: CoroutineContextElement) {
        val set = ctx.fold(setOf<CoroutineContext>()) { a, b -> a + b }
        assertEquals(listOf(*elements), set.toList())
        for (elem in elements)
            assertTrue(elem in ctx)
    }
}