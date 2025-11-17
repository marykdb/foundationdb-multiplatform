package maryk.foundationdb

import kotlin.test.Test

class DatabaseOptionsTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun databaseOptionSettersExecute() = harness.runAndReset {
        ensureApiVersionSelected()
        val options = database.options()
        options.setLocationCacheSize(4096)
        options.setMaxWatches(128)
        options.setMachineId("machine-id")
        options.setDatacenterId("dc-id")
        options.setSnapshotRywEnable()
        options.setSnapshotRywDisable()
        options.setTransactionLoggingMaxFieldLength(64)
        options.setTransactionTimeout(5_000)
        options.setTransactionRetryLimit(5)
        options.setTransactionMaxRetryDelay(1_000)
        options.setTransactionSizeLimit(100_000)
        options.setTransactionCausalReadRisky()
        options.setTransactionAutomaticIdempotency()
        options.setTransactionBypassUnreadable()
        options.setTransactionReportConflictingKeys()
        options.setTestCausalReadRisky(0)

        database.runSuspend { txn ->
            txn.set(key("db", "options", "verify"), "configured".encodeToByteArray())
        }
    }
}
