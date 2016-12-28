package context

import kotlin.coroutines.Continuation

public interface SuspendedCoroutine<T> : Continuation<T> {
    public val context: CoroutineContext?
    public operator fun plusAssign(other: CoroutineContext)
    public fun <CT : CoroutineContext> remove(contextType: CoroutineContextType<CT>)
}
