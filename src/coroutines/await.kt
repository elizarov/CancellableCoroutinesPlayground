package coroutines

import coroutines.cancellable.CancelHandler
import coroutines.cancellable.Cancellable
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
        cont.registerCancelHandler(object : CancelHandler {
            override fun handleCancel(cancellable: Cancellable) {
                g.cancel(false)
            }
        })
    }
