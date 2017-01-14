package coroutines.context

import org.junit.After
import org.junit.Test
import kotlin.coroutines.CoroutineIntrinsics.SUSPENDED

class CoroutineInterceptorTest {
    suspend fun noSuspend(v: Int): Int = v
    suspend fun noSuspendTailCall(v: Int) = noSuspend(v)

    suspend fun suspendButReturn(v: Int): Int = suspendCoroutineOrReturn {
        v
    }

    suspend fun suspendReally(v: Int): Int = suspendCoroutineOrReturn {
        it.resume(v)
        SUSPENDED
    }

    suspend fun suspendReallyTailCall(v: Int): Int = suspendReally(v)

    @Test
    fun testNoSuspend() {
        suspendingRun {
            noSuspend(42)
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(42)")
    }

    @Test
    fun testNoSuspendTwice() {
        suspendingRun {
            noSuspend(42) // ignore this value
            noSuspend(43) // return this value
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(43)")
    }

    @Test
    fun testTailCallNoSuspend() {
        suspendingRun {
            noSuspendTailCall(42)
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(42)")
    }

    @Test
    fun testSuspendButReturn() {
        suspendingRun {
            suspendButReturn(42)
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("completion.resume(42)")
    }

    @Test
    fun testSuspendReally() {
        suspendingRun {
            suspendReally(42) + 1
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("Wrapper.resume(42)")
        expect("completion.resume(43)")
    }

    @Test
    fun testSuspendReallyTwice() {
        suspendingRun {
            suspendReally(42) // ignore this value
            suspendReally(43) + 1 // return this value
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("Wrapper.resume(42)")
        expect("Wrapper.resume(43)")
        expect("completion.resume(44)")
    }

    @Test
    fun testSuspendReallyTailCall() {
        suspendingRun {
            suspendReallyTailCall(42) + 1
        }
        expect("Wrapper.resume(kotlin.Unit)")
        expect("Wrapper.resume(42)")
        expect("completion.resume(43)")
    }

    // ---------------- helpers ----------------

    val log = arrayListOf<String>()
    var index = 0

    fun expect(msg: String) {
        check(index < log.size)  { "Missing: $msg" }
        check(msg == log[index]) { "Expected: $msg, but found: ${log[index]} at #$index" }
        index++
    }

    @After
    fun tearDown() {
        check(index == log.size) { "Unexpected: ${log[index]} at #$index" }
    }

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