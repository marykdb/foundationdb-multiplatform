package maryk.foundationdb.options

import maryk.foundationdb.TransactionOptionSink
import maryk.foundationdb.TransactionOptions

sealed interface TransactionOption {
    fun applyTo(sink: TransactionOptionSink)

    object CausalWriteRisky : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setCausalWriteRisky() }
    object CausalReadRisky : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setCausalReadRisky() }
    object IncludePortInAddress : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setIncludePortInAddress() }
    object CausalReadDisable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setCausalReadDisable() }
    object ReadYourWritesDisable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadYourWritesDisable() }
    object NextWriteNoWriteConflictRange : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setNextWriteNoWriteConflictRange() }
    object SnapshotRywEnable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setSnapshotRywEnable() }
    object SnapshotRywDisable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setSnapshotRywDisable() }
    object ReadServerSideCacheEnable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadServerSideCacheEnable() }
    object ReadServerSideCacheDisable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadServerSideCacheDisable() }
    object ReadPriorityNormal : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadPriorityNormal() }
    object ReadPriorityLow : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadPriorityLow() }
    object ReadPriorityHigh : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadPriorityHigh() }
    object PrioritySystemImmediate : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setPrioritySystemImmediate() }
    object PriorityBatch : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setPriorityBatch() }
    object InitializeNewDatabase : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setInitializeNewDatabase() }
    object AccessSystemKeys : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setAccessSystemKeys() }
    object ReadSystemKeys : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadSystemKeys() }
    object RawAccess : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setRawAccess() }
    object BypassStorageQuota : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setBypassStorageQuota() }
    data class DebugTransactionIdentifier(val id: String) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setDebugTransactionIdentifier(id) }
    object LogTransaction : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setLogTransaction() }
    data class TransactionLoggingMaxFieldLength(val length: Long) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setTransactionLoggingMaxFieldLength(length) }
    object ServerRequestTracing : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setServerRequestTracing() }
    data class Timeout(val ms: Long) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setTimeout(ms) }
    data class RetryLimit(val limit: Long) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setRetryLimit(limit) }
    data class MaxRetryDelay(val ms: Long) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setMaxRetryDelay(ms) }
    data class SizeLimit(val bytes: Long) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setSizeLimit(bytes) }
    object AutomaticIdempotency : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setAutomaticIdempotency() }
    object LockAware : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setLockAware() }
    object ReadLockAware : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReadLockAware() }
    object UsedDuringCommitProtectionDisable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setUsedDuringCommitProtectionDisable() }
    object UseProvisionalProxies : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setUseProvisionalProxies() }
    object ReportConflictingKeys : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setReportConflictingKeys() }
    object SpecialKeySpaceRelaxed : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setSpecialKeySpaceRelaxed() }
    object SpecialKeySpaceEnableWrites : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setSpecialKeySpaceEnableWrites() }
    data class Tag(val value: String) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setTag(value) }
    data class AutoThrottleTag(val value: String) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setAutoThrottleTag(value) }
    data class SpanParent(val bytes: ByteArray) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setSpanParent(bytes) }
    object ExpensiveClearCostEstimationEnable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setExpensiveClearCostEstimationEnable() }
    object BypassUnreadable : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setBypassUnreadable() }
    object UseGrvCache : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setUseGrvCache() }
    data class AuthorizationToken(val value: String) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setAuthorizationToken(value) }
    object DurabilityDatacenter : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setDurabilityDatacenter() }
    object DurabilityRisky : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setDurabilityRisky() }
    data class DebugRetryLogging(val value: String) : TransactionOption { override fun applyTo(sink: TransactionOptionSink) = sink.setDebugRetryLogging(value) }
}

fun TransactionOptions.apply(option: TransactionOption) {
    option.applyTo(this)
}

fun TransactionOptions.apply(options: Iterable<TransactionOption>) {
    options.forEach { it.applyTo(this) }
}

fun Iterable<TransactionOption>.applyTo(sink: TransactionOptionSink) {
    forEach { it.applyTo(sink) }
}

inline fun TransactionOptions.configure(block: TransactionOptionBuilder.() -> Unit) {
    TransactionOptionBuilder(this, null).apply(block)
}

class TransactionOptionBuilder @PublishedApi internal constructor(
    private val sink: TransactionOptionSink?,
    private val collector: MutableList<TransactionOption>?
) {
    private fun add(option: TransactionOption) {
        when {
            collector != null -> collector.add(option)
            sink != null -> option.applyTo(sink)
            else -> error("TransactionOptionBuilder must have sink or collector")
        }
    }

    fun causalWriteRisky() = add(TransactionOption.CausalWriteRisky)
    fun causalReadRisky() = add(TransactionOption.CausalReadRisky)
    fun includePortInAddress() = add(TransactionOption.IncludePortInAddress)
    fun causalReadDisable() = add(TransactionOption.CausalReadDisable)
    fun readYourWritesDisable() = add(TransactionOption.ReadYourWritesDisable)
    fun nextWriteNoWriteConflictRange() = add(TransactionOption.NextWriteNoWriteConflictRange)
    fun snapshotReadYourWritesEnable() = add(TransactionOption.SnapshotRywEnable)
    fun snapshotReadYourWritesDisable() = add(TransactionOption.SnapshotRywDisable)
    fun readServerSideCacheEnable() = add(TransactionOption.ReadServerSideCacheEnable)
    fun readServerSideCacheDisable() = add(TransactionOption.ReadServerSideCacheDisable)
    fun readPriorityNormal() = add(TransactionOption.ReadPriorityNormal)
    fun readPriorityLow() = add(TransactionOption.ReadPriorityLow)
    fun readPriorityHigh() = add(TransactionOption.ReadPriorityHigh)
    fun prioritySystemImmediate() = add(TransactionOption.PrioritySystemImmediate)
    fun priorityBatch() = add(TransactionOption.PriorityBatch)
    fun initializeNewDatabase() = add(TransactionOption.InitializeNewDatabase)
    fun accessSystemKeys() = add(TransactionOption.AccessSystemKeys)
    fun readSystemKeys() = add(TransactionOption.ReadSystemKeys)
    fun rawAccess() = add(TransactionOption.RawAccess)
    fun bypassStorageQuota() = add(TransactionOption.BypassStorageQuota)
    fun debugTransactionIdentifier(id: String) = add(TransactionOption.DebugTransactionIdentifier(id))
    fun logTransaction() = add(TransactionOption.LogTransaction)
    fun transactionLoggingMaxFieldLength(length: Long) = add(TransactionOption.TransactionLoggingMaxFieldLength(length))
    fun serverRequestTracing() = add(TransactionOption.ServerRequestTracing)
    fun timeout(ms: Long) = add(TransactionOption.Timeout(ms))
    fun retryLimit(limit: Long) = add(TransactionOption.RetryLimit(limit))
    fun maxRetryDelay(ms: Long) = add(TransactionOption.MaxRetryDelay(ms))
    fun sizeLimit(bytes: Long) = add(TransactionOption.SizeLimit(bytes))
    fun automaticIdempotency() = add(TransactionOption.AutomaticIdempotency)
    fun lockAware() = add(TransactionOption.LockAware)
    fun readLockAware() = add(TransactionOption.ReadLockAware)
    fun usedDuringCommitProtectionDisable() = add(TransactionOption.UsedDuringCommitProtectionDisable)
    fun useProvisionalProxies() = add(TransactionOption.UseProvisionalProxies)
    fun reportConflictingKeys() = add(TransactionOption.ReportConflictingKeys)
    fun specialKeySpaceRelaxed() = add(TransactionOption.SpecialKeySpaceRelaxed)
    fun specialKeySpaceEnableWrites() = add(TransactionOption.SpecialKeySpaceEnableWrites)
    fun tag(value: String) = add(TransactionOption.Tag(value))
    fun autoThrottleTag(value: String) = add(TransactionOption.AutoThrottleTag(value))
    fun spanParent(bytes: ByteArray) = add(TransactionOption.SpanParent(bytes))
    fun expensiveClearCostEstimationEnable() = add(TransactionOption.ExpensiveClearCostEstimationEnable)
    fun bypassUnreadable() = add(TransactionOption.BypassUnreadable)
    fun useGrvCache() = add(TransactionOption.UseGrvCache)
    fun authorizationToken(value: String) = add(TransactionOption.AuthorizationToken(value))
    fun durabilityDatacenter() = add(TransactionOption.DurabilityDatacenter)
    fun durabilityRisky() = add(TransactionOption.DurabilityRisky)
    fun debugRetryLogging(value: String) = add(TransactionOption.DebugRetryLogging(value))
}

inline fun transactionOptions(block: TransactionOptionBuilder.() -> Unit): List<TransactionOption> {
    val collected = mutableListOf<TransactionOption>()
    TransactionOptionBuilder(null, collected).apply(block)
    return collected.toList()
}
