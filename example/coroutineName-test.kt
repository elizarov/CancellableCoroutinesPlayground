import kotlinx.coroutines.experimental.*

fun main(args: Array<String>) = runBlocking {
    log("Started main coroutine")
    // run two background value computations
    val v1 = suspendingValue(CoroutineName("v1")) {
        log("Computing v1 for a while")
        delay(500)
        log("Computed v1")
        19
    }
    val v2 = suspendingValue(CoroutineName("v2")) {
        log("Computing v2 for a while")
        delay(1000)
        log("Computed v2")
        23
    }
    log("The answer for v1 + v2 = ${v1.getValue() + v2.getValue()}")
}