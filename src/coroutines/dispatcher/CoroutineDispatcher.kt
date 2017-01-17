package coroutines.dispatcher

import coroutines.context.*
import kotlin.coroutines.Continuation

public abstract class CoroutineDispatcher : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    public abstract fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean
    public abstract fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean

    private val combiner: (CoroutineContinuation<Any>, CoroutineContext.Element) -> CoroutineContinuation<Any> = { c, e ->
        if (e != this && e is ContinuationInterceptor) e.interceptContinuation(c) else c
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> combiner(): (CoroutineContinuation<T>, CoroutineContext.Element) -> CoroutineContinuation<T> =
        combiner as ((CoroutineContinuation<T>, CoroutineContext.Element) -> CoroutineContinuation<T>)

    public override fun <T> interceptContinuation(continuation: CoroutineContinuation<T>): CoroutineContinuation<T> =
        DispatchedContinuation(this, continuation.context.fold(continuation, combiner()))
}

//--------------------- private impl ---------------------

private class DispatchedContinuation<T>(
    val dispatcher: CoroutineDispatcher,
    val continuation: CoroutineContinuation<T>
): CoroutineContinuation<T> {
    override val context: CoroutineContext = continuation.context

    override fun resume(value: T) {
        if (!dispatcher.dispatchResume(value, continuation))
            continuation.resume(value)
    }

    override fun resumeWithException(exception: Throwable) {
        if (!dispatcher.dispatchResumeWithException(exception, continuation))
            continuation.resumeWithException(exception)
    }
}
