package coroutines.context

import kotlin.coroutines.Continuation

public interface CoroutineDispatcher : CoroutineContextElement {
    companion object : CoroutineContextKey<CoroutineDispatcher>
    public override val contextKey get() = CoroutineDispatcher
    public fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean
    public fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean
}
