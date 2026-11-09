@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import maryk.foundationdb.async.AsyncUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

private val KEY_SERVERS_PREFIX: ByteArray =
    byteArrayOf(0xFF.toByte()) + "/keyServers/".encodeToByteArray()

internal actual fun getBoundaryKeysInternal(
    database: Database,
    begin: ByteArray,
    end: ByteArray
): FdbFuture<List<ByteArray>> = AsyncUtil.collectRemaining(openBoundaryKeysIteratorInternal(database, begin, end))

internal actual fun getBoundaryKeysInternal(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray
): FdbFuture<List<ByteArray>> = AsyncUtil.collectRemaining(openBoundaryKeysIteratorInternal(transaction, begin, end))

internal actual fun getAddressesForKeyInternal(
    transaction: Transaction,
    key: ByteArray
): FdbFuture<List<String>> {
    val future = transaction.handle.usePointerForFuture { pointer ->
        key.withPointer { keyPtr, keyLen ->
            fdb_transaction_get_addresses_for_key(pointer, keyPtr, keyLen)
        }
    } ?: error("fdb_transaction_get_addresses_for_key returned null future")
    return NativeFuture(future, onCleanup = transaction.handle::releaseFutureUse) { fut ->
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
        boundaryKeyIterator(transaction, begin, end, closeAction = transaction::close)
    } catch (t: Throwable) {
        transaction.close()
        throw t
    }
}

internal actual fun openBoundaryKeysIteratorInternal(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray
): CloseableAsyncIterator<ByteArray> {
    // Like Java, locality iteration owns a separate transaction. It neither changes
    // the caller's options nor depends on the caller remaining open.
    val owned = transaction.getDatabase().createTransaction()
    return try {
        val readVersion = transaction.getReadVersion()
        if (readVersion.isDone && !readVersion.isCancelled) {
            runCatching { readVersion.awaitBlocking() }.getOrNull()?.let(owned::setReadVersion)
        }
        boundaryKeyIterator(owned, begin, end, owned::close)
    } catch (error: Throwable) {
        owned.close()
        throw error
    }
}

internal fun boundaryKeyIterator(
    transaction: Transaction,
    begin: ByteArray,
    end: ByteArray,
    closeAction: () -> Unit,
    fetchPage: ((KeySelector, KeySelector, Int) -> FdbFuture<RangePage>)? = null
): CloseableAsyncIterator<ByteArray> {
    // Reuse the range engine's locked state, coalescing and cancellation rather
    // than introduce another mutable cursor shared with cancellation callbacks.
    val cursor = StreamingRangeIterator<KeyValue>(
        KeySelector.firstGreaterOrEqual(keyServersKey(begin)),
        KeySelector.firstGreaterOrEqual(keyServersKey(end)), 0, false
    ) { currentBegin, currentEnd, _, iteration ->
        fdbFutureFromSuspend {
            while (true) {
                currentCoroutineContext().ensureActive()
                // onError resets options; restore them for every retry.
                transaction.options().setReadSystemKeys()
                transaction.options().setLockAware()
                try {
                    return@fdbFutureFromSuspend if (fetchPage != null) fetchPage(currentBegin, currentEnd, iteration).await()
                    else transaction.fetchRange(currentBegin, currentEnd, 0, false, StreamingMode.WANT_ALL, iteration).await()
                } catch (error: FDBException) { transaction.onError(error).await() }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }
    val delegate = object : AsyncIterator<ByteArray> {
        override fun hasNext() = cursor.hasNext()
        override fun onHasNext() = cursor.onHasNext()
        override suspend fun next(): ByteArray {
            val row = cursor.next()
            return row.key.copyOfRange(KEY_SERVERS_PREFIX.size, row.key.size)
        }
        override fun cancel() = cursor.cancel()
    }
    return CloseableAsyncIterator(delegate, closeAction)
}
