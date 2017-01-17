import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.await
import kotlinx.coroutines.experimental.future.future

fun main(args: Array<String>) {
    fun slow(s: String) = future {
        delay(500L)
        s
    }

    val f = future<String> {
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
