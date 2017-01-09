package coroutines.futures

import coroutines.cancellable.CancellableContinuation
import coroutines.cancellable.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture

suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
        val g = whenComplete { result, exception ->
            if (exception == null) // the future has been completed normally
                cont.resume(result)
            else // the future has completed with an exception
                cont.resumeWithException(exception)
        }
        cont.registerCancelHandler { g.cancel(false) }
    }
