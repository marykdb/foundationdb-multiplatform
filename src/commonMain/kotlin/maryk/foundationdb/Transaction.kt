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
    fun getEstimatedRangeSizeBytes(range: Range): FdbFuture<Long>
    fun commit(): FdbFuture<Unit>
    fun onError(error: FDBException): FdbFuture<Unit>
    fun watch(key: ByteArray): FdbFuture<Unit>
    fun close()
}

/**
 * Subset of mutation types supported directly via [Transaction.mutate].
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
    APPEND_IF_FITS
}
