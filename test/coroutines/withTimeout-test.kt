package coroutines

import coroutines.cancellable.CancellationException
import coroutines.futures.asyncFuture
import coroutines.futures.await
import coroutines.timeout.sleep
import coroutines.timeout.withTimeout

fun main(args: Array<String>) {
    fun slow(s: String) = asyncFuture {
        sleep(500L)
        s
    }

    val f = asyncFuture<String> {
        log("Started f")
        val a = slow("A").await()
        log("a = $a")
        withTimeout(1000L) {
            val b = slow("B").await()
            log("b = $b")
        }
        try {
            withTimeout(750L) {
                val c = slow("C").await()
                log("c = $c")
                val d = slow("D").await()
                log("d = $d")
            }
        } catch (ex: CancellationException) {
            log("timed out with $ex")
        }
        val e = slow("E").await()
        log("e = $e")
        "done"
    }
    println("f.get() = ${f.get()}")
}
