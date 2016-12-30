import coroutines.async
import coroutines.await
import coroutines.cancellable.CancellationScope
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    fun supplySlow(s: String) = CompletableFuture.supplyAsync<String> {
        Thread.sleep(500L)
        s
    }

    val scope = CancellationScope()
    log("Starting coroutines.async f && g")
    val f = async(scope) {
        supplySlow("F").await()
        log("f should not execute this line")
    }
    val g = async(scope) {
        try {
            supplySlow("G").await()
        } finally {
            log("g is executing finally!")
        }
        log("g should not execute this line")
    }
    log("Started coroutines.async f && g... will not wait -- cancel them!!!")
    scope.cancel()
    check(f.isCancelled)
    check(g.isCancelled)
    Thread.sleep(1000L)
    log("Nothing executed!")
}
