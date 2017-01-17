package kotlinx.coroutines.experimental.future

import kotlinx.coroutines.experimental.*
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Starts coroutine in the current coroutine context and returns its results an an implementation of [CompletableFuture].
 * If is already in the appropriate thread of the context (like in UI thread or in the appropriate thread pool),
 * then the [block] is executed _in this thread_ until its first suspension point.
 *
 * The running coroutine is cancelled when the resulting future is cancelled.
 * The [context] that is optionally specified as parameter is added to the context of the parent running coroutine (if any)
 * inside which this function is invoked. The lifetime of the resulting coroutine is subordinate to the lifetime
 * of the parent coroutine (if any).
 */
public fun <T> future(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): CompletableFuture<T> {
    val newContext = newCoroutineContext(context)
    val lifetime = Lifetime(newContext[Lifetime])
    val future = CompletableFutureCoroutine<T>(newContext + lifetime)
    future.whenComplete { _, exception -> lifetime.cancel(exception) }
    block.startCoroutine(future)
    return future
}

/**
 * Starts coroutine in the [CommonPool] context and returns its results an an implementation of [CompletableFuture].
 * The [block] is scheduled for execution in the background thread and this function immediately returns to the caller,
 * similarly to [CompletableFuture.supplyAsync], but with suspending block of code.
 *
 * The running coroutine is cancelled when the resulting future is cancelled.
 * The [context] that is optionally specified as parameter is added to the context of the parent running coroutine (if any)
 * inside which this function is invoked. The lifetime of the resulting coroutine is subordinate to the lifetime
 * of the parent coroutine (if any).
 */
public fun <T> futureAsync(block: suspend () -> T): CompletableFuture<T> = future(CommonPool, block)

/**
 * Awaits for completion of the future without blocking a thread. This suspending function is cancellable.
 * If the [Lifetime] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException] .
 */
public suspend fun <T> CompletableFuture<T>.await(): T =
    // quick check if already complete (avoid extra object creation)
    if (isDone) get() else suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
        val g = whenComplete { result, exception ->
            if (exception == null) // the future has been completed normally
                cont.resume(result)
            else // the future has completed with an exception
                cont.resumeWithException(exception)
        }
        // The logic this: cont.onCompletion { g.cancel(false) }
        // Below is an optimization (one fewer object)
        cont.onCompletion(CancelFutureOnCompletion(cont, g))
        Unit
    }

private class CompletableFutureCoroutine<T>(
    override val context: CoroutineContext
) : CompletableFuture<T>(), Continuation<T> {
    override fun resume(value: T) { complete(value) }
    override fun resumeWithException(exception: Throwable) { completeExceptionally(exception) }
}
