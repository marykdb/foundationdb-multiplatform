package maryk.foundationdb

expect class DatabaseOptions {
    fun setLocationCacheSize(value: Long)
    fun setMaxWatches(value: Long)
    fun setMachineId(id: String)
    fun setDatacenterId(id: String)
    fun setSnapshotRywEnable()
    fun setSnapshotRywDisable()
    fun setTransactionLoggingMaxFieldLength(value: Long)
    fun setTransactionTimeout(value: Long)
    fun setTransactionRetryLimit(value: Long)
    fun setTransactionMaxRetryDelay(value: Long)
    fun setTransactionSizeLimit(value: Long)
    fun setTransactionCausalReadRisky()
    fun setTransactionIncludePortInAddress()
    fun setTransactionAutomaticIdempotency()
    fun setTransactionBypassUnreadable()
    fun setTransactionUsedDuringCommitProtectionDisable()
    fun setTransactionReportConflictingKeys()
    fun setUseConfigDatabase()
    fun setTestCausalReadRisky(value: Long)
}
