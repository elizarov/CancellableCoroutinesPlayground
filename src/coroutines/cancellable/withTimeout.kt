package coroutines.cancellable

import coroutines.context.startCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation

private val timeoutService = Executors.newScheduledThreadPool(1) { r -> Thread(r, "TimeoutService") }

suspend fun <T> withTimeout(millis: Long, block: suspend () -> T): T =
    suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
        // schedule cancellation of this continuation on time
        val timeout = timeoutService.schedule({ cont.cancel() }, millis, TimeUnit.MILLISECONDS)
        // restart block in a separate coroutine
        block.startCoroutine(completion = object : Continuation<T> {
            // on completion cancel timeout
            override fun resume(value: T) {
                timeout.cancel(false)
                cont.resume(value)
            }

            override fun resumeWithException(exception: Throwable) {
                timeout.cancel(false)
                cont.resumeWithException(exception)
            }
        }, context = cont.context + cont) // inner scope is cancellable as this continuation
    }