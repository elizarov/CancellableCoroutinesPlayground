package context

import junit.framework.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass

class CoroutineContextTest {
    data class CtxA(val i: Int) : CoroutineContext {
        override val contextType: KClass<out CoroutineContext> get() = CtxA::class
    }

    data class CtxB(val i: Int) : CoroutineContext {
        override val contextType: KClass<out CoroutineContext> get() = CtxB::class
    }

    data class CtxC(val i: Int) : CoroutineContext {
        override val contextType: KClass<out CoroutineContext> get() = CtxC::class
    }

    @Test
    fun testBasicOps() {
        var ctx: CoroutineContext? = null

        ctx += CtxA(1)
        assertEquals(CtxA(1), ctx.find<CtxA>())
        assertEquals(null, ctx.find<CtxB>())
        assertEquals(null, ctx.find<CtxC>())

        ctx += CtxB(2)
        assertEquals(CtxA(1), ctx.find<CtxA>())
        assertEquals(CtxB(2), ctx.find<CtxB>())
        assertEquals(null, ctx.find<CtxC>())

        ctx += CtxC(3)
        assertEquals(CtxA(1), ctx.find<CtxA>())
        assertEquals(CtxB(2), ctx.find<CtxB>())
        assertEquals(CtxC(3), ctx.find<CtxC>())

        ctx += CtxB(4)
        assertEquals(CtxA(1), ctx.find<CtxA>())
        assertEquals(CtxB(4), ctx.find<CtxB>())
        assertEquals(CtxC(3), ctx.find<CtxC>())

        ctx = ctx.remove<CtxA>()
        assertEquals(null, ctx.find<CtxA>())
        assertEquals(CtxB(4), ctx.find<CtxB>())
        assertEquals(CtxC(3), ctx.find<CtxC>())

        ctx = ctx.remove<CtxC>()
        assertEquals(null, ctx.find<CtxA>())
        assertEquals(CtxB(4), ctx.find<CtxB>())
        assertEquals(null, ctx.find<CtxC>())

        ctx = ctx.remove<CtxC>()
        assertEquals(null, ctx.find<CtxA>())
        assertEquals(CtxB(4), ctx.find<CtxB>())
        assertEquals(null, ctx.find<CtxC>())

        ctx = ctx.remove<CtxB>()
        assertEquals(null, ctx.find<CtxA>())
        assertEquals(null, ctx.find<CtxB>())
        assertEquals(null, ctx.find<CtxC>())

        assertEquals(null, ctx)
    }
}