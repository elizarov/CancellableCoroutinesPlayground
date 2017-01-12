package coroutines.ui

import coroutines.context.CoroutineDispatcher
import javax.swing.SwingUtilities
import kotlin.coroutines.Continuation

object Swing : CoroutineDispatcher {
    override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
        if (SwingUtilities.isEventDispatchThread()) return false
        SwingUtilities.invokeLater { continuation.resume(value) }
        return true
    }

    override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
        if (SwingUtilities.isEventDispatchThread()) return false
        SwingUtilities.invokeLater { continuation.resumeWithException(exception) }
        return true
    }
}
