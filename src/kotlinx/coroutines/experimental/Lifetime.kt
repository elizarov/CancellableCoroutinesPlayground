package kotlinx.coroutines.experimental

import kotlinx.coroutines.experimental.util.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.util.LockFreeLinkedListNode
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// --------------- core lifetime interfaces ---------------

/**
 * An activity with a lifetime. It has two states: _active_ (initial state) and
 * _completed_ (final state). It can be _cancelled_ at any time with [cancel] function
 * that transitions it to the final completed state. A lifetime in the coroutine context
 * represents lifetime of the coroutine and it is active while the coroutine is
 * working. This class is thread-safe.
 */
public interface Lifetime : CoroutineContext.Element {
    public companion object Key : CoroutineContext.Key<Lifetime> {
        /**
         * Creates new lifetime.
         */
        public operator fun invoke(outer: Lifetime? = null) = LifetimeSupport(outer)
    }

    /**
     * Returns `true` when still active.
     */
    public val isActive: Boolean

    /**
     * Registers completion handler. The action depends on the state of this activity.
     * When activity is cancelled with [cancel], then the handler is immediately invoked
     * with a cancellation reason. Otherwise, handler will be invoked once when this
     * activity is complete (cancellation also is a form of completion).
     * The resulting [Registration] can be used to [Registration.unregister] if this
     * registration is no longer needed. There is no need to unregister after completion.
     */
    public fun onCompletion(handler: CompletionHandler): Registration

    /**
     * Cancel this activity with an optional cancellation reason. The result is `true` if activity was
     * cancelled as a result of this invocation and `false` otherwise (if it was already cancelled).
     */
    public fun cancel(reason: Throwable? = null): Boolean

    /**
     * Registration object for [onCompletion]. It can be used to [unregister] if needed.
     * There is no need to unregister after completion.
     */
    public interface Registration {
        /**
         * Unregisters completion handler.
         */
        public fun unregister()
    }
}

typealias CompletionHandler = (Throwable?) -> Unit

typealias CancellationException = CancellationException

/**
 * Unregisters a specified [registration] when this activity is complete.
 * This is a shortcut for the following code with slightly more efficient implementation (one fewer object created).
 * ```
 * onCompletion { registration.unregister() }
 * ```
 */
public fun Lifetime.unregisterOnCompletion(registration: Lifetime.Registration): Lifetime.Registration =
    onCompletion(UnregisterOnCompletion(this, registration))

// --------------- utility classes to simplify cancellable implementation

/**
 * An concrete implementation of [Lifetime].
 * It is optionally a part of an outer lifetime.
 * This lifetime is cancelled when the outer is complete, but not vise-versa.
 *
 * This is an open class designed for extension by more specific classes that might augment the
 * state and mare store addition state information for completed activities, like their result values.
 */
@Suppress("LeakingThis")
public open class LifetimeSupport(
    outer: Lifetime? = null
) : AbstractCoroutineContextElement(Lifetime), Lifetime {
    // keeps a stack of cancel listeners or a special CANCELLED, other values denote completed scope
    @Volatile
    private var state: Any? = Active() // will drop the list on cancel

    // directly pass HandlerNode to outer scope to optimize one closure object (see makeNode)
    private val registration: Lifetime.Registration? = outer?.onCompletion(CancelOnCompletion(outer, this))

    protected companion object {
        @JvmStatic
        private val STATE: AtomicReferenceFieldUpdater<LifetimeSupport, Any?> =
            AtomicReferenceFieldUpdater.newUpdater(LifetimeSupport::class.java, Any::class.java, "state")

        @JvmStatic
        val CANCELLED = Cancelled(null)
    }

    protected fun getState(): Any? = state

    protected fun compareAndSetState(expect: Any?, update: Any?): Boolean {
        require(update !is Active) // cannot go back to active state
        if (!STATE.compareAndSet(this, expect, update)) return false
        if (expect is Active) {
            // just made an active -> inactive transition
            registration?.unregister()
            complete(expect, update)
        }
        return true
    }

    public override val isActive: Boolean get() = state is Active

    public override fun onCompletion(handler: CompletionHandler): Lifetime.Registration {
        var nodeCache: Node? = null
        while (true) { // lock-free loop on state
            val state = this.state
            if (state !is Active) {
                handler((state as? Cancelled)?.reason)
                return EmptyRegistration
            }
            val node = nodeCache ?: makeNode(handler).apply { nodeCache = this }
            if (state.addLastIf(node) { this.state == state }) return node
        }
    }

    public override fun cancel(reason: Throwable?): Boolean {
        while (true) { // lock-free loop on state
            val state = this.state as? Active ?: return false // quit if not active anymore
            val update = if (reason == null) CANCELLED else Cancelled(reason)
            if (compareAndSetState(state, update)) return true
        }
    }

    private fun complete(oldState: Active, newState: Any?)  {
        var closeException: Throwable? = null
        val reason = (newState as? Cancelled)?.reason
        oldState.forEach<Node> { node ->
            try {
                node.invoke(reason)
            } catch (ex: Throwable) {
                if (closeException == null) closeException = ex else closeException!!.addSuppressed(ex)
            }
        }
        afterCancel(newState, closeException)
    }

    protected open fun afterCancel(newState: Any?, closeException: Throwable?) {
        if (closeException != null) throw closeException
    }

    private fun makeNode(handler: CompletionHandler): Node =
            handler as? Node ?: InvokeOnCompletion(this, handler)

    protected open class Cancelled(val reason: Throwable?)

    internal class Active : LockFreeLinkedListHead()
}

private abstract class Node(
    val lifetime: Lifetime
) : LockFreeLinkedListNode(), Lifetime.Registration, CompletionHandler {
    override fun unregister() {
        // this is an object-allocation optimization -- do not remove if lifetime is not active anymore
        if (lifetime.isActive)
            remove()
    }

    override abstract fun invoke(reason: Throwable?)
}

private class InvokeOnCompletion(
    lifetime: Lifetime,
    val handler: CompletionHandler
) : Node(lifetime)  {
    override fun invoke(reason: Throwable?) = handler.invoke(reason)
    override fun toString() = "InvokeOnCompletion[${handler::class.java.name}@${Integer.toHexString(System.identityHashCode(handler))}]"
}

private class UnregisterOnCompletion(
    lifetime: Lifetime,
    val registration: Lifetime.Registration
) : Node(lifetime) {
    override fun invoke(reason: Throwable?) = registration.unregister()
    override fun toString(): String = "UnregisterOnCompletion[$registration]"
}

private class CancelOnCompletion(
    outerLifetime: Lifetime,
    val innerLifetime: Lifetime
) : Node(outerLifetime) {
    override fun invoke(reason: Throwable?) { innerLifetime.cancel(reason) }
    override fun toString(): String = "CancelOnCompletion[$innerLifetime]"
}

private object EmptyRegistration : Lifetime.Registration {
    override fun unregister() {}
    override fun toString(): String = "EmptyRegistration"
}
