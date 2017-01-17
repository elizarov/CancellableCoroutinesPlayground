package coroutines.futures

import kotlinx.coroutines.experimental.Lifetime
import coroutines.cancellable.LifetimeContinuation
import kotlinx.coroutines.experimental.LifetimeSupport
import coroutines.cancellable.suspendCancellableCoroutine
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import coroutines.current.defaultCoroutineContext
import java.util.concurrent.CompletableFuture

public fun <T> asyncFuture(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): CompletableFuture<T> {
    val ctx = defaultCoroutineContext + context
    val scope = LifetimeSupport(ctx[Lifetime])
    val future = AsyncCompletableFuture<T>(ctx + scope)
    future.whenComplete { _, exception -> scope.cancel(exception) }
    block.startCoroutine(future)
    return future
}

public suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont: LifetimeContinuation<T> ->
        val g = whenComplete { result, exception ->
            if (exception == null) // the future has been completed normally
                cont.resume(result)
            else // the future has completed with an exception
                cont.resumeWithException(exception)
        }
        cont.onCompletion { g.cancel(false) }
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
