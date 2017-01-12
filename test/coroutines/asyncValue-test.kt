package coroutines

import coroutines.run.blockingRun
import coroutines.timeout.sleep
import coroutines.timeout.withTimeout
import coroutines.value.AsyncValue
import coroutines.value.asyncValue

fun main(args: Array<String>) = blockingRun {
    val va = Array<AsyncValue<String>>(10) { i ->
        asyncValue {
            val sleepTime = i * 200L
            log("This value #$i will sleep for $sleepTime ms before producing result")
            try {
                sleep(sleepTime)
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

