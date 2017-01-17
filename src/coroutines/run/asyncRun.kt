package coroutines.run

import kotlinx.coroutines.experimental.Lifetime
import kotlinx.coroutines.experimental.LifetimeSupport
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
    val scope = LifetimeSupport(ctx[Lifetime])
    block.startCoroutine(AsyncCompletion(ctx + scope))
}

private class AsyncCompletion(
    val outerContext: CoroutineContext
) : LifetimeSupport(outerContext[Lifetime]), CoroutineContinuation<Unit> {
    override val context: CoroutineContext = outerContext + this

    override fun resume(value: Unit) {
        cancel()
    }

    override fun resumeWithException(exception: Throwable) {
        cancel(exception)
        handleCoroutineException(context, exception)
    }
}
