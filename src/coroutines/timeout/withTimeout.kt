package coroutines.timeout

import coroutines.cancellable.CancellableContinuation
import coroutines.cancellable.suspendCancellableCoroutine
import coroutines.context.startCoroutine
import java.util.concurrent.TimeUnit

suspend fun <T> withTimeout(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, block: suspend () -> T): T =
    suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
        // schedule cancellation of this continuation on time
        val timeout = timeoutExecutorService.schedule({ cont.cancel() }, time, unit)
        cont.registerCancelHandler { timeout.cancel(false) }
        // restart block in a separate coroutine using cancellable context of this continuation
        block.startCoroutine(cont)
    }
