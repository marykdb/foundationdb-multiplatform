package maryk.foundationdb

import maryk.foundationdb.async.CloseableAsyncIterator

object LocalityUtil {
    fun getBoundaryKeys(database: Database, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>> =
        getBoundaryKeysInternal(database, begin, end)

    fun getBoundaryKeys(transaction: Transaction, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>> =
        getBoundaryKeysInternal(transaction, begin, end)

    fun openBoundaryKeysIterator(
        database: Database,
        begin: ByteArray,
        end: ByteArray
    ): CloseableAsyncIterator<ByteArray> =
        openBoundaryKeysIteratorInternal(database, begin, end)

    fun openBoundaryKeysIterator(
        transaction: Transaction,
        begin: ByteArray,
        end: ByteArray
    ): CloseableAsyncIterator<ByteArray> =
        openBoundaryKeysIteratorInternal(transaction, begin, end)

    fun getAddressesForKey(transaction: Transaction, key: ByteArray): FdbFuture<List<String>> =
        getAddressesForKeyInternal(transaction, key)
}

internal expect fun getBoundaryKeysInternal(database: Database, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>>

internal expect fun getBoundaryKeysInternal(transaction: Transaction, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>>

internal expect fun openBoundaryKeysIteratorInternal(
    database: Database,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray>

internal expect fun openBoundaryKeysIteratorInternal(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray>

internal expect fun getAddressesForKeyInternal(transaction: Transaction, key: ByteArray): FdbFuture<List<String>>
