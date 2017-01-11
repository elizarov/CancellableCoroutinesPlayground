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
     * * When activity [isCancelled], then the handler is immediately invoked.
     * * Otherwise, handler will be invoked once when this activity is cancelled.
     * The resulting [CancelRegistration] can be used to [CancelRegistration.unregisterCancelHandler] if this
     * registration is not longer needed. There is no need to unregister after cancellation.
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
    private var state: Any? = Active() // will drop the list on cancel

    // directly pass HandlerNode to outer scope to optimize one closure object (see makeNode)
    private val registration: CancelRegistration? = outer?.registerCancelHandler(object : HandlerNode() {
        override fun invoke(scope: Cancellable) = cancel()
    })

    protected companion object {
        @JvmStatic
        private val STATE: AtomicReferenceFieldUpdater<CancellationScope, Any?> =
            AtomicReferenceFieldUpdater.newUpdater(CancellationScope::class.java, Any::class.java, "state")

        @JvmStatic
        val CANCELLED: Any = Any()
    }

    protected fun getState(): Any? = state

    protected fun compareAndSetState(expect: Any?, update: Any?): Boolean {
        require(update !is Active) // cannot go back to active state
        if (!STATE.compareAndSet(this, expect, update)) return false
        if (expect is Active) registration?.unregisterCancelHandler() // made active -> inactive transition
        return true
    }

    public override val isCancelled: Boolean get() = state !is Active

    public override fun registerCancelHandler(handler: CancelHandler): CancelRegistration {
        var nodeCache: HandlerNode? = null
        while (true) { // lock-free loop on state
            val state = this.state
            if (state !is Active) {
                handler(this)
                return NoCancelRegistration
            }
            val node = nodeCache ?: makeNode(handler).apply { nodeCache = this }
            if (state.addLastIf(node) { this.state == state }) return node
        }
    }
    public open fun cancel() {
        while (true) { // lock-free loop on state
            val state = this.state as? Active ?: return // quit if not active anymore
            if (compareAndSetState(state, CANCELLED)) {
                onCancel(state)
                return
            }
        }
    }

    private fun onCancel(state: Active)  {
        var suppressedException: Throwable? = null
        state.forEach<HandlerNode> { node ->
            try {
                node.invoke(this)
            } catch (ex: Throwable) {
                if (suppressedException != null) ex.addSuppressed(suppressedException)
                suppressedException = ex
            }
        }
        afterCancel(suppressedException)
    }

    protected open fun afterCancel(suppressedException: Throwable?) {
        if (suppressedException != null) throw suppressedException
    }

    private fun makeNode(handler: CancelHandler): HandlerNode = handler as? HandlerNode ?:
        object : HandlerNode() {
            override fun invoke(scope: Cancellable) = handler.invoke(scope)
        }

    protected class Active : LockFreeLinkedListHead()

    private abstract class HandlerNode : LockFreeLinkedListNode(), CancelRegistration, CancelHandler {
        override fun unregisterCancelHandler() = remove()
        override abstract fun invoke(scope: Cancellable)
    }

    protected object NoCancelRegistration : CancelRegistration {
        override fun unregisterCancelHandler() {}
    }
}


