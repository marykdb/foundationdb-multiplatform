package maryk.foundationdb.options

import maryk.foundationdb.DatabaseOptionSink
import maryk.foundationdb.DatabaseOptions

sealed interface DatabaseOption {
    fun applyTo(sink: DatabaseOptionSink)

    data class LocationCacheSize(val bytes: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setLocationCacheSize(bytes)
    }

    data class MaxWatches(val count: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setMaxWatches(count)
    }

    data class MachineId(val id: String) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setMachineId(id)
    }

    data class DatacenterId(val id: String) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setDatacenterId(id)
    }

    object SnapshotReadYourWritesEnable : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setSnapshotRywEnable()
    }

    object SnapshotReadYourWritesDisable : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setSnapshotRywDisable()
    }

    data class TransactionLoggingMaxFieldLength(val length: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionLoggingMaxFieldLength(length)
    }

    data class TransactionTimeout(val ms: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionTimeout(ms)
    }

    data class TransactionRetryLimit(val retries: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionRetryLimit(retries)
    }

    data class TransactionMaxRetryDelay(val ms: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionMaxRetryDelay(ms)
    }

    data class TransactionSizeLimit(val bytes: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionSizeLimit(bytes)
    }

    object TransactionCausalReadRisky : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionCausalReadRisky()
    }

    object TransactionAutomaticIdempotency : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionAutomaticIdempotency()
    }

    object TransactionBypassUnreadable : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionBypassUnreadable()
    }

    object TransactionUsedDuringCommitProtectionDisable : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionUsedDuringCommitProtectionDisable()
    }

    object TransactionReportConflictingKeys : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTransactionReportConflictingKeys()
    }

    object UseConfigDatabase : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setUseConfigDatabase()
    }

    data class TestCausalReadRisky(val value: Long) : DatabaseOption {
        override fun applyTo(sink: DatabaseOptionSink) = sink.setTestCausalReadRisky(value)
    }
}

fun DatabaseOptions.apply(option: DatabaseOption) {
    option.applyTo(this)
}

fun DatabaseOptions.apply(options: Iterable<DatabaseOption>) {
    options.forEach { it.applyTo(this) }
}

fun Iterable<DatabaseOption>.applyTo(sink: DatabaseOptionSink) {
    forEach { it.applyTo(sink) }
}

inline fun DatabaseOptions.configure(block: DatabaseOptionBuilder.() -> Unit) {
    DatabaseOptionBuilder(this, null).apply(block)
}

class DatabaseOptionBuilder @PublishedApi internal constructor(
    private val sink: DatabaseOptionSink?,
    private val collector: MutableList<DatabaseOption>?
) {
    private fun add(option: DatabaseOption) {
        when {
            collector != null -> collector.add(option)
            sink != null -> option.applyTo(sink)
            else -> error("DatabaseOptionBuilder must have sink or collector")
        }
    }

    fun locationCacheSize(bytes: Long) = add(DatabaseOption.LocationCacheSize(bytes))
    fun maxWatches(count: Long) = add(DatabaseOption.MaxWatches(count))
    fun machineId(id: String) = add(DatabaseOption.MachineId(id))
    fun datacenterId(id: String) = add(DatabaseOption.DatacenterId(id))
    fun snapshotReadYourWritesEnable() = add(DatabaseOption.SnapshotReadYourWritesEnable)
    fun snapshotReadYourWritesDisable() = add(DatabaseOption.SnapshotReadYourWritesDisable)
    fun transactionLoggingMaxFieldLength(length: Long) = add(DatabaseOption.TransactionLoggingMaxFieldLength(length))
    fun transactionTimeout(ms: Long) = add(DatabaseOption.TransactionTimeout(ms))
    fun transactionRetryLimit(retries: Long) = add(DatabaseOption.TransactionRetryLimit(retries))
    fun transactionMaxRetryDelay(ms: Long) = add(DatabaseOption.TransactionMaxRetryDelay(ms))
    fun transactionSizeLimit(bytes: Long) = add(DatabaseOption.TransactionSizeLimit(bytes))
    fun transactionCausalReadRisky() = add(DatabaseOption.TransactionCausalReadRisky)
    fun transactionAutomaticIdempotency() = add(DatabaseOption.TransactionAutomaticIdempotency)
    fun transactionBypassUnreadable() = add(DatabaseOption.TransactionBypassUnreadable)
    fun transactionUsedDuringCommitProtectionDisable() = add(DatabaseOption.TransactionUsedDuringCommitProtectionDisable)
    fun transactionReportConflictingKeys() = add(DatabaseOption.TransactionReportConflictingKeys)
    fun useConfigDatabase() = add(DatabaseOption.UseConfigDatabase)
    fun testCausalReadRisky(value: Long) = add(DatabaseOption.TestCausalReadRisky(value))
}

inline fun databaseOptions(block: DatabaseOptionBuilder.() -> Unit): List<DatabaseOption> {
    val collected = mutableListOf<DatabaseOption>()
    DatabaseOptionBuilder(null, collected).apply(block)
    return collected.toList()
}
