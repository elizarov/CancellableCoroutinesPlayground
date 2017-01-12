package coroutines.run

import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine

/**
 * Runs coroutine asynchronously without blocking current thread.
 */
fun asyncRun(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> Unit) {
    block.startCoroutine(AsyncCompletion(context))
}

private class AsyncCompletion(override val context: CoroutineContext) : CoroutineContinuation<Unit> {
    override fun resume(value: Unit) {}

    override fun resumeWithException(exception: Throwable) {
        (context[CoroutineExceptionHandler] ?: DefaultCoroutineExceptionHandler).handleException(context, exception)
    }
}
