package coroutines.context

import org.junit.Assert.assertEquals
import org.junit.Test

class CoroutineContextTest {
    data class CtxA(val i: Int) : CoroutineContext {
        companion object : CoroutineContextType<CtxA>
        override val contextType get() = CtxA
    }

    data class CtxB(val i: Int) : CoroutineContext {
        companion object : CoroutineContextType<CtxB>
        override val contextType get() = CtxB
    }

    data class CtxC(val i: Int) : CoroutineContext {
        companion object : CoroutineContextType<CtxC>
        override val contextType get() = CtxC
    }

    @Test
    fun testGetPlus() {
        var ctx: CoroutineContext = EmptyCoroutineContext

        ctx += CtxA(1)
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(null, ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx += CtxB(2)
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx += CtxC(3)
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx += CtxB(4)
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx += CtxA(5)
        assertEquals(CtxA(5), ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])
    }

    @Test
    fun testRemove() {
        var ctx: CoroutineContext = CtxA(1) + CtxB(2) + CtxC(3)

        ctx = ctx.remove(CtxA)
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx = ctx.remove(CtxC)
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.remove(CtxC)
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.remove(CtxB)
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
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(3), ctx[CtxB])
        assertEquals(CtxC(4), ctx[CtxC])
    }
}