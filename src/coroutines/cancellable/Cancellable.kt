package coroutines.cancellable

import coroutines.context.CoroutineContextElement
import coroutines.context.CoroutineContextKey
import coroutines.util.LockFreeLinkedListHead
import coroutines.util.LockFreeLinkedListNode
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

// --------------- core cancellable interfaces ---------------

/**
 * A cancellable activity. The state transition diagram of cancellable activity is:
 * ```
 *     +----------+     +-----------+
 *     |  Active  | --> | Cancelled |
 *     +----------+     +-----------+
 *          |
 *          V
 *     +----------+
 *     | Inactive |
 *     +----------+
 * ```
 * The state can be queried with [isActive] and [isCancelled] functions that correspond to the top two states.
 * This class is thread-safe.
 */
public interface Cancellable : CoroutineContextElement {
    companion object : CoroutineContextKey<Cancellable>
    public override val contextKey get() = Cancellable

    /**
     * Returns `true` when this activity is still active and can be cancelled.
     */
    public val isActive: Boolean

    /**
     * Returns `true` when this activity was already cancelled. It implies that it is not [isActive].
     */
    public val isCancelled: Boolean

    /**
     * Registers cancel handler. The action depends on the state of this activity.
     * * When activity [isCancelled], then the handler is immediately invoked.
     * * Otherwise, When activity not [isActive], it does nothing.
     * * Otherwise, handler will be invoked once when this activity is cancelled.
     * The resulting [CancelRegistration] can be used to [CancelRegistration.unregisterCancelHandler] if this
     * registration is not longer needed. There is no need to unregister on
     */
    public fun registerCancelHandler(handler: CancelHandler): CancelRegistration
}

typealias CancelHandler = (Cancellable) -> Unit

public interface CancelRegistration {
    fun unregisterCancelHandler()
}

typealias CancellationException = CancellationException

// --------------- utility classes to simplify cancellable implementation

/**
 * Cancellation scope is a [Cancellable] activity that can be cancelled at any time by invocation of [cancel].
 * It is optionally a part of an outer cancellable activity.This scope is cancelled when outer is cancelled, but
 * not vise-versa.
 *
 * This is an open class designed for extension by more specific cancellable classes that might augment the
 * state and provide some other means to make this activity inactive without cancelling it.
 */
public open class CancellationScope(outer: Cancellable? = null) : Cancellable {
    // keeps a stack of cancel listeners or a special CANCELLED, other values denote completed scope
    @Volatile
    private var state: Any? = ACTIVE

    private val registration: CancelRegistration? = outer?.registerCancelHandler { cancel() }

    protected companion object {
        @JvmStatic
        private val STATE: AtomicReferenceFieldUpdater<CancellationScope, Any?> =
            AtomicReferenceFieldUpdater.newUpdater(CancellationScope::class.java, Any::class.java, "state")

        @JvmStatic
        val ACTIVE: ActiveNode = object : ActiveNode {} // ACTIVE is ActiveNode

        @JvmStatic
        val CANCELLED: Any = Any() // CANCELLED is NOT ActiveNode
    }

    protected open fun unwrapState(state: Any?): Any? = state
    protected open fun rewrapState(prevState: Any?, newState: Any?): Any? = newState

    protected fun getState(): Any? = state

    protected fun compareAndSetState(expect: Any?, update: Any?): Boolean {
        require(unwrapState(expect) is ActiveNode)
        if (!STATE.compareAndSet(this, expect, update)) return false
        if (unwrapState(update) !is ActiveNode) {
            registration?.unregisterCancelHandler()
        }
        return true
    }

    public override val isActive: Boolean get() = unwrapState(state) is ActiveNode

    public override val isCancelled: Boolean get() = state == CANCELLED

    public override fun registerCancelHandler(handler: CancelHandler): CancelRegistration {
        while (true) { // lock-free loop on state
            val state = this.state
            if (state == CANCELLED) {
                handler(this)
                return NoCancelRegistration
            }
            val u = unwrapState(state) as? ActiveNode ?: return NoCancelRegistration  // not active anymore
            var list = u as? HandlerList
            if (list == null) {
                list = HandlerList()
                if (!STATE.compareAndSet(this, state, list)) continue
            }
            // todo: synchronize updates with cancel()
            return list.addFirst(HandlerNode(handler))
        }
    }

    public open fun cancel() {
        while (true) { // lock-free loop on state
            val state = this.state
            val u = unwrapState(state) as? ActiveNode ?: return // not active anymore
            if (STATE.compareAndSet(this, state, CANCELLED)) {
                registration?.unregisterCancelHandler()
                onCancel(state, u as? HandlerList)
                return
            }
        }
    }

    private fun onCancel(state: Any?, listeners: HandlerList?)  {
        var suppressedException: Throwable? = null
        listeners?.forEach<HandlerNode> { node ->
            try {
                node.handler(this)
            } catch (ex: Throwable) {
                if (suppressedException != null) ex.addSuppressed(suppressedException)
                suppressedException = ex
            }
        }
        afterCancel(state, suppressedException)
    }

    protected open fun afterCancel(state: Any?, suppressedException: Throwable?) {
        if (suppressedException != null) throw suppressedException
    }

    protected interface ActiveNode

    private class HandlerList : LockFreeLinkedListHead(), ActiveNode

    private class HandlerNode(val handler: CancelHandler) : LockFreeLinkedListNode(), CancelRegistration {
        override fun unregisterCancelHandler() = remove()
    }

    protected object NoCancelRegistration : CancelRegistration {
        override fun unregisterCancelHandler() {}
    }
}


