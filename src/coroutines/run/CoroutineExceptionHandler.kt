package coroutines.run

import kotlinx.coroutines.experimental.Lifetime
import coroutines.context.CoroutineContext

fun handleCoroutineException(context: CoroutineContext, exception: Throwable) {
    context[CoroutineExceptionHandler]?.let {
        it.handleException(context, exception)
        return
    }
    // quit if successfully pushed exception as cancellation reason
    if (context[Lifetime]?.cancel(exception) ?: false) return
    // otherwise just dump stack trace
    // todo: don't dump trace if this exception is the cancellation reason that was passed from outside
    System.err.println("Unhandled exception in coroutine: $exception")
    exception.printStackTrace(System.err)
}

public interface CoroutineExceptionHandler : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineExceptionHandler>
    public fun handleException(context: CoroutineContext, exception: Throwable)
}
