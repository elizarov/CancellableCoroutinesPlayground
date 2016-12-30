import coroutines.async
import coroutines.await
import coroutines.cancellable.CancellationException
import coroutines.cancellable.withTimeout
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    fun supplySlow(s: String) = CompletableFuture.supplyAsync<String> {
        Thread.sleep(500L)
        s
    }

    val f = async<String> {
        val a = supplySlow("A").await()
        log("a = $a")
        withTimeout(1000L) {
            val b = supplySlow("B").await()
            log("b = $b")
        }
        try {
            withTimeout(750L) {
                val c = supplySlow("C").await()
                log("c = $c")
                val d = supplySlow("D").await()
                log("d = $d")
            }
        } catch (ex: CancellationException) {
            log("timed out with $ex")
        }
        val e = supplySlow("E").await()
        log("e = $e")
        "done"
    }
    println("f.get() = ${f.get()}")
}
