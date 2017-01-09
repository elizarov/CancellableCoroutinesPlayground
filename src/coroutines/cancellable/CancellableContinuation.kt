package coroutines.cancellable

import coroutines.context.CoroutineContinuation
import coroutines.context.suspendCoroutineOrReturn
import kotlin.coroutines.CoroutineIntrinsics.SUSPENDED

// --------------- cancellable continuations ---------------

public interface CancellableContinuation<in T> : CoroutineContinuation<T>, Cancellable {
    public fun cancel()
}

public inline suspend fun <T> suspendCancellableCoroutine(crossinline block: (CancellableContinuation<T>) -> Unit): T =
    suspendCoroutineOrReturn { c ->
        val safe = SafeCancellableContinuation(c, c.context[Cancellable])
        block(safe)
        safe.getResult()
    }

// --------------- implementation details ---------------

@PublishedApi
internal class SafeCancellableContinuation<in T>(
        private val delegate: CoroutineContinuation<T>,
        cancellable: Cancellable?
) : CancellationScope(cancellable), CancellableContinuation<T>, CoroutineContinuation<T> by delegate {
    private class Fail(val exception: Throwable)
    private class Suspended(val state: Any?)

    private companion object {
        @JvmStatic
        val SUSPENDED_ACTIVE = Suspended(ACTIVE) // Optimization (when no cancellation required)

        @JvmStatic
        val RESUMED: Any? = Any()

        @JvmStatic
        fun makeSuspended(state: Any?) = if (state == ACTIVE) SUSPENDED_ACTIVE else Suspended(state)
    }

    override fun unwrapState(state: Any?): Any? = (state as? Suspended)?.state ?: state

    override fun rewrapState(prevState: Any?, newState: Any?): Any? =
        if (prevState is Suspended) makeSuspended(newState) else newState

    override fun resume(value: T) {
        while (true) { // lock-free loop on state
            val cur = getState() // atomic read
            when (cur) {
                is ActiveNode -> if (compareAndSetState(cur, value)) return
                is Suspended -> if (compareAndSetState(cur, RESUMED)) {
                    delegate.resume(value)
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
                is ActiveNode -> if (compareAndSetState(cur, Fail(exception))) return
                is Suspended -> if (compareAndSetState(cur, RESUMED)) {
                    delegate.resumeWithException(exception)
                    return
                }
                CANCELLED -> return // ignore resumes on cancelled continuation
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    fun getResult(): Any? {
        while (true) { // lock-free loop on state
            val cur = getState() // atomic read
            when (cur) {
                is ActiveNode -> if (compareAndSetState(cur, makeSuspended(cur))) return SUSPENDED
                CANCELLED -> throw CancellationException()
                RESUMED -> return SUSPENDED // already called continuation, indicate SUSPENDED upstream
                is Fail -> throw cur.exception
                else -> return cur // result value must be there
            }
        }
    }

    override fun afterCancel(state: Any?, suppressedException: Throwable?) {
        if (state is Suspended) {
            val cancellationException = CancellationException()
            if (suppressedException != null) cancellationException.addSuppressed(suppressedException)
            delegate.resumeWithException(cancellationException)
        }
        if (suppressedException != null) throw suppressedException
    }
}
