package kotlinx.coroutines.experimental.util

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

private typealias Node = LockFreeLinkedListNode

/**
 * Doubly-linked concurrent list node with remove support.
 * Based on paper
 * ["Lock-Free and Practical Doubly Linked List-Based Deques Using Single-Word Compare-and-Swap"](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.140.4693&rep=rep1&type=pdf)
 * by Sundell and Tsigas.
 * The instance of this class serves both as list head/tail sentinel and as the list item.
 * Sentinel node should be never removed.
 */
@Suppress("LeakingThis")
internal open class LockFreeLinkedListNode {
    @Volatile
    private var _next: Any = this // DoubleLinkedNode | Removed | CondAdd
    @Volatile
    private var prev: Any = this // DoubleLinkedNode | Removed

    private companion object {
        @JvmStatic
        val NEXT: AtomicReferenceFieldUpdater<Node, Any> =
                AtomicReferenceFieldUpdater.newUpdater(Node::class.java, Any::class.java, "_next")
        @JvmStatic
        val PREV: AtomicReferenceFieldUpdater<Node, Any> =
                AtomicReferenceFieldUpdater.newUpdater(Node::class.java, Any::class.java, "prev")

    }

    private class Removed(val ref: Node) {
        override fun toString(): String = "Removed[$ref]"
    }

    @PublishedApi
    internal abstract class CondAdd {
        internal lateinit var newNode: Node
        internal lateinit var oldNext: Node
        @Volatile
        private var consensus: Int = UNDECIDED // status of operation
        abstract fun isCondition(): Boolean

        private companion object {
            @JvmStatic
            val CONSENSUS: AtomicIntegerFieldUpdater<CondAdd> =
                AtomicIntegerFieldUpdater.newUpdater(CondAdd::class.java, "consensus")

            const val UNDECIDED = 0
            const val SUCCESS = 1
            const val FAILURE = 2
        }

        fun completeAdd(node: Node): Boolean {
            // make decision on status
            var consensus: Int
            while (true) {
                consensus = this.consensus
                if (consensus != UNDECIDED) break
                val proposal = if (isCondition()) SUCCESS else FAILURE
                if (CONSENSUS.compareAndSet(this, UNDECIDED, proposal)) {
                    consensus = proposal
                    break
                }
            }
            val success = consensus == SUCCESS
            if (NEXT.compareAndSet(node, this, if (success) newNode else oldNext)) {
                // only the thread the makes this update actually finishes add operation
                if (success) newNode.finishAdd(oldNext)
            }
            return success
        }
    }

    public val isRemoved: Boolean get() = _next is Removed

    private val isFresh: Boolean get() = _next === this && prev === this

    private val next: Any get() {
        while (true) { // helper loop on _next
            val next = this._next
            if (next !is CondAdd) return next
            next.completeAdd(this)
        }
    }

    @PublishedApi
    internal fun next(): Node = next.unwrap()

    @PublishedApi
    internal fun addFirstCC(node: Node, condAdd: CondAdd?): Boolean {
        require(node.isFresh)
        condAdd?.newNode = node
        while (true) { // lock-free loop on next
            val next = this.next as Node // this sentinel node is never removed
            PREV.lazySet(node, this)
            NEXT.lazySet(node, next)
            condAdd?.oldNext = next
            if (NEXT.compareAndSet(this, next, condAdd ?: node)) {
                // added successfully (linearized add) -- fixup the list
                return condAdd?.completeAdd(this) ?: run { node.finishAdd(next); true }
            }
        }
    }

    @PublishedApi
    internal fun addLastCC(node: Node, condAdd: CondAdd?): Boolean {
        require(node.isFresh)
        condAdd?.newNode = node
        while (true) { // lock-free loop on prev.next
            val prev = this.prev as Node // this sentinel node is never removed
            if (prev.next !== this) {
                helpInsert(prev)
                continue
            }
            PREV.lazySet(node, prev)
            NEXT.lazySet(node, this)
            condAdd?.oldNext = this
            if (NEXT.compareAndSet(prev, this, condAdd ?: node)) {
                // added successfully (linearized add) -- fixup the list
                return condAdd?.completeAdd(prev) ?: run { node.finishAdd(this); true }
            }
        }
    }

    private fun finishAdd(next: Node) {
        while (true) {
            val nextPrev = next.prev
            if (nextPrev is Removed || this.next !== next) return // next was removed, remover fixes up links
            if (PREV.compareAndSet(next, nextPrev, this)) {
                if (this.next is Removed) {
                    // already removed
                    next.helpInsert(nextPrev as Node)
                }
                return
            }
        }
    }

    /**
     * Removes this node from the list.
     */
    public open fun remove() {
        while (true) { // lock-free loop on next
            val next = this.next
            if (next is Removed) return // was already removed -- don't try to help (original thread will take care)
            if (NEXT.compareAndSet(this, next, Removed(next as Node))) {
                // was removed successfully (linearized remove) -- fixup the list
                helpDelete()
                next.helpInsert(prev.unwrap())
                return
            }
        }
    }

    private fun markPrev(): Node {
        while (true) { // lock-free loop on prev
            val prev = this.prev
            if (prev is Removed) return prev.ref
            if (PREV.compareAndSet(this, prev, Removed(prev as Node))) return prev
        }
    }

    // fixes next links to the left of this node
    private fun helpDelete() {
        var last: Node? = null // will set to the node left of prev when found
        var prev: Node = markPrev()
        var next: Node = (this._next as Removed).ref
        while (true) {
            // move to the right until first non-removed node
            val nextNext = next.next
            if (nextNext is Removed) {
                next.markPrev()
                next = nextNext.ref
                continue
            }
            // move the the left until first non-removed node
            val prevNext = prev.next
            if (prevNext is Removed) {
                if (last != null) {
                    prev.markPrev()
                    NEXT.compareAndSet(last, prev, prevNext.ref)
                    prev = last
                    last = null
                } else {
                    prev = prev.prev.unwrap()
                }
                continue
            }
            if (prevNext !== this) {
                // skipped over some removed nodes to the left -- setup to fixup the next links
                last = prev
                prev = prevNext as Node
                if (prev === next) return // already done!!!
                continue
            }
            // Now prev & next are Ok
            if (NEXT.compareAndSet(prev, this, next)) return // success!
        }
    }

    // fixes prev links from this node
    private fun helpInsert(_prev: Node) {
        var prev: Node = _prev
        var last: Node? = null // will be set so that last.next === prev
        while (true) {
            // move the the left until first non-removed node
            val prevNext = prev.next
            if (prevNext is Removed) {
                if (last !== null) {
                    prev.markPrev()
                    NEXT.compareAndSet(last, prev, prevNext.ref)
                    prev = last
                    last = null
                } else {
                    prev = prev.prev.unwrap()
                }
                continue
            }
            val oldPrev = this.prev
            if (oldPrev is Removed) return // this node was removed, too -- its remover will take care
            if (prevNext !== this) {
                // need to fixup next
                last = prev
                prev = prevNext as Node
                continue
            }
            if (oldPrev === prev) return // it is already linked as needed
            if (PREV.compareAndSet(this, oldPrev, prev)) {
                if (prev.prev !is Removed) return // finish only if prev was not concurrently removed
            }
        }
    }

    private fun Any.unwrap(): Node = if (this is Removed) ref else this as Node

    internal fun validateNode(prev: Node, next: Node) {
        check(prev === this.prev)
        check(next === this.next)
    }
}

internal open class LockFreeLinkedListHead : LockFreeLinkedListNode() {
    /**
     * Iterates over all elements in this list of a specified type.
     */
    public inline fun <reified T : Node> forEach(block: (T) -> Unit) {
        var cur: Node = next()
        while (cur != this) {
            if (cur is T) block(cur)
            cur = cur.next()
        }
    }

    /**
     * Adds first item to this list.
     */
    public fun addFirst(node: Node) { addFirstCC(node, null) }

    /**
     * Adds first item to this list atomically if the [condition] is true.
     */
    public inline fun addFirstIf(node: Node, crossinline condition: () -> Boolean): Boolean =
        addFirstCC(node, object : CondAdd() {
            override fun isCondition(): Boolean = condition()
        })

    /**
     * Adds last item to this list.
     */
    public fun addLast(node: Node) { addLastCC(node, null) }

    /**
     * Adds last item to this list atomically if the [condition] is true.
     */
    public inline fun addLastIf(node: Node, crossinline condition: () -> Boolean): Boolean =
        addLastCC(node, object : CondAdd() {
            override fun isCondition(): Boolean = condition()
        })

    public override fun remove() = throw UnsupportedOperationException()

    internal fun validate() {
        var prev: Node = this
        var cur: Node = next()
        while (cur != this) {
            val next = cur.next()
            cur.validateNode(prev, next)
            prev = cur
            cur = next
        }
        validateNode(prev, next())
    }
}
