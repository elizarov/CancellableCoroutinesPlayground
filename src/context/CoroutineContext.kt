package context

import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

//--------------------- api ---------------------

public interface CoroutineContext {
    public val contextType: KClass<out CoroutineContext>
}

public fun <T> (suspend  () -> T).startCoroutine(
    completion: Continuation<T>,
    context: CoroutineContext? = null
) {
    // todo:
}

public inline fun <reified CT : CoroutineContext> CoroutineContext?.find(): CT? {
    if (this == null) return null // fast path #1 -- empty context
    if (this is CT) return this // fast path #2 -- a single entry in the context
    return findImpl(CT::class) // slow path -- something else or combined context
}

public inline fun <reified CT : CoroutineContext> CoroutineContext?.remove(): CoroutineContext? {
    if (this == null) return null // fast path #1 -- empty context
    if (this is CT) return null // fast path #2 -- a single entry in the context
    return removeImpl(CT::class) ?: this
}

public operator fun <CT : CoroutineContext> CoroutineContext?.plus(other: CT): CoroutineContext {
    val contestType = other.contextType
    require(contestType != CoroutineContext::class) { "Can only add explicitly typed entries to context.CoroutineContext" }
    require(contestType.isInstance(other)) { "Context entry must be an instance of its contestType" }
    if (this == null) return other // fast path #1 -- adding to an empty context
    if (contestType.isInstance(this)) return other // fast path #2 -- replacing context of the same type
    return CombinedContext(other, removeImpl(contestType) ?: this)
}

//--------------------- internal ---------------------

// this class is not exposed, but is hidden inside implementations
private class CombinedContext(val cc: CoroutineContext, val next: CoroutineContext) : CoroutineContext {
    override val contextType: KClass<out CoroutineContext> get() = CoroutineContext::class
}

@PublishedApi
internal fun <T : CoroutineContext> CoroutineContext?.findImpl(contextType: KClass<T>): T? {
    var cur = this as? CombinedContext ?: return null
    while (true) {
        contextType.safeCast(cur.cc)?.let { return it }
        val next = cur.next
        if (next is CombinedContext) {
            cur = next
        } else {
            return contextType.safeCast(next)
        }
    }
}

// removes an entry of the contextType from context.CombinedContext or returns [null] if it is not found
@PublishedApi
internal fun <T: CoroutineContext> CoroutineContext.removeImpl(contextType: KClass<T>): CoroutineContext? {
    val cur = this as? CombinedContext ?: return null
    contextType.safeCast(cur.cc)?.let { return cur.next }
    contextType.safeCast(cur.next)?.let { return cur.cc }
    val newNext = cur.next.removeImpl(contextType) ?: return null
    return CombinedContext(cur.cc, newNext)
}