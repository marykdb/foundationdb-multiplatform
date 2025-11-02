package maryk.foundationdb

actual class DatabaseOptions internal constructor(
    internal val delegate: com.apple.foundationdb.DatabaseOptions
) {
    actual fun setLocationCacheSize(value: Long) {
        delegate.setLocationCacheSize(value)
    }

    actual fun setMaxWatches(value: Long) {
        delegate.setMaxWatches(value)
    }

    actual fun setMachineId(id: String) {
        delegate.setMachineId(id)
    }

    actual fun setDatacenterId(id: String) {
        delegate.setDatacenterId(id)
    }

    actual fun setSnapshotRywEnable() {
        delegate.setSnapshotRywEnable()
    }

    actual fun setSnapshotRywDisable() {
        delegate.setSnapshotRywDisable()
    }

    actual fun setTransactionLoggingMaxFieldLength(value: Long) {
        delegate.setTransactionLoggingMaxFieldLength(value)
    }

    actual fun setTransactionTimeout(value: Long) {
        delegate.setTransactionTimeout(value)
    }

    actual fun setTransactionRetryLimit(value: Long) {
        delegate.setTransactionRetryLimit(value)
    }

    actual fun setTransactionMaxRetryDelay(value: Long) {
        delegate.setTransactionMaxRetryDelay(value)
    }

    actual fun setTransactionSizeLimit(value: Long) {
        delegate.setTransactionSizeLimit(value)
    }

    actual fun setTransactionCausalReadRisky() {
        delegate.setTransactionCausalReadRisky()
    }

    actual fun setTransactionIncludePortInAddress() {
        delegate.setTransactionIncludePortInAddress()
    }

    actual fun setTransactionAutomaticIdempotency() {
        delegate.setTransactionAutomaticIdempotency()
    }

    actual fun setTransactionBypassUnreadable() {
        delegate.setTransactionBypassUnreadable()
    }

    actual fun setTransactionUsedDuringCommitProtectionDisable() {
        delegate.setTransactionUsedDuringCommitProtectionDisable()
    }

    actual fun setTransactionReportConflictingKeys() {
        delegate.setTransactionReportConflictingKeys()
    }

    actual fun setUseConfigDatabase() {
        delegate.setUseConfigDatabase()
    }

    actual fun setTestCausalReadRisky(value: Long) {
        delegate.setTestCausalReadRisky(value)
    }
}
