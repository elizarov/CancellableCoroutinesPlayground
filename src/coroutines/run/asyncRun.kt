package coroutines.run

import coroutines.cancellable.Cancellable
import coroutines.cancellable.CancellationScope
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import coroutines.current.defaultCoroutineContext

/**
 * Runs coroutine asynchronously without blocking current thread.
 */
fun asyncRun(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> Unit) {
    val ctx = defaultCoroutineContext + context
    val scope = CancellationScope(ctx[Cancellable])
    block.startCoroutine(AsyncCompletion(ctx + scope))
}

private class AsyncCompletion(
    val outerContext: CoroutineContext
) : CancellationScope(outerContext[Cancellable]), CoroutineContinuation<Unit> {
    override val context: CoroutineContext = outerContext + this

    override fun resume(value: Unit) {
        cancel()
    }

    override fun resumeWithException(exception: Throwable) {
        cancel(exception)
        handleCoroutineException(context, exception)
    }
}
