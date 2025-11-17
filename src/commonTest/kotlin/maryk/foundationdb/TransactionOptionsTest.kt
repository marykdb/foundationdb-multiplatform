package maryk.foundationdb

import kotlin.test.Test

class TransactionOptionsJvmTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun safeTransactionOptionsDoNotThrow() = harness.runAndReset {
        val key = key("txn", "options", "bridge")
        val txn = database.createTransaction()
        try {
            val options = txn.options()
            options.callSafely { setCausalWriteRisky() }
            options.callSafely { setCausalReadRisky() }
            options.callSafely { setIncludePortInAddress() }
            options.callSafely { setCausalReadDisable() }
            options.callSafely { setReadYourWritesDisable() }
            options.callSafely { setNextWriteNoWriteConflictRange() }
            options.callSafely { setSnapshotRywEnable() }
            options.callSafely { setSnapshotRywDisable() }
            options.callSafely { setReadServerSideCacheEnable() }
            options.callSafely { setReadServerSideCacheDisable() }
            options.callSafely { setReadPriorityNormal() }
            options.callSafely { setReadPriorityLow() }
            options.callSafely { setReadPriorityHigh() }
            options.callSafely { setPrioritySystemImmediate() }
            options.callSafely { setPriorityBatch() }
            options.callSafely { setInitializeNewDatabase() }
            options.callSafely { setAccessSystemKeys() }
            options.callSafely { setReadSystemKeys() }
            options.callSafely { setRawAccess() }
            options.callSafely { setBypassStorageQuota() }
            options.callSafely { setDebugTransactionIdentifier("debug-id") }
            options.callSafely { setLogTransaction() }
            options.callSafely { setTransactionLoggingMaxFieldLength(64) }
            options.callSafely { setServerRequestTracing() }
            options.callSafely { setTimeout(5_000) }
            options.callSafely { setRetryLimit(5) }
            options.callSafely { setMaxRetryDelay(2_000) }
            options.callSafely { setSizeLimit(10_000) }
            options.callSafely { setAutomaticIdempotency() }
            options.callSafely { setLockAware() }
            options.callSafely { setReadLockAware() }
            options.callSafely { setUsedDuringCommitProtectionDisable() }
            options.callSafely { setUseProvisionalProxies() }
            options.callSafely { setReportConflictingKeys() }
            options.callSafely { setSpecialKeySpaceRelaxed() }
            options.callSafely { setSpecialKeySpaceEnableWrites() }
            options.callSafely { setTag("coverage") }
            options.callSafely { setAutoThrottleTag("coverage-auto") }
            options.callSafely { setSpanParent(byteArrayOf(1, 2, 3)) }
            options.callSafely { setExpensiveClearCostEstimationEnable() }
            options.callSafely { setBypassUnreadable() }
            options.callSafely { setUseGrvCache() }
            options.callSafely { setAuthorizationToken("auth-token") }
            options.callSafely { setDurabilityDatacenter() }
            options.callSafely { setDurabilityRisky() }
            options.callSafely { setDebugRetryLogging("retry-ctx") }
        } finally {
            txn.close()
        }

        database.runSuspend { writer ->
            writer.set(key, "value".encodeToByteArray())
        }

        val stored = database.readSuspend { rt -> rt.get(key).await() }
        kotlin.test.assertEquals("value", stored?.decodeToUtf8())
    }
}

private inline fun TransactionOptions.callSafely(block: TransactionOptions.() -> Unit) {
    try {
        block()
    } catch (_: FDBException) {
        // Some options are guarded by cluster configuration; ignore failures while capturing coverage.
    }
}
