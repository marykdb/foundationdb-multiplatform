@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBTransaction
import foundationdb.c.FDB_TR_OPTION_ACCESS_SYSTEM_KEYS
import foundationdb.c.FDB_TR_OPTION_AUTO_THROTTLE_TAG
import foundationdb.c.FDB_TR_OPTION_AUTOMATIC_IDEMPOTENCY
import foundationdb.c.FDB_TR_OPTION_AUTHORIZATION_TOKEN
import foundationdb.c.FDB_TR_OPTION_BYPASS_STORAGE_QUOTA
import foundationdb.c.FDB_TR_OPTION_BYPASS_UNREADABLE
import foundationdb.c.FDB_TR_OPTION_CAUSAL_READ_DISABLE
import foundationdb.c.FDB_TR_OPTION_CAUSAL_READ_RISKY
import foundationdb.c.FDB_TR_OPTION_CAUSAL_WRITE_RISKY
import foundationdb.c.FDB_TR_OPTION_DEBUG_RETRY_LOGGING
import foundationdb.c.FDB_TR_OPTION_DEBUG_TRANSACTION_IDENTIFIER
import foundationdb.c.FDB_TR_OPTION_DURABILITY_DATACENTER
import foundationdb.c.FDB_TR_OPTION_DURABILITY_RISKY
import foundationdb.c.FDB_TR_OPTION_EXPENSIVE_CLEAR_COST_ESTIMATION_ENABLE
import foundationdb.c.FDB_TR_OPTION_INCLUDE_PORT_IN_ADDRESS
import foundationdb.c.FDB_TR_OPTION_INITIALIZE_NEW_DATABASE
import foundationdb.c.FDB_TR_OPTION_LOG_TRANSACTION
import foundationdb.c.FDB_TR_OPTION_MAX_RETRY_DELAY
import foundationdb.c.FDB_TR_OPTION_NEXT_WRITE_NO_WRITE_CONFLICT_RANGE
import foundationdb.c.FDB_TR_OPTION_PRIORITY_BATCH
import foundationdb.c.FDB_TR_OPTION_PRIORITY_SYSTEM_IMMEDIATE
import foundationdb.c.FDB_TR_OPTION_RAW_ACCESS
import foundationdb.c.FDB_TR_OPTION_LOCK_AWARE
import foundationdb.c.FDB_TR_OPTION_READ_LOCK_AWARE
import foundationdb.c.FDB_TR_OPTION_READ_PRIORITY_HIGH
import foundationdb.c.FDB_TR_OPTION_READ_PRIORITY_LOW
import foundationdb.c.FDB_TR_OPTION_READ_PRIORITY_NORMAL
import foundationdb.c.FDB_TR_OPTION_READ_SERVER_SIDE_CACHE_DISABLE
import foundationdb.c.FDB_TR_OPTION_READ_SERVER_SIDE_CACHE_ENABLE
import foundationdb.c.FDB_TR_OPTION_READ_SYSTEM_KEYS
import foundationdb.c.FDB_TR_OPTION_READ_YOUR_WRITES_DISABLE
import foundationdb.c.FDB_TR_OPTION_REPORT_CONFLICTING_KEYS
import foundationdb.c.FDB_TR_OPTION_RETRY_LIMIT
import foundationdb.c.FDB_TR_OPTION_SERVER_REQUEST_TRACING
import foundationdb.c.FDB_TR_OPTION_SIZE_LIMIT
import foundationdb.c.FDB_TR_OPTION_SNAPSHOT_RYW_DISABLE
import foundationdb.c.FDB_TR_OPTION_SNAPSHOT_RYW_ENABLE
import foundationdb.c.FDB_TR_OPTION_SPAN_PARENT
import foundationdb.c.FDB_TR_OPTION_SPECIAL_KEY_SPACE_ENABLE_WRITES
import foundationdb.c.FDB_TR_OPTION_SPECIAL_KEY_SPACE_RELAXED
import foundationdb.c.FDB_TR_OPTION_TAG
import foundationdb.c.FDB_TR_OPTION_TIMEOUT
import foundationdb.c.FDB_TR_OPTION_TRANSACTION_LOGGING_MAX_FIELD_LENGTH
import foundationdb.c.FDB_TR_OPTION_USE_GRV_CACHE
import foundationdb.c.FDB_TR_OPTION_USE_PROVISIONAL_PROXIES
import foundationdb.c.FDB_TR_OPTION_USED_DURING_COMMIT_PROTECTION_DISABLE
import foundationdb.c.fdb_transaction_set_option
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

actual class TransactionOptions internal constructor(private val pointer: CPointer<FDBTransaction>) : TransactionOptionSink {
    private fun set(option: UInt) {
        checkError(fdb_transaction_set_option(pointer, option, null, 0))
    }

    private fun set(option: UInt, bytes: ByteArray) {
        bytes.usePinned { pinned ->
            val ptr = pinned.addressOf(0).reinterpret<UByteVar>()
            checkError(fdb_transaction_set_option(pointer, option, ptr, bytes.size))
        }
    }

    private fun set(option: UInt, value: Long) = memScoped {
        val ref = alloc<LongVar>()
        ref.value = value
        val ptr = ref.ptr.reinterpret<UByteVar>()
        checkError(fdb_transaction_set_option(pointer, option, ptr, sizeOf<LongVar>().toInt()))
    }

    actual override fun setCausalWriteRisky() = set(FDB_TR_OPTION_CAUSAL_WRITE_RISKY)
    actual override fun setCausalReadRisky() = set(FDB_TR_OPTION_CAUSAL_READ_RISKY)
    actual override fun setIncludePortInAddress() = set(FDB_TR_OPTION_INCLUDE_PORT_IN_ADDRESS)
    actual override fun setCausalReadDisable() = set(FDB_TR_OPTION_CAUSAL_READ_DISABLE)
    actual override fun setReadYourWritesDisable() = set(FDB_TR_OPTION_READ_YOUR_WRITES_DISABLE)
    actual override fun setNextWriteNoWriteConflictRange() = set(FDB_TR_OPTION_NEXT_WRITE_NO_WRITE_CONFLICT_RANGE)
    actual override fun setSnapshotRywEnable() = set(FDB_TR_OPTION_SNAPSHOT_RYW_ENABLE)
    actual override fun setSnapshotRywDisable() = set(FDB_TR_OPTION_SNAPSHOT_RYW_DISABLE)
    actual override fun setReadServerSideCacheEnable() = set(FDB_TR_OPTION_READ_SERVER_SIDE_CACHE_ENABLE)
    actual override fun setReadServerSideCacheDisable() = set(FDB_TR_OPTION_READ_SERVER_SIDE_CACHE_DISABLE)
    actual override fun setReadPriorityNormal() = set(FDB_TR_OPTION_READ_PRIORITY_NORMAL)
    actual override fun setReadPriorityLow() = set(FDB_TR_OPTION_READ_PRIORITY_LOW)
    actual override fun setReadPriorityHigh() = set(FDB_TR_OPTION_READ_PRIORITY_HIGH)
    actual override fun setPrioritySystemImmediate() = set(FDB_TR_OPTION_PRIORITY_SYSTEM_IMMEDIATE)
    actual override fun setPriorityBatch() = set(FDB_TR_OPTION_PRIORITY_BATCH)
    actual override fun setInitializeNewDatabase() = set(FDB_TR_OPTION_INITIALIZE_NEW_DATABASE)
    actual override fun setAccessSystemKeys() = set(FDB_TR_OPTION_ACCESS_SYSTEM_KEYS)
    actual override fun setReadSystemKeys() = set(FDB_TR_OPTION_READ_SYSTEM_KEYS)
    actual override fun setRawAccess() = set(FDB_TR_OPTION_RAW_ACCESS)
    actual override fun setBypassStorageQuota() = set(FDB_TR_OPTION_BYPASS_STORAGE_QUOTA)
    actual override fun setDebugTransactionIdentifier(value: String) = set(FDB_TR_OPTION_DEBUG_TRANSACTION_IDENTIFIER, value.encodeToByteArray())
    actual override fun setLogTransaction() = set(FDB_TR_OPTION_LOG_TRANSACTION)
    actual override fun setTransactionLoggingMaxFieldLength(value: Long) = set(FDB_TR_OPTION_TRANSACTION_LOGGING_MAX_FIELD_LENGTH, value)
    actual override fun setServerRequestTracing() = set(FDB_TR_OPTION_SERVER_REQUEST_TRACING)
    actual override fun setTimeout(value: Long) = set(FDB_TR_OPTION_TIMEOUT, value)
    actual override fun setRetryLimit(value: Long) = set(FDB_TR_OPTION_RETRY_LIMIT, value)
    actual override fun setMaxRetryDelay(value: Long) = set(FDB_TR_OPTION_MAX_RETRY_DELAY, value)
    actual override fun setSizeLimit(value: Long) = set(FDB_TR_OPTION_SIZE_LIMIT, value)
    actual override fun setAutomaticIdempotency() = set(FDB_TR_OPTION_AUTOMATIC_IDEMPOTENCY)
    actual override fun setLockAware() = set(FDB_TR_OPTION_LOCK_AWARE)
    actual override fun setReadLockAware() = set(FDB_TR_OPTION_READ_LOCK_AWARE)
    actual override fun setUsedDuringCommitProtectionDisable() = set(FDB_TR_OPTION_USED_DURING_COMMIT_PROTECTION_DISABLE)
    actual override fun setUseProvisionalProxies() = set(FDB_TR_OPTION_USE_PROVISIONAL_PROXIES)
    actual override fun setReportConflictingKeys() = set(FDB_TR_OPTION_REPORT_CONFLICTING_KEYS)
    actual override fun setSpecialKeySpaceRelaxed() = set(FDB_TR_OPTION_SPECIAL_KEY_SPACE_RELAXED)
    actual override fun setSpecialKeySpaceEnableWrites() = set(FDB_TR_OPTION_SPECIAL_KEY_SPACE_ENABLE_WRITES)
    actual override fun setTag(value: String) = set(FDB_TR_OPTION_TAG, value.encodeToByteArray())
    actual override fun setAutoThrottleTag(value: String) = set(FDB_TR_OPTION_AUTO_THROTTLE_TAG, value.encodeToByteArray())
    actual override fun setSpanParent(value: ByteArray) = set(FDB_TR_OPTION_SPAN_PARENT, value)
    actual override fun setExpensiveClearCostEstimationEnable() = set(FDB_TR_OPTION_EXPENSIVE_CLEAR_COST_ESTIMATION_ENABLE)
    actual override fun setBypassUnreadable() = set(FDB_TR_OPTION_BYPASS_UNREADABLE)
    actual override fun setUseGrvCache() = set(FDB_TR_OPTION_USE_GRV_CACHE)
    actual override fun setAuthorizationToken(value: String) = set(FDB_TR_OPTION_AUTHORIZATION_TOKEN, value.encodeToByteArray())
    actual override fun setDurabilityDatacenter() = set(FDB_TR_OPTION_DURABILITY_DATACENTER)
    actual override fun setDurabilityRisky() = set(FDB_TR_OPTION_DURABILITY_RISKY)
    actual override fun setDebugRetryLogging(value: String) = set(FDB_TR_OPTION_DEBUG_RETRY_LOGGING, value.encodeToByteArray())
}
