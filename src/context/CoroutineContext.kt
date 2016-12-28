package context

import kotlin.coroutines.Continuation

//--------------------- api ---------------------

public interface CoroutineContext {
    public val contextType: CoroutineContextType<*>
}

public interface CoroutineContextType<CT : CoroutineContext>

public fun <T> (suspend  () -> T).startCoroutine(
    completion: Continuation<T>,
    context: CoroutineContext? = null
) {
    // todo:
}

public operator fun <CT : CoroutineContext> CoroutineContext?.get(contextType: CoroutineContextType<CT>): CT? {
    if (this == null) return null // fast path #1 -- empty context
    safeCast(contextType)?.let { return it }  // fast path #2 -- single item of this type
    return getImpl(contextType) // slow path -- something else or combined context
}

public operator fun <CT : CoroutineContext> CoroutineContext?.plus(other: CT): CoroutineContext {
    val contextType = other.contextType
    if (this == null) return other // fast path #1 -- adding to an empty context
    if (contextType == CombinedContext) return plusCombined(other as CombinedContext) // slow path -- add combined
    safeCast(contextType)?.let { return other } // fast path #2 -- replacing context of the same type
    return CombinedContext(other, removeImpl(contextType) ?: this)
}

public fun <CT : CoroutineContext> CoroutineContext?.remove(contextType: CoroutineContextType<CT>): CoroutineContext? {
    if (this == null) return null // fast path #1 -- empty context
    safeCast(contextType)?.let { return null } // fast path #2 -- a single entry in the context
    return removeImpl(contextType) ?: this
}

//--------------------- private impl ---------------------

// this class is not exposed, but is hidden inside implementations
private class CombinedContext(val cc: CoroutineContext, val next: CoroutineContext) : CoroutineContext {
    companion object : CoroutineContextType<CombinedContext>
    override val contextType get() = CombinedContext
}

@Suppress("UNCHECKED_CAST")
private fun <CT : CoroutineContext> CoroutineContext.safeCast(contextType: CoroutineContextType<CT>): CT? =
    if (this.contextType == contextType) this as CT else null

private fun <CT : CoroutineContext> CoroutineContext?.getImpl(contextType: CoroutineContextType<CT>): CT? {
    var cur = this as? CombinedContext ?: return null
    while (true) {
        cur.cc.safeCast(contextType)?.let { return it }
        val next = cur.next
        if (next is CombinedContext) {
            cur = next
        } else {
            return next.safeCast(contextType)
        }
    }
}

// removes an entry of the contextType from context.CombinedContext or returns [null] if it is not found
private fun <CT : CoroutineContext> CoroutineContext.removeImpl(contextType: CoroutineContextType<CT>): CoroutineContext? {
    val cur = this as? CombinedContext ?: return null
    cur.cc.safeCast(contextType)?.let { return cur.next }
    cur.next.safeCast(contextType)?.let { return cur.cc }
    val newNext = cur.next.removeImpl(contextType) ?: return null
    return CombinedContext(cur.cc, newNext)
}

private fun CoroutineContext.plusCombined(other: CombinedContext): CoroutineContext {
    var result = this
    var cur = other
    while (true) {
        result += cur.cc
        val next = cur.next
        if (next is CombinedContext) {
            cur = next
        } else {
            result += cur.next
            break
        }
    }
    return result
}

