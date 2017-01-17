package kotlinx.coroutines.experimental.swing

import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.Delay
import java.awt.event.ActionListener
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Dispatches execution onto Swing event dispatching thread.
 */
object Swing : CoroutineDispatcher(), Delay {
    override fun isDispatchNeeded(): Boolean = !SwingUtilities.isEventDispatchThread()
    override fun dispatch(block: Runnable) = SwingUtilities.invokeLater(block)

    override fun resumeAfterDelay(time: Long, unit: TimeUnit, continuation: CancellableContinuation<Unit>) {
        val timerTime = unit.toMillis(time).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val timer = Timer(timerTime, ActionListener { continuation.resume(Unit) }).apply {
            isRepeats = false
            start()
        }
        continuation.onCompletion { timer.stop() }
    }
}
