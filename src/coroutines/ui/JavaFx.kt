package coroutines.ui

import coroutines.cancellable.LifetimeContinuation
import coroutines.cancellable.suspendCancellableCoroutine
import coroutines.dispatcher.CoroutineDispatcher
import javafx.animation.AnimationTimer
import javafx.application.Platform
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation

object JavaFx : CoroutineDispatcher() {
    private val timer = Timer()

    init { timer.start() }

    override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
        if (Platform.isFxApplicationThread()) return false
        Platform.runLater { continuation.resume(value) }
        return true
    }

    override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
        if (Platform.isFxApplicationThread()) return false
        Platform.runLater { continuation.resumeWithException(exception) }
        return true
    }

    suspend fun awaitPulse(): Long = suspendCancellableCoroutine { cont ->
        timer.onNext(cont)
    }
}

private class Timer : AnimationTimer() {
    val next = CopyOnWriteArrayList<LifetimeContinuation<Long>>()

    override fun handle(now: Long) {
        val cur = next.toTypedArray()
        next.clear()
        for (cont in cur)
            cont.resume(now)
    }

    fun onNext(cont: LifetimeContinuation<Long>) {
        next += cont
    }
}
