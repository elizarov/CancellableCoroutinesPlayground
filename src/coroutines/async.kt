package coroutines

import coroutines.context.CoroutineContext
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.Continuation

fun <T> async(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): CompletableFuture<T> =
    AsyncCompletableFuture<T>().apply {
        block.startCoroutine(completion = this, context = context)
    }

private class AsyncCompletableFuture<T> : CompletableFuture<T>(), Continuation<T> {
    override fun resume(value: T) {
        complete(value)
    }
    override fun resumeWithException(exception: Throwable) {
        completeExceptionally(exception)
    }
}
