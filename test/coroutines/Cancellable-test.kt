package coroutines

import coroutines.`try`.Try
import coroutines.cancellable.CancellationScope
import coroutines.futures.asyncFuture
import coroutines.futures.await
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    fun supplySlow(s: String) = CompletableFuture.supplyAsync<String> {
        Thread.sleep(500L)
        s
    }

    val scope = CancellationScope()
    log("Starting coroutines.futures.async f && g")
    val f = asyncFuture(scope) {
        log("Started f")
        supplySlow("F").await()
        log("f should not execute this line")
    }
    val g = asyncFuture(scope) {
        log("Started g")
        try {
            supplySlow("G").await()
        } finally {
            log("g is executing finally!")
        }
        log("g should not execute this line")
    }
    log("Started coroutines.futures.async f && g... will not wait -- cancel them!!!")
    scope.cancel(CancellationException("I don't want it"))
    check(f.isCancelled)
    check(g.isCancelled)
    println("f result = ${Try<Unit> { f.get() }}")
    println("g result = ${Try<Unit> { g.get() }}")
    Thread.sleep(1000L)
    log("Nothing executed!")
}
