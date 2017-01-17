import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.withTimeout
import kotlinx.coroutines.experimental.swing.Swing
import kotlinx.coroutines.experimental.SuspendingValue
import kotlinx.coroutines.experimental.suspendingValue

fun main(args: Array<String>) = runBlocking(Swing) {
    val va = Array<SuspendingValue<String>>(10) { i ->
        suspendingValue {
            val sleepTime = i * 200L
            log("This value #$i will delay for $sleepTime ms before producing result")
            try {
                delay(sleepTime)
                log("Value $i is producing result!")
            } catch (ex: Exception) {
                log("Value $i was aborted because of $ex")
            }
            "Result #$i"
        }
    }
    log("Created ${va.size} values")
    withTimeout(1100L) {
        for (v in va)
            log("Got value: ${v.getValue()}")
    }
}