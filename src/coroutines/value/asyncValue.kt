package coroutines.value

import coroutines.cancellable.Cancellable
import coroutines.cancellable.CancellationException
import coroutines.cancellable.CancellationScope
import coroutines.cancellable.suspendCancellableCoroutine
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContinuation
import coroutines.context.EmptyCoroutineContext
import coroutines.context.startCoroutine
import coroutines.current.defaultCoroutineContext
import coroutines.run.handleCoroutineException

public fun <T> asyncValue(context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> T) : AsyncValue<T> {
    val impl = AsyncValueImpl<T>(defaultCoroutineContext + context)
    block.startCoroutine(impl)
    return impl
}

public interface AsyncValue<out T> : Cancellable {
    public suspend fun getValue(): T
}

private class AsyncValueImpl<T>(
    outerContext: CoroutineContext
) : CancellationScope(outerContext[Cancellable]), AsyncValue<T>, CoroutineContinuation<T> {
    override val context: CoroutineContext = outerContext + this

    override fun resume(value: T) {
        complete(value)
    }

    override fun resumeWithException(exception: Throwable) {
        complete(Failed(exception))
    }

    private fun complete(value: Any?) {
        while (true) { // lock-free loop on state
            val state = getState()
            when (state) {
                is Active -> if (compareAndSetState(state, value)) return
                is Failed -> throw IllegalStateException("already completed with exception")
                is Cancelled -> {
                    // ignore
                    if (value is Failed) handleCoroutineException(context, value.reason!!)
                    return
                }
                else -> throw IllegalStateException("already completed with value")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend override fun getValue(): T {
        val state = getState()
        if (state !is Active) {
            if (state is Cancelled) throw retrieveException(state)
            return state as T
        }
        // Note: getValue is cancellable itself!
        return awaitGetValue()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun awaitGetValue(): T = suspendCancellableCoroutine { cont ->
        cont.unregisterOnCancel(registerCancelHandler {
            val state = getState()
            check(state !is Active)
            if (state is Cancelled)
                cont.resumeWithException(retrieveException(state))
            else
                cont.resume(state as T)
        })
    }

    private fun retrieveException(state: Cancelled): Throwable {
        if (state is Failed || state.reason is CancellationException) return state.reason!!
        // was cancelled but the reason is not CancellationException
        return CancellationException().apply { initCause(state.reason) }
    }

    private class Failed(reason: Throwable) : Cancelled(reason)
}
