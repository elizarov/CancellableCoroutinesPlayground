import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Try
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.future.future
import java.util.concurrent.CancellationException

fun main(args: Array<String>) {
    val job = Job()
    log("Starting futures f && g")
    val f = future(job) {
        log("Started f")
        delay(500)
        log("f should not execute this line")
    }
    val g = future(job) {
        log("Started g")
        try {
            delay(500)
        } finally {
            log("g is executing finally!")
        }
        log("g should not execute this line")
    }
    log("Started futures f && g... will not wait -- cancel them!!!")
    job.cancel(CancellationException("I don't want it"))
    check(f.isCancelled)
    check(g.isCancelled)
    log("f result = ${Try<Unit> { f.get() }}")
    log("g result = ${Try<Unit> { g.get() }}")
    Thread.sleep(1000L)
    log("Nothing executed!")
}
