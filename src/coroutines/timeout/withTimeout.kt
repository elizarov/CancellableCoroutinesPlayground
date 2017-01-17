package coroutines.timeout

import coroutines.cancellable.LifetimeContinuation
import coroutines.cancellable.suspendCancellableCoroutine
import coroutines.context.startCoroutine
import java.util.concurrent.TimeUnit

suspend fun <T> withTimeout(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, block: suspend () -> T): T =
    suspendCancellableCoroutine { cont: LifetimeContinuation<T> ->
        // schedule cancellation of this continuation on time
        val timeout = timeoutExecutorService.schedule({ cont.cancel() }, time, unit)
        cont.onCompletion { timeout.cancel(false) }
        // restart block in a separate coroutine using cancellable context of this continuation
        block.startCoroutine(cont)
    }
