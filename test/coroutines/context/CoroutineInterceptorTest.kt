package coroutines.context

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CoroutineInterceptorTest {
    suspend fun noSuspend(): Int = 42
    suspend fun noSuspendTailCall() = noSuspend()

    suspend fun suspendButReturn(): Int = suspendCoroutineOrReturn {
        42
    }

    @Test
    fun testNoSuspend() {
        suspendingRun {
            noSuspend()
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(42)")
    }

    @Test
    fun testNoSuspendTwice() {
        suspendingRun {
            noSuspend() + 1 // ignore this value
            noSuspend() + 2 // return this value
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(44)")
    }

    @Test
    fun testTailCallNoSuspend() {
        suspendingRun {
            noSuspendTailCall()
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(42)")
    }

    @Test
    fun testSuspendButReturn() {
        suspendingRun {
            suspendButReturn()
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(42)")
    }

    // ---------------- helpers ----------------

    val log = arrayListOf<String>()
    var index = 0

    fun expect(msg: String) = assertEquals(msg, log[index++])

    @After
    fun tearDown() = assertEquals(index, log.size)

    inner class Wrapper<T>(val continuation: CoroutineContinuation<T>): CoroutineContinuation<T> {
        override val context: CoroutineContext
            get() = continuation.context

        override fun resume(value: T) {
            log += "Wrapper.resume($value)"
            continuation.resume(value)
        }

        override fun resumeWithException(exception: Throwable) {
            log += "Wrapper.resumeWithException($exception)"
            continuation.resumeWithException(exception)
        }
    }

    inner class Interceptor : AbstractCoroutineContextElement(), ContinuationInterceptor {
        override val contextKey: CoroutineContextKey<*> = ContinuationInterceptor
        override fun <T> interceptContinuation(continuation: CoroutineContinuation<T>): CoroutineContinuation<T> =
                Wrapper(continuation)
    }

    fun <T> suspendingRun(block: suspend () -> T) {
        block.startCoroutine(completion = object : CoroutineContinuation<T> {
            override val context: CoroutineContext = Interceptor()
            override fun resume(value: T) {
                log += "completion.resume($value)"
            }

            override fun resumeWithException(exception: Throwable) {
                log += "completion.resumeWithException($exception)"
            }
        })
    }


}