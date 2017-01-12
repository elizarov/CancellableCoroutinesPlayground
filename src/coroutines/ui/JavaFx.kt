package coroutines.ui

import coroutines.context.CoroutineDispatcher
import javafx.application.Platform
import kotlin.coroutines.Continuation

object JavaFx : CoroutineDispatcher {
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
}
