package kotlinx.coroutines.experimental

import kotlinx.coroutines.experimental.spi.DefaultCoroutineContextProvider
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.*

private const val DEBUG_PROPERTY_NAME = "kotlinx.coroutines.debug"
private val DEBUG = CoroutineId::class.java.desiredAssertionStatus() || System.getProperty(DEBUG_PROPERTY_NAME) != null
private val COROUTINE_ID = AtomicLong()

@PublishedApi
internal val CURRENT_CONTEXT = ThreadLocal<CoroutineContext>()

/**
 * Creates context for the new coroutine with user-specified overrides from [context] parameter.
 * This function shall be used to start new coroutines.
 *
 * **Debugging facilities:** When assertions are enabled or when "kotlinx.coroutines.debug" system property
 * is set, every coroutine is assigned a unique consecutive identifier. Every thread that executes
 * a coroutine has its name modified to include the identifier of the currently currently coroutine.
 */
public fun newCoroutineContext(context: CoroutineContext = EmptyCoroutineContext): CoroutineContext =
    merge(CURRENT_CONTEXT.get() ?: loadCurrentContext(), context).let {
        if (DEBUG) it + CoroutineId(COROUTINE_ID.incrementAndGet()) else it
    }

/**
 * Executes a block using a given default coroutine context.
 * This context affects all new coroutines that are started withing the block.
 * The specified [context] is merged onto the current coroutine context (if any).
 */
public inline fun <T> withDefaultCoroutineContext(context: CoroutineContext, block: () -> T): T {
    val oldContext = CURRENT_CONTEXT.get()
    val oldName = updateContext(oldContext, context)
    try {
        return block()
    } finally {
        restoreContext(oldContext, oldName)
    }
}

/**
 * Executes a suspending block with a given coroutine context.
 * It immediately application dispatcher of the new context, shifting execution of the block into the
 * different thread inside the block, and back when it completes.
 * The specified [context] is merged onto the current coroutine context.
 */
public suspend fun <T> withCoroutineContext(context: CoroutineContext, block: suspend () -> T): T =
    suspendCoroutine { cont ->
        block.startCoroutine(object : Continuation<T> by cont {
            override val context: CoroutineContext = merge(cont.context, context)
        })
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

@PublishedApi
internal fun merge(current: CoroutineContext?, context: CoroutineContext) =
    (current ?: DefaultContext).let { cc ->
        if (context == EmptyCoroutineContext) cc else normalizeContext(cc + context)
    }

private fun normalizeContext(context: CoroutineContext): CoroutineContext {
    val interceptor = context[ContinuationInterceptor] ?: return context + DefaultContext
    // use default context interceptor by default
    check(interceptor is CoroutineDispatcher) {
        "Continuation interceptor must extend CoroutineDispatcher to be used here, but ${interceptor::class} was found"
    }
    return context
}

@PublishedApi
internal fun updateContext(oldContext: CoroutineContext?, context: CoroutineContext): String? {
    if (context === oldContext) return null
    val newContext = merge(oldContext, context)
    CURRENT_CONTEXT.set(newContext)
    if (!DEBUG) return null
    if (newContext === oldContext) return null
    val new = newContext[CoroutineId] ?: return null
    val old = oldContext?.get(CoroutineId)
    if (new === old) return null
    val currentThread = Thread.currentThread()
    val oldName = currentThread.name
    val coroutineName = newContext[CoroutineName]?.name ?: "coroutine"
    currentThread.name = buildString(oldName.length + coroutineName.length + 10) {
        append(oldName)
        append(" @")
        append(coroutineName)
        append('#')
        append(new.id)
    }
    return oldName
}

@PublishedApi
internal fun restoreContext(oldContext: CoroutineContext?, oldName: String?) {
    if (oldName != null) Thread.currentThread().name = oldName
    CURRENT_CONTEXT.set(oldContext)
}

private object DefaultContext : CoroutineDispatcher() {
    override fun isDispatchNeeded(): Boolean = false
    override fun dispatch(block: Runnable) { throw UnsupportedOperationException() }
}

private class CoroutineId(val id: Long) : AbstractCoroutineContextElement(CoroutineId) {
    companion object Key : CoroutineContext.Key<CoroutineId>
    override fun toString(): String = "CoroutineId($id)"
}
