package maryk.foundationdb

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class CompletableFutureBridgeTest {
    @Test
    fun bridgeFutureAwait() = runBlocking {
        ensureApiVersionSelected()
        val completable = CompletableFuture.completedFuture("bridge")
        val future = completable.asFdbFuture()
        assertEquals("bridge", future.await())
    }
}
