package context

import kotlin.coroutines.Continuation

public interface CoroutineDispatcher : CoroutineContext {
    companion object : CoroutineContextType<CoroutineDispatcher>
    public override val contextType get() = CoroutineDispatcher
    public fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean
    public fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean
}
