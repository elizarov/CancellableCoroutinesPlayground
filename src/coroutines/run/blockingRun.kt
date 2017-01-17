package coroutines.run

import kotlinx.coroutines.experimental.LifetimeSupport
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import coroutines.current.defaultCoroutineContext
import java.util.concurrent.locks.LockSupport

/**
 * Runs coroutine and *blocks* current thread until its completion.
 */
public fun <T> blockingRun(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): T {
    val blocking = BlockingCompletion<T>(defaultCoroutineContext + context)
    block.startCoroutine(blocking)
    return blocking.awaitBlocking()
}

private class BlockingCompletion<T>(outerContext: CoroutineContext) : LifetimeSupport(), CoroutineContinuation<T> {
    val blockedThread: Thread = Thread.currentThread()
    var value: T? = null
    var exception: Throwable? = null

    override val context: CoroutineContext = outerContext + this

    override fun resume(value: T) {
        this.value = value
        LockSupport.unpark(blockedThread)
        cancel()
    }

    override fun resumeWithException(exception: Throwable) {
        this.exception = exception
        LockSupport.unpark(blockedThread)
        cancel(exception)
    }

    fun awaitBlocking(): T {
        LockSupport.park(this)
        exception?.let { throw it }
        return value as T
    }
}
