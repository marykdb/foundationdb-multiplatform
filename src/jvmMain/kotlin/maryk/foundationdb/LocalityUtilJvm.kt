package maryk.foundationdb

import maryk.foundationdb.async.CloseableAsyncIterator
import com.apple.foundationdb.LocalityUtil as JavaLocalityUtil
import maryk.foundationdb.async.AsyncUtil

internal actual fun getBoundaryKeysInternal(database: Database, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>> {
    return AsyncUtil.collectRemaining(openBoundaryKeysIteratorInternal(database, begin, end))
}

internal actual fun getBoundaryKeysInternal(transaction: Transaction, begin: ByteArray, end: ByteArray): FdbFuture<List<ByteArray>> {
    return AsyncUtil.collectRemaining(openBoundaryKeysIteratorInternal(transaction, begin, end))
}

internal actual fun getAddressesForKeyInternal(transaction: Transaction, key: ByteArray): FdbFuture<List<String>> =
    JavaLocalityUtil.getAddressesForKey(transaction.delegate, key)
        .mapToFdbFuture { list -> list.toList() }

internal actual fun openBoundaryKeysIteratorInternal(
    database: Database,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray> =
    CloseableAsyncIterator(
        JavaLocalityUtil.getBoundaryKeys(database.delegate, begin, end),
        { it as ByteArray }
    )

internal actual fun openBoundaryKeysIteratorInternal(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray> =
    CloseableAsyncIterator(
        JavaLocalityUtil.getBoundaryKeys(transaction.delegate, begin, end),
        { it as ByteArray }
    )
