@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.CloseableAsyncIterator

import foundationdb.c.fdb_future_get_string_array
import foundationdb.c.fdb_transaction_get_addresses_for_key
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import maryk.foundationdb.ReadTransaction.Companion.ROW_LIMIT_UNLIMITED

private val KEY_SERVERS_PREFIX: ByteArray =
    byteArrayOf(0xFF.toByte()) + "/keyServers/".encodeToByteArray()

internal actual fun getBoundaryKeysInternal(
    database: Database,
    begin: ByteArray,
    end: ByteArray
): FdbFuture<List<ByteArray>> = fdbFutureFromSuspend {
    val transaction = database.createTransaction()
    try {
        transaction.fetchBoundaryKeys(begin, end)
    } finally {
        transaction.close()
    }
}

internal actual fun getBoundaryKeysInternal(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray
): FdbFuture<List<ByteArray>> = fdbFutureFromSuspend {
    transaction.fetchBoundaryKeys(begin, end)
}

internal actual fun getAddressesForKeyInternal(
    transaction: Transaction,
    key: ByteArray
): FdbFuture<List<String>> {
    val future = key.withPointer { keyPtr, keyLen ->
        fdb_transaction_get_addresses_for_key(transaction.pointer, keyPtr, keyLen)
    } ?: error("fdb_transaction_get_addresses_for_key returned null future")
    return NativeFuture(future) { fut ->
        memScoped {
            val outStrings = alloc<CPointerVarOf<CPointer<CPointerVarOf<CPointer<ByteVar>>>>>()
            val outCount = alloc<IntVar>()
            checkError(fdb_future_get_string_array(fut, outStrings.ptr, outCount.ptr))
            val pointer = outStrings.value
            val results = mutableListOf<String>()
            if (pointer != null) {
                for (idx in 0 until outCount.value) {
                    pointer[idx]?.toKString()?.let(results::add)
                }
            }
            results
        }
    }
}

private suspend fun Transaction.fetchBoundaryKeys(begin: ByteArray, end: ByteArray): List<ByteArray> {
    options().setReadSystemKeys()
    options().setLockAware()

    val beginSelector = KeySelector.firstGreaterOrEqual(keyServersKey(begin))
    val endSelector = KeySelector.firstGreaterOrEqual(keyServersKey(end))

    val range = collectRangeInternal(
        beginSelector,
        endSelector,
        ROW_LIMIT_UNLIMITED,
        reverse = false,
        streamingMode = StreamingMode.WANT_ALL
    ).await()

    val prefixLength = KEY_SERVERS_PREFIX.size
    return range.values.map { kv -> kv.key.copyOfRange(prefixLength, kv.key.size) }
}

private fun keyServersKey(key: ByteArray): ByteArray {
    val result = ByteArray(KEY_SERVERS_PREFIX.size + key.size)
    KEY_SERVERS_PREFIX.copyInto(result, 0)
    key.copyInto(result, KEY_SERVERS_PREFIX.size)
    return result
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(size + other.size)
    copyInto(result, 0)
    other.copyInto(result, size)
    return result
}

internal actual fun openBoundaryKeysIteratorInternal(
    database: Database,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray> {
    val transaction = database.createTransaction()
    return try {
        boundaryKeyIterator(transaction, begin, end) {
            transaction.close()
        }
    } catch (t: Throwable) {
        transaction.close()
        throw t
    }
}

internal actual fun openBoundaryKeysIteratorInternal(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray> =
    boundaryKeyIterator(transaction, begin, end) {}

private fun boundaryKeyIterator(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray,
    closeAction: () -> Unit
): CloseableAsyncIterator<ByteArray> {
    transaction.options().setReadSystemKeys()
    transaction.options().setLockAware()

    val beginSelector = KeySelector.firstGreaterOrEqual(keyServersKey(begin))
    val endSelector = KeySelector.firstGreaterOrEqual(keyServersKey(end))
    val iterable = transaction.getRange(
        beginSelector,
        endSelector,
        ReadTransaction.ROW_LIMIT_UNLIMITED,
        reverse = false,
        streamingMode = StreamingMode.WANT_ALL
    )
    val prefixLength = KEY_SERVERS_PREFIX.size
    val kvIterator = iterable.iterator()

    val delegate = object : AsyncIterator<ByteArray>() {
        override fun hasNext(): Boolean = kvIterator.hasNext()

        override suspend fun next(): ByteArray {
            val kv = kvIterator.next()
            return kv.key.copyOfRange(prefixLength, kv.key.size)
        }

        override fun onHasNext() = kvIterator.onHasNext()

        override fun cancel() {
            kvIterator.cancel()
        }
    }

    return CloseableAsyncIterator(delegate, closeAction)
}
