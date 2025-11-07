package maryk.foundationdb.options

import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.foundationdb.DatabaseOptionSink
import maryk.foundationdb.TransactionOptionSink

class OptionsHelperTest {
    @Test
    fun databaseOptionsSetCollectsAndApplies() {
        val sink = RecordingDatabaseSink()
        val options = databaseOptions {
            locationCacheSize(1024)
            snapshotReadYourWritesDisable()
            transactionTimeout(5000)
        }
        options.applyTo(sink)
        assertEquals(
            listOf(
                "location:1024",
                "snapshotDisable",
                "timeout:5000"
            ),
            sink.events
        )
    }

    @Test
    fun transactionOptionsCollectedAndApplied() {
        val sink = RecordingTransactionSink()
        val options = transactionOptions {
            causalWriteRisky()
            timeout(2000)
            tag("bulk-load")
            readPriorityHigh()
        }
        options.applyTo(sink)
        assertEquals(
            listOf(
                "causalWriteRisky",
                "timeout:2000",
                "tag:bulk-load",
                "priorityHigh"
            ),
            sink.events
        )
    }

    @Test
    fun buildersApplyDirectlyToSink() {
        val sink = RecordingDatabaseSink()
        DatabaseOptionBuilder(sink, null).apply {
            maxWatches(10)
            transactionReportConflictingKeys()
        }
        assertEquals(listOf("maxWatches:10", "reportConflicts"), sink.events)
    }
}

private class RecordingDatabaseSink : DatabaseOptionSink {
    val events = mutableListOf<String>()
    override fun setLocationCacheSize(value: Long) { events += "location:$value" }
    override fun setMaxWatches(value: Long) { events += "maxWatches:$value" }
    override fun setMachineId(id: String) { events += "machine:$id" }
    override fun setDatacenterId(id: String) { events += "dc:$id" }
    override fun setSnapshotRywEnable() { events += "snapshotEnable" }
    override fun setSnapshotRywDisable() { events += "snapshotDisable" }
    override fun setTransactionLoggingMaxFieldLength(value: Long) { events += "loggingMax:$value" }
    override fun setTransactionTimeout(value: Long) { events += "timeout:$value" }
    override fun setTransactionRetryLimit(value: Long) { events += "retryLimit:$value" }
    override fun setTransactionMaxRetryDelay(value: Long) { events += "maxRetryDelay:$value" }
    override fun setTransactionSizeLimit(value: Long) { events += "sizeLimit:$value" }
    override fun setTransactionCausalReadRisky() { events += "causalReadRisky" }
    override fun setTransactionAutomaticIdempotency() { events += "autoIdempotency" }
    override fun setTransactionBypassUnreadable() { events += "bypassUnreadable" }
    override fun setTransactionUsedDuringCommitProtectionDisable() { events += "usedDuringCommit" }
    override fun setTransactionReportConflictingKeys() { events += "reportConflicts" }
    override fun setUseConfigDatabase() { events += "useConfigDatabase" }
    override fun setTestCausalReadRisky(value: Long) { events += "testCausal:$value" }
}

private open class TransactionOptionSinkAdapter : TransactionOptionSink {
    override fun setCausalWriteRisky() {}
    override fun setCausalReadRisky() {}
    override fun setIncludePortInAddress() {}
    override fun setCausalReadDisable() {}
    override fun setReadYourWritesDisable() {}
    override fun setNextWriteNoWriteConflictRange() {}
    override fun setSnapshotRywEnable() {}
    override fun setSnapshotRywDisable() {}
    override fun setReadServerSideCacheEnable() {}
    override fun setReadServerSideCacheDisable() {}
    override fun setReadPriorityNormal() {}
    override fun setReadPriorityLow() {}
    override fun setReadPriorityHigh() {}
    override fun setPrioritySystemImmediate() {}
    override fun setPriorityBatch() {}
    override fun setInitializeNewDatabase() {}
    override fun setAccessSystemKeys() {}
    override fun setReadSystemKeys() {}
    override fun setRawAccess() {}
    override fun setBypassStorageQuota() {}
    override fun setDebugTransactionIdentifier(value: String) {}
    override fun setLogTransaction() {}
    override fun setTransactionLoggingMaxFieldLength(value: Long) {}
    override fun setServerRequestTracing() {}
    override fun setTimeout(value: Long) {}
    override fun setRetryLimit(value: Long) {}
    override fun setMaxRetryDelay(value: Long) {}
    override fun setSizeLimit(value: Long) {}
    override fun setAutomaticIdempotency() {}
    override fun setLockAware() {}
    override fun setReadLockAware() {}
    override fun setUsedDuringCommitProtectionDisable() {}
    override fun setUseProvisionalProxies() {}
    override fun setReportConflictingKeys() {}
    override fun setSpecialKeySpaceRelaxed() {}
    override fun setSpecialKeySpaceEnableWrites() {}
    override fun setTag(value: String) {}
    override fun setAutoThrottleTag(value: String) {}
    override fun setSpanParent(value: ByteArray) {}
    override fun setExpensiveClearCostEstimationEnable() {}
    override fun setBypassUnreadable() {}
    override fun setUseGrvCache() {}
    override fun setAuthorizationToken(value: String) {}
    override fun setDurabilityDatacenter() {}
    override fun setDurabilityRisky() {}
    override fun setDebugRetryLogging(value: String) {}
}

private class RecordingTransactionSink : TransactionOptionSinkAdapter() {
    val events = mutableListOf<String>()
    override fun setCausalWriteRisky() { events += "causalWriteRisky" }
    override fun setTimeout(value: Long) { events += "timeout:$value" }
    override fun setTag(value: String) { events += "tag:$value" }
    override fun setReadPriorityHigh() { events += "priorityHigh" }
}
