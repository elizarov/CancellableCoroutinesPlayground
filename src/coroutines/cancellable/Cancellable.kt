package coroutines.cancellable

import coroutines.context.CoroutineContextElement
import coroutines.context.CoroutineContextKey
import coroutines.util.LockFreeLinkedListHead
import coroutines.util.LockFreeLinkedListNode
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

// --------------- core cancellable interfaces ---------------

/**
 * A cancellable activity. This class is thread-safe.
 */
public interface Cancellable : CoroutineContextElement {
    companion object : CoroutineContextKey<Cancellable>
    public override val contextKey get() = Cancellable

    /**
     * Returns `true` when this activity was cancelled.
     */
    public val isCancelled: Boolean

    /**
     * Registers cancel handler. The action depends on the state of this activity.
     * When activity [isCancelled], then the handler is immediately invoked with a cancellation reason.
     * Otherwise, handler will be invoked once later when this activity is cancelled.
     * The resulting [CancelRegistration] can be used to [CancelRegistration.unregisterCancelHandler] if this
     * registration is no longer needed. There is no need to unregister after cancellation.
     */
    public fun registerCancelHandler(handler: CancelHandler): CancelRegistration

    /**
     * Cancel this activity with an optional cancellation reason.
     */
    public fun cancel(reason: Throwable? = null)
}

typealias CancelHandler = (Throwable?) -> Unit

public interface CancelRegistration {
    fun unregisterCancelHandler()
}

typealias CancellationException = CancellationException

// --------------- utility classes to simplify cancellable implementation

/**
 * Cancellation scope is an actual implementation of [Cancellable] activity.
 * It is optionally a part of an outer cancellable activity.
 * This scope is cancelled when the [outer] is cancelled, but not vise-versa.
 *
 * This is an open class designed for extension by more specific cancellable classes that might augment the
 * state and mare store addition state information for cancelled activities, like their result values.
 */
public open class CancellationScope(outer: Cancellable? = null) : Cancellable {
    // keeps a stack of cancel listeners or a special CANCELLED, other values denote completed scope
    @Volatile
    private var state: Any? = Active() // will drop the list on cancel

    // directly pass HandlerNode to outer scope to optimize one closure object (see makeNode)
    private val registration: CancelRegistration? = outer?.registerCancelHandler(object : HandlerNode() {
        override fun invoke(failure: Throwable?) = cancel(failure)
    })

    protected companion object {
        @JvmStatic
        private val STATE: AtomicReferenceFieldUpdater<CancellationScope, Any?> =
            AtomicReferenceFieldUpdater.newUpdater(CancellationScope::class.java, Any::class.java, "state")

        @JvmStatic
        val CANCELLED = Cancelled(null)
    }

    protected fun getState(): Any? = state

    protected fun compareAndSetState(expect: Any?, update: Any?): Boolean {
        require(update !is Active) // cannot go back to active state
        if (!STATE.compareAndSet(this, expect, update)) return false
        if (expect is Active) {
            // made active -> inactive transition
            registration?.unregisterCancelHandler()
            onCancel(expect, update)
        }
        return true
    }

    public override val isCancelled: Boolean get() = state !is Active

    public override fun registerCancelHandler(handler: CancelHandler): CancelRegistration {
        var nodeCache: HandlerNode? = null
        while (true) { // lock-free loop on state
            val state = this.state
            if (state !is Active) {
                handler((state as? Cancelled)?.reason)
                return NoCancelRegistration
            }
            val node = nodeCache ?: makeNode(handler).apply { nodeCache = this }
            if (state.addLastIf(node) { this.state == state }) return node
        }
    }

    public override fun cancel(reason: Throwable?) {
        while (true) { // lock-free loop on state
            val state = this.state as? Active ?: return // quit if not active anymore
            val update = if (reason == null) CANCELLED else Cancelled(reason)
            if (compareAndSetState(state, update)) return
        }
    }

    private fun onCancel(oldState: Active, newState: Any?)  {
        var closeException: Throwable? = null
        val reason = (newState as? Cancelled)?.reason
        oldState.forEach<HandlerNode> { node ->
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

    private fun makeNode(handler: CancelHandler): HandlerNode = handler as? HandlerNode ?:
        object : HandlerNode() {
            override fun invoke(failure: Throwable?) = handler.invoke(failure)
        }

    protected open class Cancelled(val reason: Throwable?)

    protected class Active : LockFreeLinkedListHead()

    private abstract class HandlerNode : LockFreeLinkedListNode(), CancelRegistration, CancelHandler {
        override fun unregisterCancelHandler() = remove()
        override abstract fun invoke(failure: Throwable?)
    }

    protected object NoCancelRegistration : CancelRegistration {
        override fun unregisterCancelHandler() {}
    }
}


