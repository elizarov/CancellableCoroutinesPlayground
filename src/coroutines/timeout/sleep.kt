package coroutines.timeout

import coroutines.cancellable.CancellableContinuation
import coroutines.cancellable.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit

suspend fun sleep(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Unit =
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        val timeout = timeoutExecutorService.schedule({ cont.resume(Unit) }, time, unit)
        cont.registerCancelHandler { timeout.cancel(false) }
    }
