@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

import foundationdb.c.fdb_transaction_get_committed_version
import foundationdb.c.fdb_transaction_get_versionstamp
import foundationdb.c.fdb_future_get_key
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.memScoped

import foundationdb.c.FDBTransaction
import foundationdb.c.FDBKey
import foundationdb.c.fdb_future_get_key_array
import foundationdb.c.fdb_transaction_cancel
import foundationdb.c.fdb_transaction_get_approximate_size
import foundationdb.c.fdb_transaction_get_range_split_points
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.readBytes
import foundationdb.c.fdb_future_get_int64
import foundationdb.c.fdb_transaction_add_conflict_range
import foundationdb.c.fdb_transaction_atomic_op
import foundationdb.c.fdb_transaction_commit
import foundationdb.c.fdb_transaction_get_estimated_range_size_bytes
import foundationdb.c.fdb_transaction_on_error
import foundationdb.c.fdb_transaction_watch
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVarOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlin.concurrent.atomics.ExperimentalAtomicApi

actual class Transaction internal constructor(pointer: CPointer<FDBTransaction>, private val ownerDatabase: Database) : ReadTransaction(pointer, snapshot = false), TransactionContext {
    actual override fun options(): TransactionOptions = TransactionOptions(handle)

    actual fun getDatabase(): Database = ownerDatabase

    actual fun set(key: ByteArray, value: ByteArray) {
        handle.usePointer { pointer ->
            key.withPointer { keyPtr, keyLen ->
                value.withPointer { valuePtr, valueLen ->
                    foundationdb.c.fdb_transaction_set(pointer, keyPtr, keyLen, valuePtr, valueLen)
                }
            }
        }
    }

    actual fun clear(key: ByteArray) {
        handle.usePointer { pointer ->
            key.withPointer { keyPtr, keyLen ->
                foundationdb.c.fdb_transaction_clear(pointer, keyPtr, keyLen)
            }
        }
    }

    actual fun clear(beginKey: ByteArray, endKey: ByteArray) {
        handle.usePointer { pointer ->
            beginKey.withPointer { beginPtr, beginLen ->
                endKey.withPointer { endPtr, endLen ->
                    foundationdb.c.fdb_transaction_clear_range(pointer, beginPtr, beginLen, endPtr, endLen)
                }
            }
        }
    }

    actual fun clear(range: Range) = clear(range.begin, range.end)

    actual fun addReadConflictRange(beginKey: ByteArray, endKey: ByteArray) =
        addConflictRange(beginKey, endKey, ConflictRangeType.READ)

    actual fun addReadConflictKey(key: ByteArray) =
        addConflictRange(key, ByteArrayUtil.keyAfter(key), ConflictRangeType.READ)

    actual fun addWriteConflictRange(beginKey: ByteArray, endKey: ByteArray) =
        addConflictRange(beginKey, endKey, ConflictRangeType.WRITE)

    actual fun addWriteConflictKey(key: ByteArray) =
        addConflictRange(key, ByteArrayUtil.keyAfter(key), ConflictRangeType.WRITE)

    private fun addConflictRange(beginKey: ByteArray, endKey: ByteArray, type: ConflictRangeType) {
        handle.usePointer { pointer ->
            beginKey.withPointer { beginPtr, beginLen ->
                endKey.withPointer { endPtr, endLen ->
                    checkError(
                        fdb_transaction_add_conflict_range(
                            pointer,
                            beginPtr,
                            beginLen,
                            endPtr,
                            endLen,
                            type.toNative()
                        )
                    )
                }
            }
        }
    }

    actual fun atomicAdd(key: ByteArray, operand: ByteArray) {
        mutate(MutationType.ADD, key, operand)
    }

    actual fun mutate(type: MutationType, key: ByteArray, value: ByteArray) {
        handle.usePointer { pointer ->
            key.withPointer { keyPtr, keyLen ->
                value.withPointer { valuePtr, valueLen ->
                    fdb_transaction_atomic_op(pointer, keyPtr, keyLen, valuePtr, valueLen, type.toNative())
                }
            }
        }
    }

    actual override fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long> {
        val future = handle.usePointerForFuture { pointer ->
            range.begin.withPointer { beginPtr, beginLen ->
                range.end.withPointer { endPtr, endLen ->
                    fdb_transaction_get_estimated_range_size_bytes(pointer, beginPtr, beginLen, endPtr, endLen)
                }
            }
        } ?: error("fdb_transaction_get_estimated_range_size_bytes returned null future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { fut ->
            val out = alloc<LongVarOf<Long>>()
            checkError(fdb_future_get_int64(fut, out.ptr.reinterpret<LongVarOf<Long>>()))
            out.value
        }
    }

    actual fun getApproximateSize(): FdbFuture<Long> {
        val future = handle.usePointerForFuture { fdb_transaction_get_approximate_size(it) }
            ?: error("Missing approximate-size future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { native ->
            val size = alloc<LongVarOf<Long>>()
            checkError(fdb_future_get_int64(native, size.ptr))
            size.value
        }
    }

    actual override fun getRangeSplitPoints(range: Range, chunkSize: Long): FdbFuture<List<ByteArray>> {
        require(chunkSize > 0)
        val future = handle.usePointerForFuture { transaction ->
            range.begin.withPointer { begin, beginSize ->
                range.end.withPointer { end, endSize ->
                    fdb_transaction_get_range_split_points(transaction, begin, beginSize, end, endSize, chunkSize)
                }
            }
        } ?: error("Missing split-point future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { native ->
            val keys = allocPointerTo<FDBKey>()
            val count = alloc<IntVar>()
            checkError(fdb_future_get_key_array(native, keys.ptr, count.ptr))
            List(count.value) { index ->
                val key = keys.value!![index]
                key.key!!.readBytes(key.key_length)
            }
        }
    }

    actual fun cancel() = handle.usePointer { fdb_transaction_cancel(it) }

    actual fun commit(): FdbFuture<Unit> {
        val future = handle.usePointerForFuture(cancelOnClose = false) { pointer ->
            fdb_transaction_commit(pointer)
        } ?: error("fdb_transaction_commit returned null future")
        return NativeFuture(future, onCleanup = { handle.releaseFutureUse(cancelOnClose = false) }) { }
    }

    actual fun onError(error: Throwable): FdbFuture<Transaction> =
        if (error is FDBException) fdbFutureFromSuspend { onError(error).await(); this } else failedFdbFuture(error)

    actual fun onError(error: FDBException): FdbFuture<Unit> {
        val future = handle.usePointerForFuture { pointer ->
            fdb_transaction_on_error(pointer, error.getCode())
        } ?: error("fdb_transaction_on_error returned null future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { }
    }

    actual fun watch(key: ByteArray): FdbFuture<Unit> {
        val future = handle.usePointerForFuture { pointer ->
            key.withPointer { keyPtr, keyLen ->
                fdb_transaction_watch(pointer, keyPtr, keyLen)
            }
        } ?: error("fdb_transaction_watch returned null future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { }
    }

    actual fun close() {
        handle.close()
    }

    actual fun clearRangeStartsWith(prefix: ByteArray) = clear(Range.startsWith(prefix))
    actual fun getCommittedVersion(): Long = memScoped {
        val version = alloc<LongVarOf<Long>>()
        handle.usePointer { checkError(fdb_transaction_get_committed_version(it, version.ptr)) }
        version.value
    }
    actual fun getVersionstamp(): FdbFuture<ByteArray> {
        val future = handle.usePointerForFuture { fdb_transaction_get_versionstamp(it) }
            ?: error("Missing versionstamp future")
        return NativeFuture(future, onCleanup = handle::releaseFutureUse) { native ->
            val key = allocPointerTo<UByteVar>()
            val size = alloc<IntVar>()
            checkError(fdb_future_get_key(native, key.ptr, size.ptr))
            if (size.value == 0) byteArrayOf() else key.value!!.readBytes(size.value)
        }
    }

    actual override fun <T> run(block: (Transaction) -> T): T =
        executeBlocking(needsCommit = true) { block(this) }

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        executeAsync(needsCommit = true) { block(this).await() }

    actual override fun <T> read(block: (ReadTransaction) -> T): T =
        executeBlocking(needsCommit = false) { block(this) }

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        executeAsync(needsCommit = false) { block(this).await() }

    private fun <T> executeBlocking(needsCommit: Boolean, block: () -> T): T {
        while (true) {
            try {
                val result = block()
                if (needsCommit) {
                    commit().awaitBlocking()
                }
                return result
            } catch (error: Throwable) {
                if (!handleOnErrorBlocking(error)) throw error
            }
        }
    }

    private fun <T> executeAsync(needsCommit: Boolean, block: suspend () -> T): FdbFuture<T> =
        fdbFutureFromSuspend {
            while (true) {
                try {
                    val result = block()
                    if (needsCommit) {
                        commit().await()
                    }
                    return@fdbFutureFromSuspend result
                } catch (error: Throwable) {
                    if (!handleOnErrorSuspending(error)) throw error
                }
            }
            error("unreachable")
        }

    private fun handleOnErrorBlocking(error: Throwable): Boolean {
        if (error !is FDBException) return false
        onError(error).awaitBlocking()
        return true
    }

    private suspend fun handleOnErrorSuspending(error: Throwable): Boolean {
        if (error !is FDBException) return false
        onError(error).await()
        return true
    }
}
