package kotlinx.coroutines.experimental.spi

import kotlin.coroutines.CoroutineContext

/**
 * Extension point that shall be implemented by frameworks that need to associate a default
 * context with their framework threads.
 */
public interface DefaultCoroutineContextProvider {
    public fun getDefaultCoroutineContext(currentThread: Thread): CoroutineContext?
}