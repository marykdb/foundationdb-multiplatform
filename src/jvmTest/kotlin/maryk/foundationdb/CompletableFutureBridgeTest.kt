package maryk.foundationdb

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class CompletableFutureBridgeTest {
    @Test
    fun mappedCancellationReachesSource() {
        val source = CompletableFuture<Int>()
        val mapped = source.mapToFdbFuture { it.toString() }
        mapped.cancel()
        assertTrue(source.isCancelled)
        assertTrue(mapped.isCancelled)
    }

    @Test
    fun mappedCompletionPreservesResult() = runBlocking {
        val source = CompletableFuture.completedFuture(42)
        assertEquals("42", source.mapToFdbFuture { it.toString() }.await())
    }

    @Test
    fun bridgeFutureAwait() = runBlocking {
        ensureApiVersionSelected()
        val completable = CompletableFuture.completedFuture("bridge")
        val future = completable.asFdbFuture()
        assertEquals("bridge", future.await())
    }
}
