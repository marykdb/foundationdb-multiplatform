package maryk.foundationdb

actual class DatabaseOptions internal constructor(
    internal val delegate: com.apple.foundationdb.DatabaseOptions
) : DatabaseOptionSink {
    actual override fun setLocationCacheSize(value: Long) {
        delegate.setLocationCacheSize(value)
    }

    actual override fun setMaxWatches(value: Long) {
        delegate.setMaxWatches(value)
    }

    actual override fun setMachineId(id: String) {
        delegate.setMachineId(id)
    }

    actual override fun setDatacenterId(id: String) {
        delegate.setDatacenterId(id)
    }

    actual override fun setSnapshotRywEnable() {
        delegate.setSnapshotRywEnable()
    }

    actual override fun setSnapshotRywDisable() {
        delegate.setSnapshotRywDisable()
    }

    actual override fun setTransactionLoggingMaxFieldLength(value: Long) {
        delegate.setTransactionLoggingMaxFieldLength(value)
    }

    actual override fun setTransactionTimeout(value: Long) {
        delegate.setTransactionTimeout(value)
    }

    actual override fun setTransactionRetryLimit(value: Long) {
        delegate.setTransactionRetryLimit(value)
    }

    actual override fun setTransactionMaxRetryDelay(value: Long) {
        delegate.setTransactionMaxRetryDelay(value)
    }

    actual override fun setTransactionSizeLimit(value: Long) {
        delegate.setTransactionSizeLimit(value)
    }

    actual override fun setTransactionCausalReadRisky() {
        delegate.setTransactionCausalReadRisky()
    }

    actual override fun setTransactionAutomaticIdempotency() {
        delegate.setTransactionAutomaticIdempotency()
    }

    actual override fun setTransactionBypassUnreadable() {
        delegate.setTransactionBypassUnreadable()
    }

    actual override fun setTransactionUsedDuringCommitProtectionDisable() {
        delegate.setTransactionUsedDuringCommitProtectionDisable()
    }

    actual override fun setTransactionReportConflictingKeys() {
        delegate.setTransactionReportConflictingKeys()
    }

    actual override fun setUseConfigDatabase() {
        delegate.setUseConfigDatabase()
    }

    actual override fun setTestCausalReadRisky(value: Long) {
        delegate.setTestCausalReadRisky(value)
    }
}
