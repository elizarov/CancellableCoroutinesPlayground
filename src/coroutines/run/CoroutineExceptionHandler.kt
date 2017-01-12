package coroutines.run

import coroutines.cancellable.Cancellable
import coroutines.context.CoroutineContext
import coroutines.context.CoroutineContextElement
import coroutines.context.CoroutineContextKey

public interface CoroutineExceptionHandler : CoroutineContextElement {
    companion object : CoroutineContextKey<CoroutineExceptionHandler>
    override val contextKey: CoroutineContextKey<*> get() = CoroutineExceptionHandler
    public fun handleException(context: CoroutineContext, exception: Throwable)
}

public object DefaultCoroutineExceptionHandler : CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        // quit if successfully pushed exception as cancellation reason
        if (context[Cancellable]?.cancel(exception) ?: false) return
        // otherwise just dump stack trace
        exception.printStackTrace(System.err)
    }
}