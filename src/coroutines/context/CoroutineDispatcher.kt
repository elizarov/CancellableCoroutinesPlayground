package coroutines.context

import kotlin.coroutines.Continuation

public interface CoroutineDispatcher : CoroutineContextElement {
    companion object : CoroutineContextKey<CoroutineDispatcher>
    public override val contextKey get() = CoroutineDispatcher
    public fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean
    public fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean

    override fun <T> contextualizeContinuation(continuation: CoroutineContinuation<T>): CoroutineContinuation<T> =
        DispatchedContinuation(continuation.context, this, continuation)
}

//--------------------- private impl ---------------------

private class DispatchedContinuation<T>(
    override val context: CoroutineContext,
    val dispatcher: CoroutineDispatcher,
    val continuation: Continuation<T>
): CoroutineContinuation<T> {
    override fun resume(value: T) {
        if (!dispatcher.dispatchResume(value, continuation))
            continuation.resume(value)
    }

    override fun resumeWithException(exception: Throwable) {
        if (!dispatcher.dispatchResumeWithException(exception, continuation))
            continuation.resumeWithException(exception)
    }
}
