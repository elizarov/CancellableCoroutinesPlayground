package coroutines.cancellable

import coroutines.`try`.Try
import org.junit.Assert.*
import org.junit.Test

class CancellationScopeTest {
    @Test
    fun testState() {
        val scope = CancellationScope()
        assertTrue(scope.isActive)
        assertFalse(scope.isCancelled)
        scope.cancel()
        assertFalse(scope.isActive)
        assertTrue(scope.isCancelled)
    }

    @Test
    fun testHandler() {
        val scope = CancellationScope()
        var fireCount = 0
        scope.registerCancelHandler { fireCount++ }
        assertTrue(scope.isActive)
        assertFalse(scope.isCancelled)
        assertEquals(0, fireCount)
        // cancel once
        scope.cancel()
        assertFalse(scope.isActive)
        assertTrue(scope.isCancelled)
        assertEquals(1, fireCount)
        // cancel again
        scope.cancel()
        assertFalse(scope.isActive)
        assertTrue(scope.isCancelled)
        assertEquals(1, fireCount)
    }

    @Test
    fun testManyHandlers() {
        val scope = CancellationScope()
        val n = 100
        val fireCount = IntArray(n)
        for (i in 0 until n) scope.registerCancelHandler { fireCount[i]++ }
        assertTrue(scope.isActive)
        assertFalse(scope.isCancelled)
        for (i in 0 until n) assertEquals(0, fireCount[i])
        // cancel once
        scope.cancel()
        assertFalse(scope.isActive)
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
        // cancel again
        scope.cancel()
        assertFalse(scope.isActive)
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
    }

    @Test
    fun testManyHandlersWithUnregister() {
        val scope = CancellationScope()
        val n = 100
        val fireCount = IntArray(n)
        val registrations = Array<CancelRegistration>(n) { i -> scope.registerCancelHandler { fireCount[i]++ } }
        assertTrue(scope.isActive)
        assertFalse(scope.isCancelled)
        fun unreg(i: Int) = i % 4 <= 1
        for (i in 0 until n) if (unreg(i)) registrations[i].unregisterCancelHandler()
        for (i in 0 until n) assertEquals(0, fireCount[i])
        scope.cancel()
        assertFalse(scope.isActive)
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(if (unreg(i)) 0 else 1, fireCount[i])
    }

    @Test
    fun testExceptionsInHandler() {
        val scope = CancellationScope()
        val n = 100
        val fireCount = IntArray(n)
        class TestException : Throwable()
        for (i in 0 until n) scope.registerCancelHandler {
            fireCount[i]++
            throw TestException()
        }
        assertTrue(scope.isActive)
        assertFalse(scope.isCancelled)
        for (i in 0 until n) assertEquals(0, fireCount[i])
        val tryCancel = Try<Unit> { scope.cancel() }
        assertFalse(scope.isActive)
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
        assertTrue(tryCancel.exception is TestException)
    }

    @Test
    fun testMemoryRelease() {
        val scope = CancellationScope()
        val n = 10_000_000
        var fireCount = 0
        for (i in 0 until n) scope.registerCancelHandler { fireCount++ }.unregisterCancelHandler()
    }
}