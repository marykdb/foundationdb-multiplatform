@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDB_DB_OPTION_TRANSACTION_INCLUDE_PORT_IN_ADDRESS

import foundationdb.c.FDBDatabase
import foundationdb.c.FDB_DB_OPTION_DATACENTER_ID
import foundationdb.c.FDB_DB_OPTION_LOCATION_CACHE_SIZE
import foundationdb.c.FDB_DB_OPTION_MACHINE_ID
import foundationdb.c.FDB_DB_OPTION_MAX_WATCHES
import foundationdb.c.FDB_DB_OPTION_SNAPSHOT_RYW_DISABLE
import foundationdb.c.FDB_DB_OPTION_SNAPSHOT_RYW_ENABLE
import foundationdb.c.FDB_DB_OPTION_TEST_CAUSAL_READ_RISKY
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_AUTOMATIC_IDEMPOTENCY
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_BYPASS_UNREADABLE
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_CAUSAL_READ_RISKY
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_LOGGING_MAX_FIELD_LENGTH
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_MAX_RETRY_DELAY
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_RETRY_LIMIT
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_REPORT_CONFLICTING_KEYS
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_SIZE_LIMIT
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_TIMEOUT
import foundationdb.c.FDB_DB_OPTION_TRANSACTION_USED_DURING_COMMIT_PROTECTION_DISABLE
import foundationdb.c.FDB_DB_OPTION_USE_CONFIG_DATABASE
import foundationdb.c.fdb_database_set_option
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value

actual class DatabaseOptions internal constructor(private val handle: NativeDatabaseHandle?, private val customConsumer: OptionConsumer? = null) : DatabaseOptionSink {
    actual constructor(consumer: OptionConsumer) : this(null, consumer)

    private fun set(option: UInt) {
        customConsumer?.let { it.setOption(option.toInt(), null); return }
        requireNotNull(handle).usePointer { pointer ->
            checkError(fdb_database_set_option(pointer, option, null, 0))
        }
    }

    private fun set(option: UInt, bytes: ByteArray) {
        customConsumer?.let { it.setOption(option.toInt(), bytes); return }
        requireNotNull(handle).usePointer { pointer ->
            bytes.withPointer { ptr, length ->
                checkError(fdb_database_set_option(pointer, option, ptr, length))
            }
        }
    }

    private fun set(option: UInt, value: Long) = set(option, ByteArrayUtil.encodeInt(value))

    actual override fun setLocationCacheSize(value: Long) = set(FDB_DB_OPTION_LOCATION_CACHE_SIZE, value)
    actual override fun setMaxWatches(value: Long) = set(FDB_DB_OPTION_MAX_WATCHES, value)
    actual override fun setMachineId(id: String) = set(FDB_DB_OPTION_MACHINE_ID, id.encodeToByteArray())
    actual override fun setDatacenterId(id: String) = set(FDB_DB_OPTION_DATACENTER_ID, id.encodeToByteArray())
    actual override fun setSnapshotRywEnable() = set(FDB_DB_OPTION_SNAPSHOT_RYW_ENABLE)
    actual override fun setSnapshotRywDisable() = set(FDB_DB_OPTION_SNAPSHOT_RYW_DISABLE)
    actual override fun setTransactionLoggingMaxFieldLength(value: Long) = set(FDB_DB_OPTION_TRANSACTION_LOGGING_MAX_FIELD_LENGTH, value)
    actual override fun setTransactionTimeout(value: Long) = set(FDB_DB_OPTION_TRANSACTION_TIMEOUT, value)
    actual override fun setTransactionRetryLimit(value: Long) = set(FDB_DB_OPTION_TRANSACTION_RETRY_LIMIT, value)
    actual override fun setTransactionMaxRetryDelay(value: Long) = set(FDB_DB_OPTION_TRANSACTION_MAX_RETRY_DELAY, value)
    actual override fun setTransactionSizeLimit(value: Long) = set(FDB_DB_OPTION_TRANSACTION_SIZE_LIMIT, value)
    actual override fun setTransactionCausalReadRisky() = set(FDB_DB_OPTION_TRANSACTION_CAUSAL_READ_RISKY)
    actual override fun setTransactionAutomaticIdempotency() = set(FDB_DB_OPTION_TRANSACTION_AUTOMATIC_IDEMPOTENCY)
    actual override fun setTransactionBypassUnreadable() = set(FDB_DB_OPTION_TRANSACTION_BYPASS_UNREADABLE)
    actual override fun setTransactionUsedDuringCommitProtectionDisable() = set(FDB_DB_OPTION_TRANSACTION_USED_DURING_COMMIT_PROTECTION_DISABLE)
    actual override fun setTransactionReportConflictingKeys() = set(FDB_DB_OPTION_TRANSACTION_REPORT_CONFLICTING_KEYS)
    actual override fun setUseConfigDatabase() = set(FDB_DB_OPTION_USE_CONFIG_DATABASE)
    actual override fun setTestCausalReadRisky(value: Long) = set(FDB_DB_OPTION_TEST_CAUSAL_READ_RISKY, value)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual override fun setTransactionIncludePortInAddress() = set(FDB_DB_OPTION_TRANSACTION_INCLUDE_PORT_IN_ADDRESS)
    actual fun getOptionConsumer(): OptionConsumer = OptionConsumer { code, value ->
        if (value == null) set(code.toUInt()) else set(code.toUInt(), value)
    }

}
