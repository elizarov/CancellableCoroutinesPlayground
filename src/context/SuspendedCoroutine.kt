package context

import kotlin.coroutines.Continuation
import kotlin.reflect.KClass

public interface SuspendedCoroutine<T> : Continuation<T> {
    public val context: CoroutineContext?
    public operator fun plusAssign(other: CoroutineContext)
    public fun remove(contextType: KClass<out CoroutineContext>)
}

public inline fun <reified CT : CoroutineContext> SuspendedCoroutine<*>.remove() =
    remove(CT::class)
