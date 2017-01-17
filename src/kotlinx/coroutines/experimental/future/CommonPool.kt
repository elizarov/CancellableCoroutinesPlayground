package kotlinx.coroutines.experimental.future

import kotlinx.coroutines.experimental.CoroutineDispatcher
import java.util.concurrent.ForkJoinPool

/**
 * Represents [ForkJoinPool.commonPool] as coroutine dispatcher for compute-intensive tasks.
 * [ForkJoinPool] implements efficient work-stealing algorithm for its queues, so every
 * coroutine resumption is dispatched as a separate task even when it is executes inside the pool.
 */
object CommonPool : CoroutineDispatcher() {
    override fun isDispatchNeeded(): Boolean = true
    override fun dispatch(block: Runnable) = ForkJoinPool.commonPool().execute(block)
}
