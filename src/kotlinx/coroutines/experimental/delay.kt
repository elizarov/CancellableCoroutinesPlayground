package kotlinx.coroutines.experimental

import java.util.concurrent.TimeUnit
import kotlin.coroutines.ContinuationInterceptor

/**
 * Implemented by [CoroutineDispatcher] implementations that natively support non-blocking [delay] function.
 */
public interface Delay {
    /**
     * Delays coroutine for a given time without blocking a thread. This suspending function is cancellable.
     * If the [Lifetime] of the current coroutine is completed while this suspending function is waiting, this function
     * immediately resumes with [CancellationException].
     */
    suspend fun delay(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        require(time >= 0) { "Delay time $time cannot be negative" }
        if (time <= 0) return // don't delay
        return suspendCancellableCoroutine { resumeAfterDelay(time, unit, it) }
    }

    /**
     * Resumes a specified continuation after a specified delay.
     */
    fun resumeAfterDelay(time: Long, unit: TimeUnit, continuation: CancellableContinuation<Unit>)
}

/**
 * Delays coroutine for a given time without blocking a thread. This suspending function is cancellable.
 * If the [Lifetime] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 *
 * This function delegates to [Delay] implementation of the context [CoroutineDispatcher] if possible,
 * or suspends with a single threaded scheduled executor service otherwise.
 */
suspend fun delay(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) {
    require(time >= 0) { "Delay time $time cannot be negative" }
    if (time <= 0) return // don't delay
    return suspendCancellableCoroutine sc@ { cont: CancellableContinuation<Unit> ->
        (cont.context[ContinuationInterceptor] as? Delay)?.apply {
            resumeAfterDelay(time, unit, cont)
            return@sc
        }
        val timeout = scheduledExecutor.schedule({ cont.resume(Unit) }, time, unit)
        cont.onCompletion(CancelFutureOnCompletion(cont, timeout))
    }
}
