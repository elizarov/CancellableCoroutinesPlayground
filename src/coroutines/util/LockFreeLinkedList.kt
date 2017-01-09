package coroutines.util

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

private typealias Node = LockFreeLinkedListNode

/**
 * Doubly-linked concurrent list node with remove support.
 * Based on paper "Lock-Free and Practical Doubly Linked List-Based Deques Using Single-Word Compare-and-Swap"
 * by Sundell and Tsigas. The instance of this class serves both as list head/tail sentinel and as the list item.
 * Sentinel node should be never removed.
 */
@Suppress("LeakingThis")
public open class LockFreeLinkedListNode {
    @Volatile
    private var next: Any = this // DoubleLinkedNode | Removed
    @Volatile
    private var prev: Any = this // DoubleLinkedNode | Removed

    private companion object {
        @JvmStatic
        val NEXT: AtomicReferenceFieldUpdater<Node, Any> =
                AtomicReferenceFieldUpdater.newUpdater(Node::class.java, Any::class.java, "next")
        @JvmStatic
        val PREV: AtomicReferenceFieldUpdater<Node, Any> =
                AtomicReferenceFieldUpdater.newUpdater(Node::class.java, Any::class.java, "prev")
    }

    private class Removed(val ref: Node)

    public val isRemoved: Boolean get() = next is Removed

    @PublishedApi
    internal fun next(): Node = next.unwrap()

    internal open fun <T : Node> addFirst(node: T): T {
        require(node.next == node && node.prev == node)
        while (true) { // lock-free loop on next
            val next = this.next as Node // this sentinel node is never removed
            PREV.lazySet(node, this)
            NEXT.lazySet(node, next)
            if (NEXT.compareAndSet(this, next, node)) {
                finishAdd(node, next)
                return node
            }
        }
    }

    private fun finishAdd(node: Node, next: Node) {
        while (true) {
            val nextPrev = next.prev
            if (nextPrev is Removed || node.next != next) return
            if (PREV.compareAndSet(next, nextPrev, node)) {
                if (node.next is Removed) {
                    // already removed
                    helpInsert(nextPrev as Node, next)
                }
                return
            }
        }
    }

    private fun helpInsert(_prev: Node, node: Node) {
        var prev: Node = _prev
        var last: Node? = null
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
            val nodePrev = node.prev
            if (nodePrev is Removed) return // node was removed, too -- its remover will take care
            if (prevNext !== node) {
                // need to fixup next
                last = prev
                prev = nodePrev as Node
                continue
            }
            if (nodePrev === prev) return // it is already linked as needed
            if (prevNext === node && PREV.compareAndSet(node, nodePrev, prev)) {
                if (prev.prev !is Removed) return // finish only if prev was not removed
            }
        }
    }

    /**
     * Removes this node from the list.
     */
    public open fun remove() {
        while (true) { // lock-free loop on next
            val next = this.next
            if (next is Removed) return // already removed
            if (NEXT.compareAndSet(this, next, Removed(next as Node))) {
                // removed successfully (linearized remove) -- fixup the list
                helpRemove()
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

    private fun helpRemove() {
        var last: Node? = null // will set to the node left of prev when found
        var prev: Node = markPrev()
        var next: Node = (this.next as Removed).ref
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
            if (prevNext != this) {
                // skipped over some removed nodes to the left -- setup to fixup the next links
                last = prev
                prev = prevNext as Node
                continue
            }
            // Now prev & next are Ok
            if (NEXT.compareAndSet(prev, this, next)) return // success!
        }
    }

    private fun Any.unwrap(): Node = if (this is Removed) ref else this as Node
}

public open class LockFreeLinkedListHead : LockFreeLinkedListNode() {
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
    public override fun <T : Node> addFirst(node: T): T = super.addFirst(node)

    public override fun remove() = throw UnsupportedOperationException()
}
