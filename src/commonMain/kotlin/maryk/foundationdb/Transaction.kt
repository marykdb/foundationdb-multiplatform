package maryk.foundationdb

/**
 * Mutable FoundationDB transaction.
 */
expect class Transaction : ReadTransaction, TransactionContext {
    override fun <T> run(block: (Transaction) -> T): T
    override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T>
    override fun <T> read(block: (ReadTransaction) -> T): T
    override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T>
    override fun options(): TransactionOptions
    /** Originating connection; closing the returned database closes that shared connection. */
    fun getDatabase(): Database
    fun set(key: ByteArray, value: ByteArray)
    fun clear(key: ByteArray)
    fun clear(beginKey: ByteArray, endKey: ByteArray)
    fun clear(range: Range)
    fun addReadConflictRange(beginKey: ByteArray, endKey: ByteArray)
    fun addReadConflictKey(key: ByteArray)
    fun addWriteConflictRange(beginKey: ByteArray, endKey: ByteArray)
    fun addWriteConflictKey(key: ByteArray)
    fun atomicAdd(key: ByteArray, operand: ByteArray)
    fun mutate(type: MutationType, key: ByteArray, value: ByteArray)
    override fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long>
    /** Native estimate includes mutations and conflict ranges; not a hard admission guarantee. */
    fun getApproximateSize(): FdbFuture<Long>
    /** Split points include both range boundaries and need not align with logical records. */
    override fun getRangeSplitPoints(range: Range, chunkSize: Long): FdbFuture<List<ByteArray>>
    /** Cancel pending operations without releasing the transaction's handle. */
    fun cancel()
    fun clearRangeStartsWith(prefix: ByteArray)
    fun getCommittedVersion(): Long
    fun getVersionstamp(): FdbFuture<ByteArray>
    fun commit(): FdbFuture<Unit>
    /** Reset after a retryable failure and return this portable transaction, retaining the Java replacement. */
    fun onError(error: Throwable): FdbFuture<Transaction>
    /** Compatibility overload; resets this transaction and returns Unit. */
    fun onError(error: FDBException): FdbFuture<Unit>
    fun watch(key: ByteArray): FdbFuture<Unit>
    fun close()
}

/**
 * Mutation types supported directly via [Transaction.mutate].
 */
enum class MutationType {
    ADD,
    AND,
    OR,
    XOR,
    MAX,
    MIN,
    BYTE_MIN,
    BYTE_MAX,
    SET_VERSIONSTAMPED_KEY,
    SET_VERSIONSTAMPED_VALUE,
    APPEND_IF_FITS,
    COMPARE_AND_CLEAR,
    BIT_AND,
    BIT_OR,
    BIT_XOR;

    fun code(): Int = when (this) {
        ADD -> 2
        AND, BIT_AND -> 6
        OR, BIT_OR -> 7
        XOR, BIT_XOR -> 8
        APPEND_IF_FITS -> 9
        MAX -> 12
        MIN -> 13
        SET_VERSIONSTAMPED_KEY -> 14
        SET_VERSIONSTAMPED_VALUE -> 15
        BYTE_MIN -> 16
        BYTE_MAX -> 17
        COMPARE_AND_CLEAR -> 20
    }
}
