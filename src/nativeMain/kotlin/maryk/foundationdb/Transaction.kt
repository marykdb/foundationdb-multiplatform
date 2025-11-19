@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBTransaction
import foundationdb.c.fdb_future_get_int64
import foundationdb.c.fdb_transaction_add_conflict_range
import foundationdb.c.fdb_transaction_atomic_op
import foundationdb.c.fdb_transaction_commit
import foundationdb.c.fdb_transaction_destroy
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

actual class Transaction internal constructor(pointer: CPointer<FDBTransaction>) : ReadTransaction(pointer, snapshot = false), TransactionContext {
    actual override fun options(): TransactionOptions = TransactionOptions(pointer)

    actual fun set(key: ByteArray, value: ByteArray) {
        key.withPointer { keyPtr, keyLen ->
            value.withPointer { valuePtr, valueLen ->
                foundationdb.c.fdb_transaction_set(pointer, keyPtr, keyLen, valuePtr, valueLen)
            }
        }
    }

    actual fun clear(key: ByteArray) {
        key.withPointer { keyPtr, keyLen ->
            foundationdb.c.fdb_transaction_clear(pointer, keyPtr, keyLen)
        }
    }

    actual fun clear(beginKey: ByteArray, endKey: ByteArray) {
        beginKey.withPointer { beginPtr, beginLen ->
            endKey.withPointer { endPtr, endLen ->
                foundationdb.c.fdb_transaction_clear_range(pointer, beginPtr, beginLen, endPtr, endLen)
            }
        }
    }

    actual fun clear(range: Range) = clear(range.begin, range.end)

    actual fun addReadConflictRange(beginKey: ByteArray, endKey: ByteArray) =
        addConflictRange(beginKey, endKey, ConflictRangeType.READ)

    actual fun addReadConflictKey(key: ByteArray) =
        addConflictRange(key, key.nextKey(), ConflictRangeType.READ)

    actual fun addWriteConflictRange(beginKey: ByteArray, endKey: ByteArray) =
        addConflictRange(beginKey, endKey, ConflictRangeType.WRITE)

    actual fun addWriteConflictKey(key: ByteArray) =
        addConflictRange(key, key.nextKey(), ConflictRangeType.WRITE)

    private fun addConflictRange(beginKey: ByteArray, endKey: ByteArray, type: ConflictRangeType) {
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

    actual fun atomicAdd(key: ByteArray, operand: ByteArray) {
        mutate(MutationType.ADD, key, operand)
    }

    actual fun mutate(type: MutationType, key: ByteArray, value: ByteArray) {
        key.withPointer { keyPtr, keyLen ->
            value.withPointer { valuePtr, valueLen ->
                fdb_transaction_atomic_op(pointer, keyPtr, keyLen, valuePtr, valueLen, type.toNative())
            }
        }
    }

    actual fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long> {
        val future = range.begin.withPointer { beginPtr, beginLen ->
            range.end.withPointer { endPtr, endLen ->
                fdb_transaction_get_estimated_range_size_bytes(pointer, beginPtr, beginLen, endPtr, endLen)
            }
        } ?: error("fdb_transaction_get_estimated_range_size_bytes returned null future")
        return NativeFuture(future) { fut ->
        val out = alloc<LongVarOf<Long>>()
            checkError(fdb_future_get_int64(fut, out.ptr.reinterpret<LongVarOf<Long>>()))
            out.value
        }
    }

    actual fun commit(): FdbFuture<Unit> {
        val future = fdb_transaction_commit(pointer) ?: error("fdb_transaction_commit returned null future")
        return NativeFuture(future) { }
    }

    actual fun onError(error: FDBException): FdbFuture<Unit> {
        val future = fdb_transaction_on_error(pointer, error.getCode()) ?: error("fdb_transaction_on_error returned null future")
        return NativeFuture(future) { }
    }

    actual fun watch(key: ByteArray): FdbFuture<Unit> {
        val future = key.withPointer { keyPtr, keyLen ->
            fdb_transaction_watch(pointer, keyPtr, keyLen)
        } ?: error("fdb_transaction_watch returned null future")
        return NativeFuture(future) { }
    }

    actual fun close() {
        fdb_transaction_destroy(pointer)
    }

    actual override fun <T> run(block: (Transaction) -> T): T = block(this)

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> = block(this)

    actual override fun <T> read(block: (ReadTransaction) -> T): T = block(this)

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> = block(this)
}
