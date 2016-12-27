import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationDispatcher
import kotlin.coroutines.startCoroutine

private val timeoutService = Executors.newScheduledThreadPool(1) { r -> Thread(r, "TimeoutService") }

suspend fun <T> withTimeout(millis: Long, block: suspend () -> T): T =
    suspendCancellableDispatchedCoroutine { c: CancellableContinuation<T>, d: ContinuationDispatcher? ->
        // schedule cancellation of this continuation on time
        val timeout = timeoutService.schedule({ c.cancel() }, millis, TimeUnit.MILLISECONDS)
        // restart block in a separate coroutine
        block.startCoroutine(completion = object : Continuation<T> {
            // on completion cancel timeout
            override fun resume(value: T) {
                timeout.cancel(false)
                c.resume(value)
            }

            override fun resumeWithException(exception: Throwable) {
                timeout.cancel(false)
                c.resumeWithException(exception)
            }
        }, dispatcher = CancellableDispatcher(c, d)) // use cancellable dispatcher inside
    }
