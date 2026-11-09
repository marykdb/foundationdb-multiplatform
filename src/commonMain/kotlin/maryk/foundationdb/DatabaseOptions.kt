package maryk.foundationdb

interface DatabaseOptionSink {
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
    fun setTransactionAutomaticIdempotency()
    fun setTransactionBypassUnreadable()
    fun setTransactionUsedDuringCommitProtectionDisable()
    fun setTransactionReportConflictingKeys()
    fun setUseConfigDatabase()
    fun setTestCausalReadRisky(value: Long)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    fun setTransactionIncludePortInAddress() { throw UnsupportedOperationException("setTransactionIncludePortInAddress is not implemented by this option sink") }
}

expect class DatabaseOptions : DatabaseOptionSink {
    constructor(consumer: OptionConsumer)
    fun getOptionConsumer(): OptionConsumer
    override fun setLocationCacheSize(value: Long)
    override fun setMaxWatches(value: Long)
    override fun setMachineId(id: String)
    override fun setDatacenterId(id: String)
    override fun setSnapshotRywEnable()
    override fun setSnapshotRywDisable()
    override fun setTransactionLoggingMaxFieldLength(value: Long)
    override fun setTransactionTimeout(value: Long)
    override fun setTransactionRetryLimit(value: Long)
    override fun setTransactionMaxRetryDelay(value: Long)
    override fun setTransactionSizeLimit(value: Long)
    override fun setTransactionCausalReadRisky()
    override fun setTransactionAutomaticIdempotency()
    override fun setTransactionBypassUnreadable()
    override fun setTransactionUsedDuringCommitProtectionDisable()
    override fun setTransactionReportConflictingKeys()
    override fun setUseConfigDatabase()
    override fun setTestCausalReadRisky(value: Long)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    override fun setTransactionIncludePortInAddress()
}
