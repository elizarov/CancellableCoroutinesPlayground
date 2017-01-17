package coroutines

import coroutines.`try`.Try
import kotlinx.coroutines.experimental.LifetimeSupport
import coroutines.futures.asyncFuture
import coroutines.timeout.sleep
import java.util.concurrent.CancellationException

fun main(args: Array<String>) {
    val scope = LifetimeSupport()
    log("Starting coroutines.futures.async f && g")
    val f = asyncFuture(scope) {
        log("Started f")
        sleep(500)
        log("f should not execute this line")
    }
    val g = asyncFuture(scope) {
        log("Started g")
        try {
            sleep(500)
        } finally {
            log("g is executing finally!")
        }
        log("g should not execute this line")
    }
    log("Started coroutines.futures.async f && g... will not wait -- cancel them!!!")
    scope.cancel(CancellationException("I don't want it"))
    check(f.isCancelled)
    check(g.isCancelled)
    log("f result = ${Try<Unit> { f.get() }}")
    log("g result = ${Try<Unit> { g.get() }}")
    Thread.sleep(1000L)
    log("Nothing executed!")
}
