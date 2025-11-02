package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.asCompletableFuture
import maryk.foundationdb.toFdbFuture

actual class Transaction internal constructor(
    internal override val delegate: com.apple.foundationdb.Transaction
) : ReadTransaction(delegate), TransactionContext {
    actual override fun options(): TransactionOptions = TransactionOptions(delegate.options())

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
        delegate.mutate(com.apple.foundationdb.MutationType.ADD, key, operand)
    }

    actual fun mutate(type: MutationType, key: ByteArray, value: ByteArray) {
        delegate.mutate(type.toJava(), key, value)
    }

    actual fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long> =
        delegate.getEstimatedRangeSizeBytes(range.delegate).toFdbFuture()

    actual fun commit(): FdbFuture<Unit> =
        delegate.commit().thenApply { Unit }.toFdbFuture()

    actual fun onError(error: FDBException): FdbFuture<Unit> =
        delegate.onError(error).thenApply { Unit }.toFdbFuture()

    actual fun close() {
        delegate.close()
    }

    override fun <T> run(block: (Transaction) -> T): T =
        delegate.run { txn -> block(Transaction(txn)) }

    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.runAsync { txn -> block(Transaction(txn)).asCompletableFuture() }.toFdbFuture()

    override fun <T> read(block: (ReadTransaction) -> T): T =
        delegate.read { rt -> block(ReadTransaction(rt)) }

    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.readAsync { rt -> block(ReadTransaction(rt)).asCompletableFuture() }.toFdbFuture()

    private fun MutationType.toJava(): com.apple.foundationdb.MutationType = when (this) {
        MutationType.ADD -> com.apple.foundationdb.MutationType.ADD
        MutationType.AND -> com.apple.foundationdb.MutationType.BIT_AND
        MutationType.OR -> com.apple.foundationdb.MutationType.BIT_OR
        MutationType.XOR -> com.apple.foundationdb.MutationType.BIT_XOR
        MutationType.MAX -> com.apple.foundationdb.MutationType.MAX
        MutationType.MIN -> com.apple.foundationdb.MutationType.MIN
        MutationType.BYTE_MIN -> com.apple.foundationdb.MutationType.BYTE_MIN
        MutationType.BYTE_MAX -> com.apple.foundationdb.MutationType.BYTE_MAX
        MutationType.SET_VERSIONSTAMPED_KEY -> com.apple.foundationdb.MutationType.SET_VERSIONSTAMPED_KEY
        MutationType.SET_VERSIONSTAMPED_VALUE -> com.apple.foundationdb.MutationType.SET_VERSIONSTAMPED_VALUE
        MutationType.APPEND_IF_FITS -> com.apple.foundationdb.MutationType.APPEND_IF_FITS
    }
}
