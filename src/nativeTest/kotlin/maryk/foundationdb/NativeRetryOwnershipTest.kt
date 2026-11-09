package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeRetryOwnershipTest {
    @Test
    fun cancellationAfterRetryClosesTheSingleOwnedTransaction() = FoundationDbTestHarness().runAndReset {
        coroutineScope {
            val entered = CompletableDeferred<Unit>()
            var created = 0
            var attempts = 0
            var owned: Transaction? = null
            val operation = launch {
                executeWithRetry({
                    created++
                    database.createTransaction().also { owned = it }
                }, needsCommit = true) {
                    if (++attempts == 1) throw FDBException("Injected conflict", 1020)
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            entered.await()
            operation.cancelAndJoin()
            assertEquals(1, created)
            assertEquals(2, attempts)
            assertFailsWith<IllegalStateException> { requireNotNull(owned).handle.checkOpen() }
        }
    }
}
