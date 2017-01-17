package kotlinx.coroutines.experimental

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Suspending value is a non-blocking cancellable future.
 */
public interface SuspendingValue<out T> : Lifetime {
    /**
     * Awaits for completion of this value without blocking a thread. This suspending function is cancellable.
     * If the [Lifetime] of the current coroutine is completed while this suspending function is waiting, this function
     * immediately resumes with [CancellationException] .
     */
    public suspend fun getValue(): T
}

/**
 * Starts coroutine and returns its results an an implementation of [SuspendingValue].
 * The running coroutine is cancelled when the resulting value is cancelled.
 * The [context] that is optionally specified as parameter is added to the context of the parent running coroutine (if any)
 * inside which this function is invoked. The lifetime of the resulting coroutine is subordinate to the lifetime
 * of the parent coroutine (if any).
 */
public fun <T> suspendingValue(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T) : SuspendingValue<T> =
    SuspendingValueCoroutine<T>(currentCoroutineContext(context)).also { block.startCoroutine(it) }

private class SuspendingValueCoroutine<T>(
        parentContext: CoroutineContext
) : LifetimeContinuation<T>(parentContext), SuspendingValue<T> {
    @Suppress("UNCHECKED_CAST")
    suspend override fun getValue(): T {
        // quick check if already complete (avoid extra object creation)
        val state = getState()
        if (state !is Active) {
            if (state is CompletedExceptionally) throw state.exception
            return state as T
        }
        // Note: getValue is cancellable itself!
        return awaitGetValue()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun awaitGetValue(): T = suspendCancellableCoroutine { cont ->
        cont.unregisterOnCompletion(onCompletion {
            val state = getState()
            check(state !is Active)
            if (state is CompletedExceptionally)
                cont.resumeWithException(state.exception)
            else
                cont.resume(state as T)
        })
    }

    override fun afterCompletion(state: Any?, closeException: Throwable?) {
        if (closeException != null) handleCoroutineException(context, closeException)
    }
}
