package coroutines

import coroutines.futures.asyncFuture
import coroutines.timeout.sleep

fun main(args: Array<String>) {
    val f = asyncFuture {
        try {
            log("Started f")
            sleep(500)
            log("Slept 500 ms #1")
            sleep(500)
            log("Slept 500 ms #2")
            sleep(500)
            log("Slept 500 ms #3")
            sleep(500)
            log("Slept 500 ms #4")
            sleep(500)
            log("Slept 500 ms #5")
        } catch(e: Exception) {
            log("Aborting because of $e")
        }
    }
    Thread.sleep(1100)
    f.cancel(false)
}
