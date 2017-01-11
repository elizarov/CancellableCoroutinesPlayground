package coroutines.sequence

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineIntrinsics
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.createCoroutine

/**
 * Scope for [buildSequence] and [buildIterator] blocks.
 */
@RestrictsSuspension
public abstract class SequenceBuilder<in T> internal constructor() {
    /**
     * Yields a value.
     */
    public abstract suspend fun yield(value: T)

    /**
     * Yields potentially infinite sequence of iterator values.
     */
    public abstract suspend fun yieldAll(iterator: Iterator<T>)

    /**
     * Yields a collections of values.
     */
    public suspend fun yieldAll(elements: Iterable<T>) = yieldAll(elements.iterator())

    /**
     * Yields potentially infinite sequence of values.
     */
    public suspend fun yieldAll(sequence: Sequence<T>) = yieldAll(sequence.iterator())
}

/**
 * Builds lazy sequence.
 */
public fun <T> buildSequence(block: suspend SequenceBuilder<T>.() -> Unit): Sequence<T> =
    Sequence { buildIterator(block) }

/**
 * Builds lazy iterator.
 */
public fun <T> buildIterator(block: suspend SequenceBuilder<T>.() -> Unit): Iterator<T> {
    val iterator = SequenceBuilderImpl<T>()
    iterator.nextStep = block.createCoroutine(receiver = iterator, completion = iterator)
    return iterator
}

private class SequenceBuilderImpl<T>: SequenceBuilder<T>(), Iterator<T>, Continuation<Unit> {
    var computedNext = false
    var nextStep: Continuation<Unit>? = null
    var nextValue: T? = null

    override fun hasNext(): Boolean {
        if (!computedNext) {
            val step = nextStep!!
            computedNext = true
            nextStep = null
            step.resume(Unit) // leaves it in "done" state if crashes
        }
        return nextStep != null
    }

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        computedNext = false
        return nextValue as T
    }

    // Completion continuation implementation
    override fun resume(value: Unit) {
        // nothing to do here -- leave null in nextStep
    }

    override fun resumeWithException(exception: Throwable) {
        throw exception // just rethrow
    }

    // YieldScope implementation
    override suspend fun yield(value: T) {
        nextValue = value
        return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
            nextStep = c
            CoroutineIntrinsics.SUSPENDED
        }
    }

    override suspend fun yieldAll(iterator: Iterator<T>) {
        if (!iterator.hasNext()) return // no values -- don't suspend
        nextValue = iterator.next()
        return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
            nextStep = IteratorContinuation(c, iterator)
            CoroutineIntrinsics.SUSPENDED
        }
    }

    inner class IteratorContinuation(val completion: Continuation<Unit>, val iterator: Iterator<T>) : Continuation<Unit> {
        override fun resume(value: Unit) {
            if (!iterator.hasNext()) {
                completion.resume(Unit)
                return
            }
            nextValue = iterator.next()
            nextStep = this
        }

        override fun resumeWithException(exception: Throwable) {
            throw exception // just rethrow
        }
    }
}
