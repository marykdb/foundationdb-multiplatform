package maryk.foundationdb

import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import com.apple.foundationdb.Transaction as JavaTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryOwnershipReviewTest {
    private fun transaction(onError: CompletableFuture<JavaTransaction>, close: () -> Unit): JavaTransaction =
        Proxy.newProxyInstance(JavaTransaction::class.java.classLoader, arrayOf(JavaTransaction::class.java)) { _, method, _ ->
            when (method.name) {
                "onError" -> onError
                "close" -> { close(); null }
                else -> error("Unexpected transaction operation: ${method.name}")
            }
        } as JavaTransaction

    @Test
    fun closingDuringRetryClosesTheLateReplacement() = runBlocking<Unit> {
        val pending = CompletableFuture<JavaTransaction>()
        var replacementsClosed = 0
        val old = Transaction(transaction(pending) {})
        val result = old.onError(FDBException("conflict", 1020))
        old.close()
        pending.complete(transaction(CompletableFuture()) { replacementsClosed++ })
        assertEquals(1, replacementsClosed)
        assertFailsWith<CancellationException> { result.await() }
    }

    @Test
    fun retryCompletionDoesNotHoldTheOwnershipMonitorAcrossClientCallbacks() {
        val pending = CompletableFuture<JavaTransaction>()
        var replacementsClosed = 0
        val old = Transaction(transaction(pending) {})
        val executor = Executors.newSingleThreadExecutor()
        try {
            val error: Throwable = FDBException("conflict", 1020)
            val callback = old.onError(error).asCompletableFuture().thenRun {
                executor.submit { old.close() }.get(5, TimeUnit.SECONDS)
            }
            pending.complete(transaction(CompletableFuture()) { replacementsClosed++ })
            callback.get(10, TimeUnit.SECONDS)
            assertEquals(1, replacementsClosed)
        } finally { executor.shutdownNow(); old.close() }
    }

    @Test
    fun cancellingRetryCannotDiscardReplacementOwnership() {
        val pending = object : CompletableFuture<JavaTransaction>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        }
        var replacementsClosed = 0
        val old = Transaction(transaction(pending) {})
        val error: Throwable = FDBException("conflict", 1020)
        val result = old.onError(error)
        result.cancel()
        pending.complete(transaction(CompletableFuture()) { replacementsClosed++ })
        assertEquals(1, replacementsClosed)
        old.close()
        assertEquals(1, replacementsClosed)
    }
}
