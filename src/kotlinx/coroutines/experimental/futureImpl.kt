package kotlinx.coroutines.experimental

import java.util.concurrent.Future

// an internal object-count optimization
internal class CancelFutureOnCompletion(
        lifetime: Lifetime,
        val future: Future<*>
) : LifetimeNode(lifetime)  {
    override fun invoke(reason: Throwable?) { future.cancel(true) }
    override fun toString() = "CancelFutureOnCompletion[$future]"
}
