package kotlinx.coroutines.experimental

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * User-specified name of coroutine.
 */
public data class CoroutineName(val name: String) : AbstractCoroutineContextElement(CoroutineName) {
    public companion object Key : CoroutineContext.Key<CoroutineName>
    override fun toString(): String = "CoroutineName($name)"
}
