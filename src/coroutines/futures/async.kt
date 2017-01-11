package coroutines.futures

import coroutines.cancellable.Cancellable
import coroutines.cancellable.CancellationScope
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import java.util.concurrent.CompletableFuture

fun <T> asyncFuture(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): CompletableFuture<T> {
    val scope = CancellationScope(context[Cancellable])
    val future = AsyncCompletableFuture<T>(context + scope)
    future.whenComplete { t, u -> scope.cancel() }
    block.startCoroutine(future)
    return future
}

private class AsyncCompletableFuture<T>(
    override val context: CoroutineContext
) : CompletableFuture<T>(), CoroutineContinuation<T> {
    override fun resume(value: T) {
        complete(value)
    }

    override fun resumeWithException(exception: Throwable) {
        completeExceptionally(exception)
    }
}
