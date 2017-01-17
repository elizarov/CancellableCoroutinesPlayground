package coroutines.current

import coroutines.context.*

private val DEFAULT = ThreadLocal<CoroutineContext>()
private val CURRENT = ThreadLocal<CoroutineContext>()

/**
 * Returns `true` if the code is running inside a coroutine. This function may be used in blocking code
 * to ensure that it is not accidentally used in the coroutine.
 */
public val isRunningInCoroutine: Boolean get() = CURRENT.get() != null

/**
 * Returns context of the current coroutine or default context of this thread. This function shall be used
 * to start new coroutines.
 */
public val defaultCoroutineContext: CoroutineContext get() = CURRENT.get() ?: DEFAULT.get() ?: CurrentContext

/**
 * Changes default coroutine context for this thread. This function is to be used only in the main
 * code of the thread and shall not be invoked from inside of a coroutine. It throws [IllegalStateException]
 * when used from inside a coroutine, because coroutine context is persistent and cannot be change. Start a
 * new coroutine when you need a new context.
 */
public fun setThreadDefaultCoroutineContext(context: CoroutineContext) {
    check(!isRunningInCoroutine) { "Cannot change thread default coroutine context from inside of coroutine" }
    DEFAULT.set(context + CurrentContext)
}

private object CurrentContext : AbstractCoroutineContextElement(Key), ContinuationInterceptor {
    object Key : CoroutineContext.Key<CurrentContext>
    override fun <T> interceptContinuation(continuation: CoroutineContinuation<T>): CoroutineContinuation<T> =
        WithCurrentContext(continuation)
}

private class WithCurrentContext<T>(val continuation: CoroutineContinuation<T>) : CoroutineContinuation<T> {
    override val context: CoroutineContext = continuation.context

    override fun resume(value: T) {
        val old = CURRENT.get()
        CURRENT.set(context)
        try {
            continuation.resume(value)
        } finally {
            CURRENT.set(old)
        }
    }

    override fun resumeWithException(exception: Throwable) {
        val old = CURRENT.get()
        CURRENT.set(context)
        try {
            continuation.resumeWithException(exception)
        } finally {
            CURRENT.set(old)
        }
    }
}
