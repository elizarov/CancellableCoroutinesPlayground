package coroutines.timeout

import coroutines.cancellable.LifetimeContinuation
import coroutines.cancellable.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit

suspend fun sleep(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Unit {
    require(time >= 0) { "Sleep time $time cannot be negative" }
    if (time <= 0) return // don't sleep
    return suspendCancellableCoroutine { cont: LifetimeContinuation<Unit> ->
        val timeout = timeoutExecutorService.schedule({ cont.resume(Unit) }, time, unit)
        cont.onCompletion { timeout.cancel(false) }
    }
}
