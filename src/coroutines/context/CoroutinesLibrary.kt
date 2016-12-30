package coroutines.context

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.coroutines.*

public fun <T> (suspend  () -> T).createCoroutine(
        completion: Continuation<T>,
        context: CoroutineContext = EmptyCoroutineContext
): Continuation<Unit> {
    return createCoroutine(completion = completion, dispatcher = ContextDispatcherImpl(context))
}

public fun <T> (suspend  () -> T).startCoroutine(
        completion: Continuation<T>,
        context: CoroutineContext = EmptyCoroutineContext
) {
    startCoroutine(completion = completion, dispatcher = ContextDispatcherImpl(context))
}

public interface CoroutineContinuation<in T> : Continuation<T> {
    public val context: CoroutineContext
    public fun addToContext(other: CoroutineContext)
}

public suspend fun <T> suspendCoroutine(block: (CoroutineContinuation<T>) -> Unit) =
        suspendDispatchedCoroutineOrReturn<T> { c, d ->
            val safe = SafeContinuation<T>(c, d as? ContextDispatcherImpl)
            block(safe)
            safe.getResult()
        }

public suspend fun <T> suspendCoroutineOrReturn(block: (CoroutineContinuation<T>) -> Any?) =
        suspendDispatchedCoroutineOrReturn<T> { c, d ->
            val impl = object : CoroutineContinuationImpl<T>(d as? ContextDispatcherImpl), Continuation<T> by c {}
            block(impl)
        }

// --------------------- impl ---------------------

private class ContextDispatcherImpl(var context: CoroutineContext) : ContinuationDispatcher {
    override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
        return context[CoroutineDispatcher]?.dispatchResume(value, continuation) ?: false
    }

    override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
        return context[CoroutineDispatcher]?.dispatchResumeWithException(exception, continuation) ?: false
    }
}

private abstract class CoroutineContinuationImpl<in T>(
    val dispatcher: ContextDispatcherImpl?
) : CoroutineContinuation<T> {
    override val context: CoroutineContext
        get() = dispatcher?.context ?: EmptyCoroutineContext

    override fun addToContext(other: CoroutineContext) {
        dispatcher!!.context += other
    }
}

private val UNDECIDED: Any? = Any()
private val RESUMED: Any? = Any()
private class Fail(val exception: Throwable)

private class SafeContinuation<in T>(
    val delegate: Continuation<T>,
    dispatcher: ContextDispatcherImpl?
) : CoroutineContinuationImpl<T>(dispatcher) {
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

