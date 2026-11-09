package maryk.foundationdb

@Deprecated("Tenants were removed in FoundationDB 8.x; this API will be removed in a future release.")
expect class Tenant : TransactionContext {
    override fun <T> run(block: (Transaction) -> T): T
    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T>
    override fun <T> read(block: (ReadTransaction) -> T): T
    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T>
    fun createTransaction(): Transaction
    fun getName(): ByteArray
    fun getId(): FdbFuture<Long>
    @ExperimentalBlobGranules
    fun purgeBlobGranules(range: Range, purgeVersion: Long = -2, force: Boolean = false): FdbFuture<ByteArray>
    @ExperimentalBlobGranules
    fun blobbifyRange(range: Range): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun blobbifyRangeBlocking(range: Range): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun unblobbifyRange(range: Range): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun listBlobbifiedRanges(range: Range, rangeLimit: Int): FdbFuture<List<Range>>
    @ExperimentalBlobGranules
    fun verifyBlobRange(range: Range, version: Long = -2): FdbFuture<Long>
    @ExperimentalBlobGranules
    fun flushBlobRange(range: Range, compact: Boolean = false, version: Long = -2): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun waitPurgeGranulesComplete(purgeKey: ByteArray): FdbFuture<Unit>
    @ExperimentalBlobGranules
    fun purgeBlobGranules(begin: ByteArray, end: ByteArray, force: Boolean): FdbFuture<ByteArray>
    @ExperimentalBlobGranules
    fun purgeBlobGranules(begin: ByteArray, end: ByteArray, purgeVersion: Long, force: Boolean): FdbFuture<ByteArray>
    @ExperimentalBlobGranules
    fun blobbifyRange(begin: ByteArray, end: ByteArray): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun blobbifyRangeBlocking(begin: ByteArray, end: ByteArray): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun unblobbifyRange(begin: ByteArray, end: ByteArray): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun listBlobbifiedRanges(begin: ByteArray, end: ByteArray, rangeLimit: Int): FdbFuture<List<Range>>
    @ExperimentalBlobGranules
    fun verifyBlobRange(begin: ByteArray, end: ByteArray): FdbFuture<Long>
    @ExperimentalBlobGranules
    fun verifyBlobRange(begin: ByteArray, end: ByteArray, version: Long): FdbFuture<Long>
    @ExperimentalBlobGranules
    fun flushBlobRange(begin: ByteArray, end: ByteArray, compact: Boolean): FdbFuture<Boolean>
    @ExperimentalBlobGranules
    fun flushBlobRange(begin: ByteArray, end: ByteArray, compact: Boolean, version: Long): FdbFuture<Boolean>

    fun close()
}
