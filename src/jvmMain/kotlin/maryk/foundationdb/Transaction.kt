package maryk.foundationdb

import com.apple.foundationdb.MutationType as JavaMutationType
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

actual class Transaction internal constructor(
    override var delegate: com.apple.foundationdb.Transaction
) : ReadTransaction(delegate), TransactionContext {
    private val ownershipLock = Any()
    private var closed = false
    private var retryInFlight = false

    actual override fun options(): TransactionOptions = TransactionOptions(delegate.options())

    actual fun getDatabase(): Database = Database(delegate.database)

    actual fun set(key: ByteArray, value: ByteArray) {
        delegate.set(key, value)
    }

    actual fun clear(key: ByteArray) {
        delegate.clear(key)
    }

    actual fun clear(beginKey: ByteArray, endKey: ByteArray) {
        delegate.clear(beginKey, endKey)
    }

    actual fun clear(range: Range) {
        delegate.clear(range.delegate)
    }

    actual fun addReadConflictRange(beginKey: ByteArray, endKey: ByteArray) {
        delegate.addReadConflictRange(beginKey, endKey)
    }

    actual fun addReadConflictKey(key: ByteArray) {
        delegate.addReadConflictKey(key)
    }

    actual fun addWriteConflictRange(beginKey: ByteArray, endKey: ByteArray) {
        delegate.addWriteConflictRange(beginKey, endKey)
    }

    actual fun addWriteConflictKey(key: ByteArray) {
        delegate.addWriteConflictKey(key)
    }

    actual fun atomicAdd(key: ByteArray, operand: ByteArray) {
        delegate.mutate(JavaMutationType.ADD, key, operand)
    }

    actual fun mutate(type: MutationType, key: ByteArray, value: ByteArray) {
        delegate.mutate(type.toJava(), key, value)
    }

    actual override fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long> =
        delegate.getEstimatedRangeSizeBytes(range.delegate).toFdbFuture()

    actual fun getApproximateSize(): FdbFuture<Long> = delegate.approximateSize.toFdbFuture()

    actual override fun getRangeSplitPoints(range: Range, chunkSize: Long): FdbFuture<List<ByteArray>> {
        require(chunkSize > 0)
        return delegate.getRangeSplitPoints(range.begin, range.end, chunkSize).mapToFdbFuture { result ->
            result.keys.map { it.copyOf() }
        }
    }

    actual fun cancel() = synchronized(ownershipLock) {
        if (!closed) {
            if (retryInFlight) close() else delegate.cancel()
        }
    }

    actual fun commit(): FdbFuture<Unit> =
        delegate.commit().mapToFdbFuture { }

    actual fun onError(error: Throwable): FdbFuture<Transaction> = recover(error)

    actual fun onError(error: FDBException): FdbFuture<Unit> = recover(error).asCompletableFuture().mapToFdbFuture { }

    private fun recover(error: Throwable): FdbFuture<Transaction> {
        val source = synchronized(ownershipLock) {
            check(!closed) { "Transaction is closed" }
            check(!retryInFlight) { "Transaction retry is already in flight" }
            delegate.onError(error).also { retryInFlight = true }
        }
        val result = CompletableFuture<Transaction>()
        result.whenComplete { _, _ -> if (result.isCancelled) close() }
        // This observer owns the transferred SDK transaction. Never cancel the observer:
        // even a late replacement must be installed or explicitly closed.
        source.whenComplete { replacement, failure ->
            val discardReplacement = synchronized(ownershipLock) {
                retryInFlight = false
                if (failure == null && !closed && !result.isCancelled) {
                    delegate = replacement
                    false
                } else true
            }
            // Completing a future can synchronously run application callbacks.
            // Do not invoke them while holding the transaction ownership monitor.
            if (failure != null) {
                result.completeExceptionally(failure)
            } else if (discardReplacement) {
                replacement.close()
                result.completeExceptionally(CancellationException("Transaction closed during retry"))
            } else {
                result.complete(this)
            }
        }
        return result.toFdbFuture()
    }

    actual fun watch(key: ByteArray): FdbFuture<Unit> =
        delegate.watch(key).mapToFdbFuture { }

    actual fun close() = synchronized(ownershipLock) {
        if (!closed) {
            closed = true
            delegate.close()
        }
    }

    actual fun clearRangeStartsWith(prefix: ByteArray) = delegate.clearRangeStartsWith(prefix)
    actual fun getCommittedVersion(): Long = delegate.committedVersion
    actual fun getVersionstamp(): FdbFuture<ByteArray> = delegate.versionstamp.toFdbFuture()

    actual override fun <T> run(block: (Transaction) -> T): T {
        while (true) {
            try {
                val result = block(this)
                delegate.commit().join()
                return result
            } catch (t: Throwable) {
                val unwrapped = t.unwrapCompletionLike()
                if (unwrapped is FDBException) {
                    onError(unwrapped).asCompletableFuture().join()
                } else {
                    throw unwrapped
                }
            }
        }
    }

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        fdbFutureFromSuspend {
            while (true) {
                try {
                    val result = block(this@Transaction).await()
                    commit().await()
                    return@fdbFutureFromSuspend result
                } catch (t: Throwable) {
                    if (t is FDBException) {
                        onError(t).await()
                    } else {
                        throw t
                    }
                }
            }
            @Suppress("KotlinUnreachableCode")
            error("unreachable")
        }

    actual override fun <T> read(block: (ReadTransaction) -> T): T =
        delegate.read { rt -> block(ReadTransaction(rt)) }

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.readAsync { rt -> block(ReadTransaction(rt)).asCompletableFuture() }.toFdbFuture()

    private fun MutationType.toJava(): JavaMutationType = when (this) {
        MutationType.ADD -> JavaMutationType.ADD
        MutationType.BIT_AND, MutationType.AND -> JavaMutationType.BIT_AND
        MutationType.BIT_OR, MutationType.OR -> JavaMutationType.BIT_OR
        MutationType.BIT_XOR, MutationType.XOR -> JavaMutationType.BIT_XOR
        MutationType.MAX -> JavaMutationType.MAX
        MutationType.MIN -> JavaMutationType.MIN
        MutationType.BYTE_MIN -> JavaMutationType.BYTE_MIN
        MutationType.BYTE_MAX -> JavaMutationType.BYTE_MAX
        MutationType.SET_VERSIONSTAMPED_KEY -> JavaMutationType.SET_VERSIONSTAMPED_KEY
        MutationType.SET_VERSIONSTAMPED_VALUE -> JavaMutationType.SET_VERSIONSTAMPED_VALUE
        MutationType.COMPARE_AND_CLEAR -> JavaMutationType.COMPARE_AND_CLEAR
        MutationType.APPEND_IF_FITS -> JavaMutationType.APPEND_IF_FITS
    }
}

internal fun Throwable.unwrapCompletionLike(): Throwable = when (this) {
    is CompletionException -> this.cause ?: this
    is ExecutionException -> this.cause ?: this
    else -> this
}
