package maryk.foundationdb

import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

class TransactionRetryPolicyTest {
    private val harness = FoundationDbTestHarness()

    // Official synchronous JVM context methods retain their CompletionException wrapper.
    private fun Throwable.fdbCode(): Int = assertNotNull(
        generateSequence(this) { it.cause }.filterIsInstance<FDBException>().firstOrNull(),
        "Expected a terminal FDBException, got $this",
    ).getCode()

    private fun conflict(attempt: Int): Nothing {
        check(attempt < 5) { "Retry limit did not stop the transaction" }
        throw FDBException("Injected not_committed", 1020)
    }

    @Test
    fun databaseRetryLimitsApplyToSynchronousReadsAndWrites() = harness.runAndReset {
        for (limit in listOf(0L, 1L)) for (write in listOf(false, true)) {
            database.options().setTransactionRetryLimit(limit)
            database.options().setTransactionMaxRetryDelay(1)
            var attempts = 0
            val failure = assertFails {
                if (write) database.run<Unit> { conflict(++attempts) }
                else database.read<Unit> { conflict(++attempts) }
            }
            assertEquals(1020, failure.fdbCode())
            assertEquals(limit.toInt() + 1, attempts)
        }
    }

    @Test
    fun databaseRetryLimitsApplyToAsyncReadsAndWrites() = harness.runAndReset {
        for (limit in listOf(0L, 1L)) for (write in listOf(false, true)) {
            database.options().setTransactionRetryLimit(limit)
            database.options().setTransactionMaxRetryDelay(1)
            var attempts = 0
            val failure = assertFails {
                if (write) database.runAsync<Unit> { fdbFuture { conflict(++attempts) } }.await()
                else database.readAsync<Unit> { fdbFuture { conflict(++attempts) } }.await()
            }
            assertEquals(1020, failure.fdbCode())
            assertEquals(limit.toInt() + 1, attempts)
        }
    }

    @Test
    fun databaseRetryLimitsApplyToSuspendingReadsAndWrites() = harness.runAndReset {
        for (limit in listOf(0L, 1L)) for (write in listOf(false, true)) {
            database.options().setTransactionRetryLimit(limit)
            database.options().setTransactionMaxRetryDelay(1)
            var attempts = 0
            val failure = assertFails {
                if (write) database.runSuspend<Unit> { conflict(++attempts) }
                else database.readSuspend<Unit> { conflict(++attempts) }
            }
            assertEquals(1020, failure.fdbCode())
            assertEquals(limit.toInt() + 1, attempts)
        }
    }

    @Test
    fun timeoutCoversTheWholeRetryLoop() = harness.runAndReset {
        database.options().setTransactionTimeout(100)
        database.options().setTransactionRetryLimit(100)
        database.options().setTransactionMaxRetryDelay(1)
        var attempts = 0
        val failure = assertFails {
            database.runSuspend<Unit> {
                check(++attempts < 30) { "Timeout was reset between attempts" }
                delay(20)
                throw FDBException("Injected not_committed", 1020)
            }
        }
        assertEquals(1031, failure.fdbCode())
    }

    @Suppress("DEPRECATION")
    @Test
    fun tenantRetriesRetainDatabaseDefaults() = harness.runAndReset {
        database.options().setTransactionRetryLimit(1)
        database.options().setTransactionMaxRetryDelay(1)
        val name = key("retry-policy-tenant")
        TenantManagement.createTenant(database, name).await()
        try {
            val tenant = database.openTenant(name)
            try {
                for (mode in 0..5) {
                    var attempts = 0
                    val failure = assertFails {
                        when (mode) {
                            0 -> tenant.run<Unit> { conflict(++attempts) }
                            1 -> tenant.read<Unit> { conflict(++attempts) }
                            2 -> tenant.runAsync<Unit> { fdbFuture { conflict(++attempts) } }.await()
                            3 -> tenant.readAsync<Unit> { fdbFuture { conflict(++attempts) } }.await()
                            4 -> tenant.runSuspend<Unit> { conflict(++attempts) }
                            else -> tenant.readSuspend<Unit> { conflict(++attempts) }
                        }
                    }
                    assertEquals(1020, failure.fdbCode())
                    assertEquals(2, attempts)
                }
            } finally { tenant.close() }
        } finally { TenantManagement.deleteTenant(database, name).await() }
    }
}
