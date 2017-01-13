package coroutines.context

//--------------------- api ---------------------

/**
 * Persistent context for the coroutine. It is an indexed set of [CoroutineContextElement] instances.
 * An indexed set is a mix between a set and a map.
 * Every element in this set has a unique [CoroutineContextKey].
 * The context preserves order of its elements, but its equality is set-based.
 */
public interface CoroutineContext {
    /**
     * Returns an element with the given [contextKey] in this context or `null`.
     */
    public operator fun <CT : CoroutineContextElement> get(contextKey: CoroutineContextKey<CT>): CT?

    /**
     * Returns `true` when this context contains the particular element.
     */
    public operator fun contains(element: CoroutineContextElement): Boolean

    /**
     * Accumulates entries of this context starting with [initial] value and applying [operation]
     * from left to right to current accumulator value and each element of this context.
     */
    public fun <R> fold(initial: R, operation: (R, CoroutineContextElement) -> R): R

    /**
     * Returns a context containing elements from this context and elements from [other] context.
     * The elements from this context with the same key as in the other one are dropped.
     */
    public operator fun plus(other: CoroutineContext): CoroutineContext

    /**
     * Returns a context contention elements from this context, but without an element with
     * the specified [contextKey].
     */
    public fun minusKey(contextKey: CoroutineContextKey<*>): CoroutineContext
}

/**
 * An element of the [CoroutineContext]. An element of the coroutine context is a singleton context by itself.
 */
public interface CoroutineContextElement : CoroutineContext {
    /**
     * A key of this coroutine context element.
     */
    public val contextKey: CoroutineContextKey<*>

    @Suppress("UNCHECKED_CAST")
    public override operator fun <CT : CoroutineContextElement> get(contextKey: CoroutineContextKey<CT>): CT? =
        if (this.contextKey == contextKey) this as CT else null

    public override operator fun contains(element: CoroutineContextElement): Boolean =
        this == element

    public override fun <R> fold(initial: R, operation: (R, CoroutineContextElement) -> R): R =
        operation(initial, this)

    public override operator fun plus(other: CoroutineContext): CoroutineContext =
        plusImpl(other)

    public override fun minusKey(contextKey: CoroutineContextKey<*>): CoroutineContext =
        if (this.contextKey == contextKey) EmptyCoroutineContext else this
}

/**
 * Key for the entries of [CoroutineContext]. [CT] is a type of entry with this key.
 */
public interface CoroutineContextKey<CT : CoroutineContextElement>

/**
 * An empty coroutine context.
 */
public object EmptyCoroutineContext : CoroutineContext {
    public override fun <CT : CoroutineContextElement> get(contextKey: CoroutineContextKey<CT>): CT? = null
    public override operator fun contains(element: CoroutineContextElement): Boolean = false
    public override fun <R> fold(initial: R, operation: (R, CoroutineContextElement) -> R): R = initial
    public override fun plus(other: CoroutineContext): CoroutineContext = other
    public override fun minusKey(contextKey: CoroutineContextKey<*>): CoroutineContext = this
    public override fun hashCode(): Int = 0
    public override fun toString(): String = "EmptyCoroutineContext"
}

//--------------------- private impl ---------------------

// this class is not exposed, but is hidden inside implementations
// this is a left-biased list, so that `plus` works naturally
private class CombinedContext(val left: CoroutineContext, val element: CoroutineContextElement) : CoroutineContext {
    override fun <CT : CoroutineContextElement> get(contextKey: CoroutineContextKey<CT>): CT? {
        var cur = this
        while (true) {
            cur.element[contextKey]?.let { return it }
            val next = cur.left
            if (next is CombinedContext) {
                cur = next
            } else {
                return next[contextKey]
            }
        }
    }

    public override operator fun contains(element: CoroutineContextElement): Boolean {
        var cur = this
        while (true) {
            if (cur.element == element) return true
            val next = cur.left
            if (next is CombinedContext) {
                cur = next
            } else {
                return next == element
            }
        }
    }

    public override fun <R> fold(initial: R, operation: (R, CoroutineContextElement) -> R): R =
        operation(left.fold(initial, operation), element)

    public override operator fun plus(other: CoroutineContext): CoroutineContext =
        plusImpl(other)

    public override fun minusKey(contextKey: CoroutineContextKey<*>): CoroutineContext {
        element[contextKey]?.let { return left }
        val newLeft = left.minusKey(contextKey)
        return when (newLeft) {
            left -> this
            EmptyCoroutineContext -> element
            else -> CombinedContext(newLeft, element)
        }
    }

    private fun size(): Int =
        if (left is CombinedContext) left.size() + 1 else 2

    private fun containsAll(other: CombinedContext): Boolean {
        var cur = other
        while (true) {
            if (!contains(cur.element)) return false
            val next = cur.left
            if (next is CombinedContext) {
                cur = next
            } else {
                return contains(next as CoroutineContextElement)
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CombinedContext && other.size() == size() && other.containsAll(this)

    override fun hashCode(): Int = left.hashCode() + element.hashCode()

    override fun toString(): String =
        "[" + fold("") { acc, element ->
            if (acc.isEmpty()) element.toString() else acc + ", " + element
        } + "]"
}

private fun CoroutineContext.plusImpl(other: CoroutineContext): CoroutineContext =
    if (other == EmptyCoroutineContext) this else // fast path -- avoid lambda creation
        other.fold(this) { acc, element ->
            val removed = acc.minusKey(element.contextKey)
            if (removed == EmptyCoroutineContext) element else {
                // make sure interceptor is always last in the context (and thus is fast to get when present)
                val interceptor = removed[ContinuationInterceptor]
                if (interceptor == null) CombinedContext(removed, element) else {
                    val left = removed.minusKey(ContinuationInterceptor)
                    if (left == EmptyCoroutineContext) CombinedContext(element, interceptor) else
                        CombinedContext(CombinedContext(left, element), interceptor)
                }
            }
        }

