package coroutines.cancellable

import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.suspendCoroutineOrReturn
import kotlin.coroutines.CoroutineIntrinsics.SUSPENDED

// --------------- cancellable continuations ---------------

public interface CancellableContinuation<in T> : CoroutineContinuation<T>, Cancellable {
    public fun cancel()
}

public inline suspend fun <T> suspendCancellableCoroutine(crossinline block: (CancellableContinuation<T>) -> Unit): T =
    suspendCoroutineOrReturn { c ->
        val safe = SafeCancellableContinuation(c)
        block(safe)
        safe.getResult()
    }

// --------------- implementation details ---------------

@PublishedApi
internal class SafeCancellableContinuation<in T>(
        private val delegate: CoroutineContinuation<T>
) : CancellationScope(delegate.context[Cancellable]), CancellableContinuation<T>, CoroutineContinuation<T> {
    override val context: CoroutineContext by lazy { delegate.context + this@SafeCancellableContinuation }

    // only updated from the thread that invoked suspendCancellableCoroutine
    private var suspendedThread: Thread? = Thread.currentThread()

    private class Fail(val exception: Throwable)

    override fun resume(value: T) {
        while (true) { // lock-free loop on state
            val state = getState() // atomic read
            when (state) {
                is Active -> if (compareAndSetState(state, value)) {
                    if (suspendedThread === Thread.currentThread()) suspendedThread = null
                        else delegate.resume(value)
                    return
                }
                CANCELLED -> return // ignore resumes on cancelled continuation
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    override fun resumeWithException(exception: Throwable) {
        while (true) { // lock-free loop on state
            val cur = getState() // atomic read
            when (cur) {
                is Active -> if (compareAndSetState(cur, Fail(exception))) {
                    if (suspendedThread === Thread.currentThread()) suspendedThread = null
                        else delegate.resumeWithException(exception)
                    return
                }
                CANCELLED -> return // ignore resumes on cancelled continuation // todo: suppress exception?
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    fun getResult(): Any? {
        if (suspendedThread != null) {
            suspendedThread = null
            return SUSPENDED
        }
        val state = getState()
        if (state is Fail) throw state.exception
        return state
    }

    override fun afterCancel(suppressedException: Throwable?) {
        val cancellationException = CancellationException()
        if (suppressedException != null) cancellationException.addSuppressed(suppressedException)
        if (suspendedThread === Thread.currentThread()) {
            // cancelled during suspendCancellableCoroutine in its thread
            suspendedThread = null
            compareAndSetState(CANCELLED, Fail(cancellationException))
        } else {
            // cancelled later or in other thread
            delegate.resumeWithException(cancellationException)
        }
    }
}
