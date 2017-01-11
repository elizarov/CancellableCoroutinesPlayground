package coroutines.context

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.coroutines.*

/*
 * Alternative coroutines library with [createCoroutine] and [startCoroutine] that accept [CoroutineContext]
 * parameter and [suspendCoroutine] the provides [CoroutineContinuation] with context.
 */
public fun <T> (suspend  () -> T).createCoroutine(completion: CoroutineContinuation<T>): Continuation<Unit> {
    return createCoroutine(completion = completion, dispatcher = ContextDispatcherImpl(completion.context))
}

public fun <T> (suspend  () -> T).startCoroutine(completion: CoroutineContinuation<T>) {
    startCoroutine(completion = completion, dispatcher = ContextDispatcherImpl(completion.context))
}

public interface CoroutineContinuation<in T> : Continuation<T> {
    public val context: CoroutineContext
}

public suspend fun <T> suspendCoroutine(block: (CoroutineContinuation<T>) -> Unit) =
        suspendDispatchedCoroutineOrReturn<T> { c, d ->
            val safe = SafeContinuation<T>(c, getCoroutineContext(d))
            block(safe)
            safe.getResult()
        }

public suspend fun <T> suspendCoroutineOrReturn(block: (CoroutineContinuation<T>) -> Any?) =
        suspendDispatchedCoroutineOrReturn<T> { c, d ->
            val impl = object : CoroutineContinuationImpl<T>(getCoroutineContext(d)), Continuation<T> by c {}
            block(impl)
        }

// --------------------- impl ---------------------

private inline suspend fun <T> suspendDispatchedCoroutineOrReturn(crossinline block: (Continuation<T>, ContinuationDispatcher?) -> Any?): T =
        CoroutineIntrinsics.suspendCoroutineOrReturn { c: Continuation<T> ->
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")
            val d = (c as? kotlin.jvm.internal.DispatchedContinuation<T>)?.dispatcher
            block(c, d)
        }

private fun getCoroutineContext(d: ContinuationDispatcher?) =
        (d as? ContextDispatcherImpl)?.context ?: EmptyCoroutineContext

private class ContextDispatcherImpl(var context: CoroutineContext) : ContinuationDispatcher {
    override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
        return context[CoroutineDispatcher]?.dispatchResume(value, continuation) ?: false
    }

    override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
        return context[CoroutineDispatcher]?.dispatchResumeWithException(exception, continuation) ?: false
    }
}

private abstract class CoroutineContinuationImpl<in T>(
    override val context: CoroutineContext
) : CoroutineContinuation<T>

private val UNDECIDED: Any? = Any()
private val RESUMED: Any? = Any()
private class Fail(val exception: Throwable)

private class SafeContinuation<in T>(
    val delegate: Continuation<T>,
    context: CoroutineContext
) : CoroutineContinuationImpl<T>(context) {
    @Volatile
    private var result: Any? = UNDECIDED

    companion object {
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private val RESULT_UPDATER = AtomicReferenceFieldUpdater.newUpdater<SafeContinuation<*>, Any?>(
                SafeContinuation::class.java, Any::class.java as Class<Any?>, "result")
    }

    private fun cas(expect: Any?, update: Any?): Boolean =
            RESULT_UPDATER.compareAndSet(this, expect, update)

    override fun resume(value: T) {
        while (true) { // lock-free loop
            val result = this.result // atomic read
            when (result) {
                UNDECIDED -> if (cas(UNDECIDED, value)) return
                CoroutineIntrinsics.SUSPENDED -> if (cas(CoroutineIntrinsics.SUSPENDED, RESUMED)) {
                    delegate.resume(value)
                    return
                }
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    override fun resumeWithException(exception: Throwable) {
        while (true) { // lock-free loop
            val result = this.result // atomic read
            when (result) {
                UNDECIDED -> if (cas(UNDECIDED, Fail(exception))) return
                CoroutineIntrinsics.SUSPENDED -> if (cas(CoroutineIntrinsics.SUSPENDED, RESUMED)) {
                    delegate.resumeWithException(exception)
                    return
                }
                else -> throw IllegalStateException("Already resumed")
            }
        }
    }

    @PublishedApi
    internal fun getResult(): Any? {
        var result = this.result // atomic read
        if (result == UNDECIDED) {
            if (cas(UNDECIDED, CoroutineIntrinsics.SUSPENDED)) return CoroutineIntrinsics.SUSPENDED
            result = this.result // reread volatile var
        }
        when (result) {
            RESUMED -> return CoroutineIntrinsics.SUSPENDED // already called continuation, indicate SUSPENDED upstream
            is Fail -> throw result.exception
            else -> return result // either SUSPENDED or data
        }
    }
}

