package context

import junit.framework.Assert.assertEquals
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
    fun testBasicOps() {
        var ctx: CoroutineContext? = null

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

        ctx = ctx.remove(CtxA)
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx = ctx.remove(CtxC)
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.remove(CtxC)
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.remove(CtxB)
        assertEquals(null, ctx[CtxA])
        assertEquals(null, ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        assertEquals(null, ctx)
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