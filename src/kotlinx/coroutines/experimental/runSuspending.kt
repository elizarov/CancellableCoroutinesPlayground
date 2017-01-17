package kotlinx.coroutines.experimental

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Runs coroutine without blocking current thread. Uncaught exceptions in this coroutine
 * cancel parent lifetime in the context by default (unless [CoroutineExceptionHandler] is explicitly specified),
 * which means that when `runSuspending` is used from another coroutine, any uncaught exception leads to the
 * cancellation of parent coroutine.
 * The [context] that is optionally specified as parameter is added to the context of the parent running coroutine (if any)
 * inside which this function is invoked. The lifetime of the resulting coroutine is subordinate to the lifetime
 * of the parent coroutine (if any).
 */
fun runSuspending(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> Unit) {
    block.startCoroutine(StandaloneCoroutine(newCoroutineContext(context)))
}

private class StandaloneCoroutine(
    val parentContext: CoroutineContext
) : LifetimeContinuation<Unit>(parentContext) {
    override fun afterCompletion(state: Any?, closeException: Throwable?) {
        if (closeException != null) handleCoroutineException(context, closeException)
        // note the use of the parent context below!
        if (state is CompletedExceptionally) handleCoroutineException(parentContext, state.exception)
    }
}
