package coroutines.context

//--------------------- api ---------------------

public interface CoroutineContext {
    public val contextType: CoroutineContextType<*>
}

public interface CoroutineContextType<CT : CoroutineContext>

public object EmptyCoroutineContext : CoroutineContext, CoroutineContextType<EmptyCoroutineContext> {
    public override val contextType = EmptyCoroutineContext
}

public operator fun <CT : CoroutineContext> CoroutineContext.get(contextType: CoroutineContextType<CT>): CT? {
    if (this == EmptyCoroutineContext) return null // fast path #1 -- empty coroutines.context
    safeCast(contextType)?.let { return it }  // fast path #2 -- single item of this type
    return getImpl(contextType) // slow path -- something else or combined coroutines.context
}

public operator fun CoroutineContext.plus(other: CoroutineContext): CoroutineContext {
    if (other == EmptyCoroutineContext) return this // fast path #1 -- adding an empty coroutines.context
    if (this == EmptyCoroutineContext) return other // fast path #2 -- adding to an empty coroutines.context
    val contextType = other.contextType
    if (contextType == CombinedContext) return plusCombined(other as CombinedContext) // very slow path -- add combined
    safeCast(contextType)?.let { return other } // fast path #3 -- replacing coroutines.context of the same type
    return CombinedContext(other, removeImpl(contextType) ?: this)
}

public fun <CT : CoroutineContext> CoroutineContext.remove(contextType: CoroutineContextType<CT>): CoroutineContext {
    if (this == EmptyCoroutineContext) return EmptyCoroutineContext // fast path #1 -- empty coroutines.context
    safeCast(contextType)?.let { return EmptyCoroutineContext } // fast path #2 -- a single entry in the coroutines.context
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

// removes an entry of the contextType from coroutines.context.CombinedContext or returns [null] if it is not found
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

