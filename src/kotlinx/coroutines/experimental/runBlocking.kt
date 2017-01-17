package kotlinx.coroutines.experimental

import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Runs coroutine and *blocks* current thread *interruptibly* until its completion.
 * This function should not be used from coroutine. It is designed to bridge regular code blocking code
 * to libraries that are written in suspending style.
 * The [context] that is optionally specified as parameter is added to the context of the parent running coroutine (if any)
 * inside which this function is invoked. The lifetime of the resulting coroutine is subordinate to the lifetime
 * of the parent coroutine (if any).
 */
public fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T): T =
    BlockingCoroutine<T>(newCoroutineContext(context)).also { block.startCoroutine(it) }.awaitBlocking()

private class BlockingCoroutine<T>(parentContext: CoroutineContext) : LifetimeContinuation<T>(parentContext) {
    val blockedThread: Thread = Thread.currentThread()

    override fun afterCompletion(state: Any?, closeException: Throwable?) {
        if (closeException != null) handleCoroutineException(context, closeException)
        LockSupport.unpark(blockedThread)
    }

    @Suppress("UNCHECKED_CAST")
    fun awaitBlocking(): T {
        while (isActive) LockSupport.park(this)
        val state = getState()
        (state as? CompletedExceptionally)?.let { throw it.exception }
        return state as T
    }
}
