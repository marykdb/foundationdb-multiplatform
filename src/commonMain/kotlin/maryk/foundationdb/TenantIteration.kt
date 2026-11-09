@file:OptIn(ExperimentalAtomicApi::class)

package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.async.CloseableAsyncIterator
import maryk.foundationdb.async.asFlow
import maryk.foundationdb.async.asyncIterable
import maryk.foundationdb.tuple.Tuple
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal val TENANT_MANAGEMENT_PREFIX: ByteArray =
    byteArrayOf(0xff.toByte(), 0xff.toByte()) + "/management/tenant/map/".encodeToByteArray()

internal fun openTenantsIteratorInternal(database: Database, begin: ByteArray, end: ByteArray, limit: Int): CloseableAsyncIterator<KeyValue> {
    require(limit >= 0) { "Tenant limit must be nonnegative" }
    val transaction = database.createTransaction()
    return try { tenantIterator(transaction, begin, end, limit, transaction::close) }
    catch (error: Throwable) { transaction.close(); throw error }
}

private fun tenantIterator(transaction: ReadTransaction, begin: ByteArray, end: ByteArray, limit: Int, closeAction: () -> Unit): CloseableAsyncIterator<KeyValue> {
    transaction.options().setRawAccess()
    transaction.options().setLockAware()
    transaction.options().setSpecialKeySpaceRelaxed()
    val source = transaction.getRange(TENANT_MANAGEMENT_PREFIX + begin, TENANT_MANAGEMENT_PREFIX + end,
        limit, false, StreamingMode.ITERATOR).iterator()
    return TenantListingIterator(source, closeAction)
}

/** Owns the range and, for streaming calls, its transaction. Only one consumer advances the cursor. */
internal class TenantListingIterator(private val source: AsyncIterator<KeyValue>, private val closeAction: () -> Unit) : CloseableAsyncIterator<KeyValue> {
    private val closed = AtomicInt(0)
    override fun hasNext(): Boolean = if (closed.load() != 0) false else try {
        source.hasNext().also { if (!it) close() }
    } catch (error: Throwable) { closePreservingFailure(error); throw error }

    override suspend fun next(): KeyValue {
        if (closed.load() != 0) throw NoSuchElementException("Tenant iterator is closed")
        return try {
            val row = source.next()
            KeyValue(row.key.copyOfRange(TENANT_MANAGEMENT_PREFIX.size, row.key.size), row.value, copy = false)
        } catch (error: Throwable) { closePreservingFailure(error); throw error }
    }

    override fun onHasNext(): FdbFuture<Boolean> {
        if (closed.load() != 0) return completedFdbFuture(false)
        val pending = fdbFutureFromSuspend(onCompletion = { error ->
            if (error != null) closePreservingFailure(error)
        }) {
            try {
                if (closed.load() != 0) false else source.onHasNext().await().let { more ->
                    if (!more) close()
                    more && closed.load() == 0
                }
            } catch (error: Throwable) { closePreservingFailure(error); throw error }
        }
        // Explicit cancellation must release ownership even if the factory coroutine
        // was still queued and never entered its try/finally body.
        return object : FdbFuture<Boolean> by pending {
            override suspend fun await(): Boolean = try { pending.await() }
            catch (error: CancellationException) { closePreservingFailure(error); throw error }
            override fun cancel() {
                try { pending.cancel() } finally { if (pending.isCancelled) close() }
            }
        }
    }

    private fun closePreservingFailure(error: Throwable) {
        try { close() } catch (cleanup: Throwable) {
            if (cleanup !== error) error.addSuppressed(cleanup)
        }
    }

    override fun cancel() = close()
    override fun close() {
        if (closed.compareAndSet(0, 1)) try { source.cancel() } finally { closeAction() }
    }
}

internal fun listTenantsInternal(database: Database, begin: ByteArray, end: ByteArray, limit: Int): FdbFuture<List<KeyValue>> {
    require(limit >= 0) { "Tenant limit must be nonnegative" }
    val from = begin.copyOf()
    val until = end.copyOf()
    // The materialized API can retry the whole read, unlike a partially consumed stream.
    return database.readAsync { transaction -> fdbFutureFromSuspend {
        val iterator = tenantIterator(transaction, from, until, limit) {}
        try {
            val result = mutableListOf<KeyValue>()
            while (iterator.onHasNext().await()) result += iterator.next()
            result
        } finally { iterator.close() }
    } }
}

/** Cold flow: each collector owns a fresh iterator/transaction; early completion and cancellation close it. */
fun TenantManagement.listTenantsFlow(database: Database, begin: ByteArray = byteArrayOf(), end: ByteArray = byteArrayOf(0xff.toByte()), limit: Int = 0): Flow<KeyValue> {
    require(limit >= 0) { "Tenant limit must be nonnegative" }
    val from = begin.copyOf()
    val until = end.copyOf()
    return asyncIterable { openTenantsIterator(database, from, until, limit) }.asFlow()
}

fun TenantManagement.listTenantsFlow(database: Database, begin: Tuple, end: Tuple, limit: Int = 0): Flow<KeyValue> =
    listTenantsFlow(database, begin.pack(), end.pack(), limit)
