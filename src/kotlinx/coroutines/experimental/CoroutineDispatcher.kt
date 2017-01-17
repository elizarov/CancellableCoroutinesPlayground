package kotlinx.coroutines.experimental

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor

/**
 * Base class that shall be extended by all coroutine thread dispatchers so that that [currentCoroutineContext] is
 * correctly transferred to a new thread.
 */
public abstract class CoroutineDispatcher :
        AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    /**
     * Return `true` if execution shall be dispatched onto another thread.
     */
    public abstract fun isDispatchNeeded(): Boolean

    /**
     * Dispatches execution of a runnable [block] onto another thread.
     */
    public abstract fun dispatch(block: Runnable)

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
            DispatchedContinuation<T>(this, continuation)
}

private class DispatchedContinuation<T>(
        val dispatcher: CoroutineDispatcher,
        val continuation: Continuation<T>
): Continuation<T> by continuation {
    override fun resume(value: T) {
        if (dispatcher.isDispatchNeeded())
            dispatcher.dispatch(Runnable {
                withDefaultCoroutineContext(continuation.context) {
                    continuation.resume(value)
                }
            })
        else continuation.resume(value)
    }

    override fun resumeWithException(exception: Throwable) {
        if (dispatcher.isDispatchNeeded())
            dispatcher.dispatch(Runnable {
                withDefaultCoroutineContext(continuation.context) {
                    continuation.resumeWithException(exception)
                }
            })
        else continuation.resumeWithException(exception)
    }
}
