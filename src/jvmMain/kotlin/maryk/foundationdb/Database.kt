package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple

actual class Database internal constructor(
    internal val delegate: com.apple.foundationdb.Database
) : TransactionContext {
    actual fun options(): DatabaseOptions = DatabaseOptions(delegate.options())

    actual fun createTransaction(): Transaction =
        Transaction(delegate.createTransaction())

    @Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
    actual fun openTenant(tenantName: Tuple): Tenant =
        Tenant(delegate.openTenant(tenantName.delegate))

    @ExperimentalBlobGranules
    actual fun purgeBlobGranules(begin: ByteArray, end: ByteArray, force: Boolean): FdbFuture<ByteArray> = purgeBlobGranules(Range(begin, end), force = force)
    @ExperimentalBlobGranules
    actual fun purgeBlobGranules(begin: ByteArray, end: ByteArray, purgeVersion: Long, force: Boolean): FdbFuture<ByteArray> = purgeBlobGranules(Range(begin, end), purgeVersion, force)
    @ExperimentalBlobGranules
    actual fun blobbifyRange(begin: ByteArray, end: ByteArray): FdbFuture<Boolean> = blobbifyRange(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun blobbifyRangeBlocking(begin: ByteArray, end: ByteArray): FdbFuture<Boolean> = blobbifyRangeBlocking(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun unblobbifyRange(begin: ByteArray, end: ByteArray): FdbFuture<Boolean> = unblobbifyRange(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun listBlobbifiedRanges(begin: ByteArray, end: ByteArray, rangeLimit: Int): FdbFuture<List<Range>> = listBlobbifiedRanges(Range(begin, end), rangeLimit)
    @ExperimentalBlobGranules
    actual fun verifyBlobRange(begin: ByteArray, end: ByteArray): FdbFuture<Long> = verifyBlobRange(Range(begin, end))
    @ExperimentalBlobGranules
    actual fun verifyBlobRange(begin: ByteArray, end: ByteArray, version: Long): FdbFuture<Long> = verifyBlobRange(Range(begin, end), version)
    @ExperimentalBlobGranules
    actual fun flushBlobRange(begin: ByteArray, end: ByteArray, compact: Boolean): FdbFuture<Boolean> = flushBlobRange(Range(begin, end), compact)
    @ExperimentalBlobGranules
    actual fun flushBlobRange(begin: ByteArray, end: ByteArray, compact: Boolean, version: Long): FdbFuture<Boolean> = flushBlobRange(Range(begin, end), compact, version)

    actual fun close() {
        delegate.close()
    }

    actual fun openTenant(tenantName: ByteArray): Tenant = Tenant(delegate.openTenant(tenantName))
    actual fun getClientStatus(): FdbFuture<ByteArray> = delegate.clientStatus.toFdbFuture()
    actual fun getMainThreadBusyness(): Double = delegate.mainThreadBusyness

    @ExperimentalBlobGranules
    actual fun purgeBlobGranules(range: Range, purgeVersion: Long, force: Boolean): FdbFuture<ByteArray> = delegate.purgeBlobGranules(range.begin, range.end, purgeVersion, force).toFdbFuture()
    @ExperimentalBlobGranules
    actual fun blobbifyRange(range: Range): FdbFuture<Boolean> = delegate.blobbifyRange(range.begin, range.end).toFdbFuture()
    @ExperimentalBlobGranules
    actual fun blobbifyRangeBlocking(range: Range): FdbFuture<Boolean> = delegate.blobbifyRangeBlocking(range.begin, range.end).toFdbFuture()
    @ExperimentalBlobGranules
    actual fun unblobbifyRange(range: Range): FdbFuture<Boolean> = delegate.unblobbifyRange(range.begin, range.end).toFdbFuture()
    @ExperimentalBlobGranules
    actual fun listBlobbifiedRanges(range: Range, rangeLimit: Int): FdbFuture<List<Range>> = delegate.listBlobbifiedRanges(range.begin, range.end, rangeLimit).mapToFdbFuture { result -> result.keyRanges.map { Range(it.begin.copyOf(), it.end.copyOf()) } }
    @ExperimentalBlobGranules
    actual fun verifyBlobRange(range: Range, version: Long): FdbFuture<Long> = delegate.verifyBlobRange(range.begin, range.end, version).toFdbFuture()
    @ExperimentalBlobGranules
    actual fun flushBlobRange(range: Range, compact: Boolean, version: Long): FdbFuture<Boolean> = delegate.flushBlobRange(range.begin, range.end, compact, version).toFdbFuture()
    @ExperimentalBlobGranules
    actual fun waitPurgeGranulesComplete(purgeKey: ByteArray): FdbFuture<Unit> = delegate.waitPurgeGranulesComplete(purgeKey).mapToFdbFuture { }

    actual override fun <T> run(block: (Transaction) -> T): T =
        delegate.run { txn -> block(Transaction(txn)) }

    actual override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.runAsync { txn -> block(Transaction(txn)).asCompletableFuture() }.toFdbFuture()

    actual override fun <T> read(block: (ReadTransaction) -> T): T =
        delegate.read { rt -> block(ReadTransaction(rt)) }

    actual override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> =
        delegate.readAsync { rt -> block(ReadTransaction(rt)).asCompletableFuture() }.toFdbFuture()
}
