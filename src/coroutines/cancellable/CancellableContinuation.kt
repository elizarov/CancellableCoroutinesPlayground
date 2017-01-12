package coroutines.cancellable

import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.suspendCoroutineOrReturn
import kotlin.coroutines.CoroutineIntrinsics.SUSPENDED

// --------------- cancellable continuations ---------------

/**
 * Cancellable continuation. Its cancellation scope spans its life-time.
 * It is implicitly cancelled with it is resumed or when the coroutine it is part of is cancelled.
 * In the later case or when [cancel] function is explicitly invoked,
 * this continuation resumes with [CancellationException]. If the cancel reason was not a cancellation exception,
 * the original exception is added as cause of the [CancellationException] that this continuation resumes with.
 */
public interface CancellableContinuation<in T> : CoroutineContinuation<T>, Cancellable

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
) : CancellationScope(delegate.context[Cancellable]), CancellableContinuation<T> {
    override val context: CoroutineContext = delegate.context + this

    // only updated from the thread that invoked suspendCancellableCoroutine
    private var suspendedThread: Thread? = Thread.currentThread()

    private class Failed(reason: Throwable) : Cancelled(reason)

    override fun resume(value: T) {
        while (true) { // lock-free loop on state
            val state = getState() // atomic read
            when (state) {
                is Active -> if (compareAndSetState(state, value)) return
                CANCELLED -> return // ignore resumes on cancelled continuation
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    override fun resumeWithException(exception: Throwable) {
        while (true) { // lock-free loop on state
            val cur = getState() // atomic read
            when (cur) {
                is Active -> if (compareAndSetState(cur, Failed(exception))) return
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
        if (state is Cancelled) throw retrieveException(state)
        return state
    }

    private fun retrieveException(state: Cancelled): Throwable {
        if (state is Failed || state.reason is CancellationException) return state.reason!!
        // was cancelled but the reason is not CancellationException
        return CancellationException().apply { initCause(state.reason) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun afterCancel(newState: Any?, closeException: Throwable?) {
        var result = newState
        if (closeException != null) {
            if (newState is Cancelled && newState.reason != null) {
                closeException.addSuppressed(newState.reason)
            }
            result = Failed(closeException)
        }
        if (suspendedThread === Thread.currentThread()) {
            // cancelled during suspendCancellableCoroutine in its thread
            suspendedThread = null
            if (result != newState) compareAndSetState(newState, result)
        } else {
            // cancelled later or in other thread
            if (result is Cancelled)
                delegate.resumeWithException(retrieveException(result))
            else
                delegate.resume(result as T)
        }
    }
}
