package kotlinx.coroutines.experimental

import kotlinx.coroutines.experimental.spi.DefaultCoroutineContextProvider
import java.util.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@PublishedApi
internal val CURRENT_CONTEXT = ThreadLocal<CoroutineContext>()

/**
 * Returns context of the current coroutine or default context of this thread with user-specified overrides
 * from [context] parameters. This function shall be used to start new coroutines.
 */
public fun currentCoroutineContext(context: CoroutineContext = EmptyCoroutineContext): CoroutineContext =
    merge(CURRENT_CONTEXT.get() ?: loadCurrentContext(), context)

/**
 * Executes a block with a given coroutine context.
 */
public inline fun <T> withCoroutineContext(context: CoroutineContext, block: () -> T): T {
    val old = CURRENT_CONTEXT.get()
    CURRENT_CONTEXT.set(normalizeContext(context))
    try {
        return block()
    } finally {
        CURRENT_CONTEXT.set(old)
    }
}

private fun loadCurrentContext(): CoroutineContext {
    var result: CoroutineContext? = null
    val currentThread = Thread.currentThread()
    for (provider in ServiceLoader.load(DefaultCoroutineContextProvider::class.java)) {
        result = provider.getDefaultCoroutineContext(currentThread)
        if (result != null) {
            result = normalizeContext(result)
            break
        }
    }
    if (result == null) result = DefaultContext
    CURRENT_CONTEXT.set(result)
    return result
}

private fun merge(current: CoroutineContext, context: CoroutineContext) =
    if (context == EmptyCoroutineContext) current else normalizeContext(current + context)

@PublishedApi
internal fun normalizeContext(context: CoroutineContext): CoroutineContext {
    val interceptor = context[ContinuationInterceptor]
    if (interceptor == null) return context + DefaultContext // use default context interceptor by default
    check(interceptor is CoroutineDispatcher) {
        "Continuation interceptor must extend CoroutineDispatcher to be used here, but ${interceptor::class} was found"
    }
    return context
}

private object DefaultContext : CoroutineDispatcher() {
    override fun isDispatchNeeded(): Boolean = false
    override fun dispatch(block: Runnable) { throw UnsupportedOperationException() }
}
