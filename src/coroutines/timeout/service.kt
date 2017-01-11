package coroutines.timeout

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

val timeoutExecutorService by lazy<ScheduledExecutorService> {
    Executors.newScheduledThreadPool(1) { r ->
        Thread(r, "TimeoutExecutorService").apply { isDaemon = true }
    }
}

