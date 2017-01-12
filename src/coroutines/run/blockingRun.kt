package coroutines.run

import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import java.util.concurrent.locks.LockSupport

/**
 * Runs coroutine and blocks current thread until its completion.
 */
public fun <T> blockingRun(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): T {
    val blocking = BlockingCompletion<T>(context)
    block.startCoroutine(blocking)
    return blocking.awaitBlocking()
}

private class BlockingCompletion<T>(override val context: CoroutineContext) : CoroutineContinuation<T> {
    val blockedThread: Thread = Thread.currentThread()
    var value: T? = null
    var exception: Throwable? = null

    override fun resume(value: T) {
        this.value = value
        LockSupport.unpark(blockedThread)
    }

    override fun resumeWithException(exception: Throwable) {
        this.exception = exception
        LockSupport.unpark(blockedThread)
    }

    fun awaitBlocking(): T {
        LockSupport.park(this)
        exception?.let { throw it }
        return value as T
    }
}
