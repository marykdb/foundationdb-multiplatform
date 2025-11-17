package maryk.foundationdb.options

import maryk.foundationdb.DatabaseOptionSink
import maryk.foundationdb.TransactionOptionSink

internal class RecordingDatabaseSink : DatabaseOptionSink {
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

internal class RecordingDatabaseSinkAll : DatabaseOptionSink {
    val seen = mutableListOf<String>()
    private fun mark(name: String) { seen += name }
    override fun setLocationCacheSize(value: Long) = mark("setLocationCacheSize")
    override fun setMaxWatches(value: Long) = mark("setMaxWatches")
    override fun setMachineId(id: String) = mark("setMachineId")
    override fun setDatacenterId(id: String) = mark("setDatacenterId")
    override fun setSnapshotRywEnable() = mark("setSnapshotRywEnable")
    override fun setSnapshotRywDisable() = mark("setSnapshotRywDisable")
    override fun setTransactionLoggingMaxFieldLength(value: Long) = mark("setTransactionLoggingMaxFieldLength")
    override fun setTransactionTimeout(value: Long) = mark("setTransactionTimeout")
    override fun setTransactionRetryLimit(value: Long) = mark("setTransactionRetryLimit")
    override fun setTransactionMaxRetryDelay(value: Long) = mark("setTransactionMaxRetryDelay")
    override fun setTransactionSizeLimit(value: Long) = mark("setTransactionSizeLimit")
    override fun setTransactionCausalReadRisky() = mark("setTransactionCausalReadRisky")
    override fun setTransactionAutomaticIdempotency() = mark("setTransactionAutomaticIdempotency")
    override fun setTransactionBypassUnreadable() = mark("setTransactionBypassUnreadable")
    override fun setTransactionUsedDuringCommitProtectionDisable() = mark("setTransactionUsedDuringCommitProtectionDisable")
    override fun setTransactionReportConflictingKeys() = mark("setTransactionReportConflictingKeys")
    override fun setUseConfigDatabase() = mark("setUseConfigDatabase")
    override fun setTestCausalReadRisky(value: Long) = mark("setTestCausalReadRisky")

    companion object {
        val expected = listOf(
            "setLocationCacheSize",
            "setMaxWatches",
            "setMachineId",
            "setDatacenterId",
            "setSnapshotRywEnable",
            "setSnapshotRywDisable",
            "setTransactionLoggingMaxFieldLength",
            "setTransactionTimeout",
            "setTransactionRetryLimit",
            "setTransactionMaxRetryDelay",
            "setTransactionSizeLimit",
            "setTransactionCausalReadRisky",
            "setTransactionAutomaticIdempotency",
            "setTransactionBypassUnreadable",
            "setTransactionUsedDuringCommitProtectionDisable",
            "setTransactionReportConflictingKeys",
            "setUseConfigDatabase",
            "setTestCausalReadRisky"
        )
    }
}

internal open class TransactionOptionSinkAdapter : TransactionOptionSink {
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

internal class RecordingTransactionSink : TransactionOptionSinkAdapter() {
    val events = mutableListOf<String>()
    override fun setCausalWriteRisky() { events += "causalWriteRisky" }
    override fun setTimeout(value: Long) { events += "timeout:$value" }
    override fun setTag(value: String) { events += "tag:$value" }
    override fun setReadPriorityHigh() { events += "priorityHigh" }
}

internal class RecordingTransactionSinkAll : TransactionOptionSinkAdapter() {
    val seen = mutableListOf<String>()
    private fun mark(name: String) { seen += name }

    override fun setCausalWriteRisky() = mark("setCausalWriteRisky")
    override fun setCausalReadRisky() = mark("setCausalReadRisky")
    override fun setIncludePortInAddress() = mark("setIncludePortInAddress")
    override fun setCausalReadDisable() = mark("setCausalReadDisable")
    override fun setReadYourWritesDisable() = mark("setReadYourWritesDisable")
    override fun setNextWriteNoWriteConflictRange() = mark("setNextWriteNoWriteConflictRange")
    override fun setSnapshotRywEnable() = mark("setSnapshotRywEnable")
    override fun setSnapshotRywDisable() = mark("setSnapshotRywDisable")
    override fun setReadServerSideCacheEnable() = mark("setReadServerSideCacheEnable")
    override fun setReadServerSideCacheDisable() = mark("setReadServerSideCacheDisable")
    override fun setReadPriorityNormal() = mark("setReadPriorityNormal")
    override fun setReadPriorityLow() = mark("setReadPriorityLow")
    override fun setReadPriorityHigh() = mark("setReadPriorityHigh")
    override fun setPrioritySystemImmediate() = mark("setPrioritySystemImmediate")
    override fun setPriorityBatch() = mark("setPriorityBatch")
    override fun setInitializeNewDatabase() = mark("setInitializeNewDatabase")
    override fun setAccessSystemKeys() = mark("setAccessSystemKeys")
    override fun setReadSystemKeys() = mark("setReadSystemKeys")
    override fun setRawAccess() = mark("setRawAccess")
    override fun setBypassStorageQuota() = mark("setBypassStorageQuota")
    override fun setDebugTransactionIdentifier(value: String) = mark("setDebugTransactionIdentifier")
    override fun setLogTransaction() = mark("setLogTransaction")
    override fun setTransactionLoggingMaxFieldLength(value: Long) = mark("setTransactionLoggingMaxFieldLength")
    override fun setServerRequestTracing() = mark("setServerRequestTracing")
    override fun setTimeout(value: Long) = mark("setTimeout")
    override fun setRetryLimit(value: Long) = mark("setRetryLimit")
    override fun setMaxRetryDelay(value: Long) = mark("setMaxRetryDelay")
    override fun setSizeLimit(value: Long) = mark("setSizeLimit")
    override fun setAutomaticIdempotency() = mark("setAutomaticIdempotency")
    override fun setLockAware() = mark("setLockAware")
    override fun setReadLockAware() = mark("setReadLockAware")
    override fun setUsedDuringCommitProtectionDisable() = mark("setUsedDuringCommitProtectionDisable")
    override fun setUseProvisionalProxies() = mark("setUseProvisionalProxies")
    override fun setReportConflictingKeys() = mark("setReportConflictingKeys")
    override fun setSpecialKeySpaceRelaxed() = mark("setSpecialKeySpaceRelaxed")
    override fun setSpecialKeySpaceEnableWrites() = mark("setSpecialKeySpaceEnableWrites")
    override fun setTag(value: String) = mark("setTag")
    override fun setAutoThrottleTag(value: String) = mark("setAutoThrottleTag")
    override fun setSpanParent(value: ByteArray) = mark("setSpanParent")
    override fun setExpensiveClearCostEstimationEnable() = mark("setExpensiveClearCostEstimationEnable")
    override fun setBypassUnreadable() = mark("setBypassUnreadable")
    override fun setUseGrvCache() = mark("setUseGrvCache")
    override fun setAuthorizationToken(value: String) = mark("setAuthorizationToken")
    override fun setDurabilityDatacenter() = mark("setDurabilityDatacenter")
    override fun setDurabilityRisky() = mark("setDurabilityRisky")
    override fun setDebugRetryLogging(value: String) = mark("setDebugRetryLogging")

    companion object {
        val expected = listOf(
            "setCausalWriteRisky",
            "setCausalReadRisky",
            "setIncludePortInAddress",
            "setCausalReadDisable",
            "setReadYourWritesDisable",
            "setNextWriteNoWriteConflictRange",
            "setSnapshotRywEnable",
            "setSnapshotRywDisable",
            "setReadServerSideCacheEnable",
            "setReadServerSideCacheDisable",
            "setReadPriorityNormal",
            "setReadPriorityLow",
            "setReadPriorityHigh",
            "setPrioritySystemImmediate",
            "setPriorityBatch",
            "setInitializeNewDatabase",
            "setAccessSystemKeys",
            "setReadSystemKeys",
            "setRawAccess",
            "setBypassStorageQuota",
            "setDebugTransactionIdentifier",
            "setLogTransaction",
            "setTransactionLoggingMaxFieldLength",
            "setServerRequestTracing",
            "setTimeout",
            "setRetryLimit",
            "setMaxRetryDelay",
            "setSizeLimit",
            "setAutomaticIdempotency",
            "setLockAware",
            "setReadLockAware",
            "setUsedDuringCommitProtectionDisable",
            "setUseProvisionalProxies",
            "setReportConflictingKeys",
            "setSpecialKeySpaceRelaxed",
            "setSpecialKeySpaceEnableWrites",
            "setTag",
            "setAutoThrottleTag",
            "setSpanParent",
            "setExpensiveClearCostEstimationEnable",
            "setBypassUnreadable",
            "setUseGrvCache",
            "setAuthorizationToken",
            "setDurabilityDatacenter",
            "setDurabilityRisky",
            "setDebugRetryLogging"
        )
    }
}
