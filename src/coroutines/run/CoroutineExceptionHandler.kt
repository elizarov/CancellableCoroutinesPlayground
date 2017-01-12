package coroutines.run

import coroutines.cancellable.Cancellable
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContextElement
import coroutines.context.CoroutineContextKey

fun handleCoroutineException(context: CoroutineContext, exception: Throwable) {
    context[CoroutineExceptionHandler]?.let {
        it.handleException(context, exception)
        return
    }
    // quit if successfully pushed exception as cancellation reason
    if (context[Cancellable]?.cancel(exception) ?: false) return
    // otherwise just dump stack trace
    // todo: don't dump trace if this exception is the cancellation reason that was passed from outside
    System.err.println("Unhandled exception in coroutine: $exception")
    exception.printStackTrace(System.err)
}

public interface CoroutineExceptionHandler : CoroutineContextElement {
    companion object : CoroutineContextKey<CoroutineExceptionHandler>
    override val contextKey: CoroutineContextKey<*> get() = CoroutineExceptionHandler
    public fun handleException(context: CoroutineContext, exception: Throwable)
}
