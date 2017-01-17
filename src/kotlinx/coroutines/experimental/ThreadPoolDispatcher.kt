package kotlinx.coroutines.experimental

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Creates new coroutine execution context with the a single thread and built-in [delay] support.
 * All continuations are dispatched immediately when invoked inside the thread of this context.
 * Resources of this pool (its thread) are reclaimed when lifetime of this context is cancelled.
 * An optional parent lifetime may be specified upon creation.
 */
fun newSingleThreadContext(name: String, parent: Lifetime? = null): CoroutineContext {
    val lifetime = Lifetime(parent)
    return lifetime + ThreadPoolDispatcher(1, name, lifetime)
}

/**
 * Creates new coroutine execution context with the fixed-size thread-pool *-and built-in [delay] support.
 * All continuations are dispatched immediately when invoked inside the threads of this context.
 * Resources of this pool (its threads) are reclaimed when lifetime of this context is cancelled.
 * An optional parent lifetime may be specified upon creation.
 */
fun newFixedThreadPoolContext(nThreads: Int, name: String, parent: Lifetime? = null): CoroutineContext {
    require(nThreads >= 1) { "Expected at least one thread, but $nThreads specified" }
    val lifetime = Lifetime(parent)
    return lifetime + ThreadPoolDispatcher(nThreads, name, lifetime)
}

private val thisThreadContext = ThreadLocal<ThreadPoolDispatcher>()

private class ThreadPoolDispatcher(
        nThreads: Int,
        name: String,
        val lifetime: Lifetime
) : CoroutineDispatcher(), ContinuationInterceptor, Delay {
    val threadNo = AtomicInteger()
    val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(nThreads) { target ->
        thread(start = false, isDaemon = true,
                name = if (nThreads == 1) name else name + "-" + threadNo.incrementAndGet()) {
            thisThreadContext.set(this@ThreadPoolDispatcher)
            target.run()
        }
    }

    init {
        lifetime.onCompletion { executor.shutdown() }
    }

    override fun isDispatchNeeded(): Boolean = thisThreadContext.get() != this

    override fun dispatch(block: Runnable) = executor.execute(block)

    override fun resumeAfterDelay(time: Long, unit: TimeUnit, continuation: CancellableContinuation<Unit>) {
        val timeout = executor.schedule({ continuation.resume(Unit) }, time, unit)
        continuation.onCompletion(CancelFutureOnCompletion(continuation, timeout))
    }
}
