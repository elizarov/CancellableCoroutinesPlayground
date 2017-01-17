package coroutines.cancellable

import coroutines.`try`.Try
import kotlinx.coroutines.experimental.Registration
import kotlinx.coroutines.experimental.LifetimeSupport
import org.junit.Assert.*
import org.junit.Test

class CancellationScopeTest {
    @Test
    fun testState() {
        val scope = LifetimeSupport()
        assertFalse(scope.isCancelled)
        scope.cancel()
        assertTrue(scope.isCancelled)
    }

    @Test
    fun testHandler() {
        val scope = LifetimeSupport()
        var fireCount = 0
        scope.onCompletion { fireCount++ }
        assertFalse(scope.isCancelled)
        assertEquals(0, fireCount)
        // cancel once
        scope.cancel()
        assertTrue(scope.isCancelled)
        assertEquals(1, fireCount)
        // cancel again
        scope.cancel()
        assertTrue(scope.isCancelled)
        assertEquals(1, fireCount)
    }

    @Test
    fun testManyHandlers() {
        val scope = LifetimeSupport()
        val n = 100
        val fireCount = IntArray(n)
        for (i in 0 until n) scope.onCompletion { fireCount[i]++ }
        assertFalse(scope.isCancelled)
        for (i in 0 until n) assertEquals(0, fireCount[i])
        // cancel once
        scope.cancel()
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
        // cancel again
        scope.cancel()
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
    }

    @Test
    fun testUnregisterInHandler() {
        val scope = LifetimeSupport()
        val n = 100
        val fireCount = IntArray(n)
        for (i in 0 until n) {
            var registration: Registration? = null
            registration = scope.onCompletion {
                fireCount[i]++
                registration!!.unregisterCancelHandler()
            }
        }
        assertFalse(scope.isCancelled)
        for (i in 0 until n) assertEquals(0, fireCount[i])
        // cancel once
        scope.cancel()
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
        // cancel again
        scope.cancel()
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
    }

    @Test
    fun testManyHandlersWithUnregister() {
        val scope = LifetimeSupport()
        val n = 100
        val fireCount = IntArray(n)
        val registrations = Array<Registration>(n) { i -> scope.onCompletion { fireCount[i]++ } }
        assertFalse(scope.isCancelled)
        fun unreg(i: Int) = i % 4 <= 1
        for (i in 0 until n) if (unreg(i)) registrations[i].unregisterCancelHandler()
        for (i in 0 until n) assertEquals(0, fireCount[i])
        scope.cancel()
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(if (unreg(i)) 0 else 1, fireCount[i])
    }

    @Test
    fun testExceptionsInHandler() {
        val scope = LifetimeSupport()
        val n = 100
        val fireCount = IntArray(n)
        class TestException : Throwable()
        for (i in 0 until n) scope.onCompletion {
            fireCount[i]++
            throw TestException()
        }
        assertFalse(scope.isCancelled)
        for (i in 0 until n) assertEquals(0, fireCount[i])
        val tryCancel = Try<Unit> { scope.cancel() }
        assertTrue(scope.isCancelled)
        for (i in 0 until n) assertEquals(1, fireCount[i])
        assertTrue(tryCancel.exception is TestException)
    }

    @Test
    fun testMemoryRelease() {
        val scope = LifetimeSupport()
        val n = 10_000_000
        var fireCount = 0
        for (i in 0 until n) scope.onCompletion { fireCount++ }.unregister()
    }
}