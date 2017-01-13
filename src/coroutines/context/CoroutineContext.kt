package coroutines.context

/**
 * Persistent context for the coroutine. It is an indexed set of [CoroutineContextElement] instances.
 * An indexed set is a mix between a set and a map.
 * Every element in this set has a unique [CoroutineContextKey].
 */
public interface CoroutineContext {
    /**
     * Returns an element with the given [key] in this context or `null`.
     */
    public operator fun <E : CoroutineContextElement> get(key: CoroutineContextKey<E>): E?

    /**
     * Accumulates entries of this context starting with [initial] value and applying [operation]
     * from left to right to current accumulator value and each element of this context.
     */
    public fun <R> fold(initial: R, operation: (R, CoroutineContextElement) -> R): R

    /**
     * Returns a context containing elements from this context and elements from [context] context.
     * The elements from this context with the same key as in the other one are dropped.
     */
    public operator fun plus(context: CoroutineContext): CoroutineContext

    /**
     * Returns a context containing elements from this context, but without an element with
     * the specified [key].
     */
    public fun minusKey(key: CoroutineContextKey<*>): CoroutineContext
}

/**
 * An element of the [CoroutineContext]. An element of the coroutine context is a singleton context by itself.
 */
public interface CoroutineContextElement : CoroutineContext {
    /**
     * A key of this coroutine context element.
     */
    public val contextKey: CoroutineContextKey<*>
}

/**
 * Key for the entries of [CoroutineContext]. [CT] is a type of entry with this key.
 */
public interface CoroutineContextKey<CT : CoroutineContextElement>

