import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withCoroutineContext

fun main(args: Array<String>) {
    val ctx1 = newSingleThreadContext("Ctx1")
    val ctx2 = newSingleThreadContext("Ctx2")
    runBlocking(ctx1) {
        log("Started in ctx1")
        withCoroutineContext(ctx2) {
            log("Working in ctx2")
        }
        log("Back to ctx1")
    }
}