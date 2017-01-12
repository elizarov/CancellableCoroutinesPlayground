package coroutines.futures

import coroutines.cancellable.Cancellable
import coroutines.cancellable.CancellableContinuation
import coroutines.cancellable.CancellationScope
import coroutines.cancellable.suspendCancellableCoroutine
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import coroutines.current.defaultCoroutineContext
import java.util.concurrent.CompletableFuture

public fun <T> asyncFuture(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): CompletableFuture<T> {
    val ctx = defaultCoroutineContext + context
    val scope = CancellationScope(ctx[Cancellable])
    val future = AsyncCompletableFuture<T>(ctx + scope)
    future.whenComplete { _, exception -> scope.cancel(exception) }
    block.startCoroutine(future)
    return future
}

public suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
        val g = whenComplete { result, exception ->
            if (exception == null) // the future has been completed normally
                cont.resume(result)
            else // the future has completed with an exception
                cont.resumeWithException(exception)
        }
        cont.registerCancelHandler { g.cancel(false) }
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
