package coroutines.context

/**
 * Marks coroutine context element that intercepts coroutine continuations.
 */
public interface ContinuationInterceptor : CoroutineContextElement {
    /**
     * The key that defines *the* context interceptor.
     */
    companion object : CoroutineContextKey<ContinuationInterceptor>

    /**
     * Intercepts the given [continuation] by wrapping it. Application code should not call this method directly as
     * it is invoked by coroutines framework appropriately and the resulting continuations are efficiently cached.
     */
    public fun <T> interceptContinuation(continuation: CoroutineContinuation<T>): CoroutineContinuation<T>
}

