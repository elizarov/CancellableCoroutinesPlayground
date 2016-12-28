package context

import kotlin.coroutines.Continuation
import kotlin.reflect.KClass

public interface CoroutineDispatcher : CoroutineContext {
    public override val contextType: KClass<out CoroutineContext> get() = CoroutineDispatcher::class
    public fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean
    public fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean
}
