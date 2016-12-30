package coroutines.context

//--------------------- api ---------------------

public interface CoroutineContext {
    public val contextType: CoroutineContextType<*>

    @Suppress("UNCHECKED_CAST")
    public operator fun <CT : CoroutineContext> get(contextType: CoroutineContextType<CT>): CT? =
        if (this.contextType == contextType) this as CT else null

    public fun <R> fold(initial: R, operation: (R, CoroutineContext) -> R): R =
        operation(initial, this)

    public operator fun plus(other: CoroutineContext): CoroutineContext =
        other.fold(this) { a, b ->
            val removed = a.remove(b.contextType)
            if (removed == EmptyCoroutineContext) b else CombinedContext(b, removed)
        }

    public fun remove(contextType: CoroutineContextType<*>): CoroutineContext =
        if (this.contextType == contextType) EmptyCoroutineContext else this
}

public interface CoroutineContextType<CT : CoroutineContext>

public object EmptyCoroutineContext : CoroutineContext, CoroutineContextType<EmptyCoroutineContext> {
    public override val contextType = EmptyCoroutineContext

    override fun <CT : CoroutineContext> get(contextType: CoroutineContextType<CT>): CT? = null
    override fun <R> fold(initial: R, operation: (R, CoroutineContext) -> R): R = initial
    override fun plus(other: CoroutineContext): CoroutineContext = other
    override fun remove(contextType: CoroutineContextType<*>): CoroutineContext = this
}

//--------------------- private impl ---------------------

// this class is not exposed, but is hidden inside implementations
private class CombinedContext(val cc: CoroutineContext, val next: CoroutineContext) : CoroutineContext {
    companion object : CoroutineContextType<CombinedContext>
    override val contextType get() = CombinedContext

    override fun <CT : CoroutineContext> get(contextType: CoroutineContextType<CT>): CT? {
        var cur = this
        while (true) {
            cur.cc[contextType]?.let { return it }
            val next = cur.next
            if (next is CombinedContext) {
                cur = next
            } else {
                return next[contextType]
            }
        }
    }

    override fun <R> fold(initial: R, operation: (R, CoroutineContext) -> R): R {
        var result = initial
        var cur = this
        while (true) {
            result = operation(result, cur.cc)
            val next = cur.next
            if (next is CombinedContext) {
                cur = next
            } else {
                result = operation(result, cur.next)
                break
            }
        }
        return result
    }

    override fun remove(contextType: CoroutineContextType<*>): CoroutineContext {
        cc[contextType]?.let { return next }
        val newNext = next.remove(contextType)
        return when (newNext) {
            next -> this
            EmptyCoroutineContext -> cc
            else -> CombinedContext(cc, newNext)
        }
    }
}
