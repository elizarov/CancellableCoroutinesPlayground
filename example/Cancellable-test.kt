import kotlinx.coroutines.experimental.LifetimeSupport
import kotlinx.coroutines.experimental.Try
import kotlinx.coroutines.experimental.future
import kotlinx.coroutines.experimental.delay
import java.util.concurrent.CancellationException

fun main(args: Array<String>) {
    val scope = LifetimeSupport()
    log("Starting coroutines.futures.async f && g")
    val f = future(scope) {
        log("Started f")
        delay(500)
        log("f should not execute this line")
    }
    val g = future(scope) {
        log("Started g")
        try {
            delay(500)
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
