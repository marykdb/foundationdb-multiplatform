package maryk.foundationdb

import com.apple.foundationdb.Database as JavaDatabase
import com.apple.foundationdb.ReadTransaction as JavaReadTransaction
import com.apple.foundationdb.KeyValue as JavaKeyValue
import com.apple.foundationdb.MappedKeyValue as JavaMappedKeyValue
import com.apple.foundationdb.async.AsyncIterable as JavaAsyncIterable
import com.apple.foundationdb.async.AsyncIterator as JavaAsyncIterator
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JavaRangeCollectionOwnershipTest {
    private class Rows(val count: Int, val mapped: Boolean, val wait: Boolean = false) : JavaAsyncIterable<JavaKeyValue> {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val cancels = AtomicInteger()
        val reads = AtomicInteger()
        val pending = CompletableFuture<Boolean>()
        override fun iterator(): JavaAsyncIterator<JavaKeyValue> = object : JavaAsyncIterator<JavaKeyValue> {
            private var index = 0
            override fun hasNext() = index < count
            override fun next(): JavaKeyValue {
                reads.incrementAndGet()
                val key = ByteArrayUtil.encodeInt(index++.toLong())
                return if (mapped) JavaMappedKeyValue(key, byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), emptyList())
                    else JavaKeyValue(key, byteArrayOf(1))
            }
            override fun onHasNext(): CompletableFuture<Boolean> {
                entered.complete(Unit)
                return if (wait) pending else CompletableFuture.completedFuture(hasNext())
            }
            override fun cancel() { cancels.incrementAndGet(); pending.cancel(false); cancelled.complete(Unit) }
            override fun remove(): Unit = throw UnsupportedOperationException()
        }
        override fun asList(): CompletableFuture<List<JavaKeyValue>> = error("Use cursor iteration")
    }

    private fun transaction(rows: Rows, onLimit: (Int) -> Unit = {}): ReadTransaction {
        val delegate = Proxy.newProxyInstance(JavaReadTransaction::class.java.classLoader,
            arrayOf(JavaReadTransaction::class.java)) { _, method, args ->
            check(method.name == "getRange" || method.name == "getMappedRange")
            val limit = args.filterIsInstance<Int>().single()
            require(limit >= 0)
            onLimit(limit)
            rows
        } as JavaReadTransaction
        return ReadTransaction(delegate)
    }

    @Test fun immediatelyReadyPlainAndMappedRowsAreStackSafe() = runBlocking {
        withTimeout(10_000) {
            val plain = transaction(Rows(30_000, false)).collectRange(byteArrayOf(1), byteArrayOf(2), streamingMode = StreamingMode.WANT_ALL).await()
            assertEquals(30_000, plain.values.size)
            assertEquals(30_000, plain.summary.keyCount)
            assertFalse(plain.hasMore)
            val mapped = transaction(Rows(30_000, true)).collectMappedRange(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), streamingMode = StreamingMode.WANT_ALL).await()
            assertEquals(30_000, mapped.values.size)
            assertEquals(30_000, mapped.summary.keyCount)
            assertFalse(mapped.hasMore)
        }
    }

    @Test fun cancellingEitherCollectorCancelsItsReadAndCursorExactlyOnce() = runBlocking {
        withTimeout(10_000) {
            for (mapped in listOf(false, true)) {
                val rows = Rows(1, mapped, wait = true)
                val transaction = transaction(rows)
                val result = if (mapped) transaction.collectMappedRange(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))
                    else transaction.collectRange(byteArrayOf(1), byteArrayOf(2))
                rows.entered.await()
                result.cancel()
                rows.cancelled.await()
                assertTrue(result.isCancelled)
                assertTrue(rows.pending.isCancelled)
                assertEquals(1, rows.cancels.get())
            }
        }
    }

    @Test fun finiteAndMaximumLimitsKeepSummaryAndNeverOverflow() = runBlocking {
        var requested = 0
        val plain = transaction(Rows(3, false)) { requested = it }.collectRange(byteArrayOf(1), byteArrayOf(2), limit = Int.MAX_VALUE).await()
        assertEquals(Int.MAX_VALUE, requested)
        assertEquals(3, plain.values.size)
        val mapped = transaction(Rows(3, true)) { requested = it }.collectMappedRange(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), limit = Int.MAX_VALUE).await()
        assertEquals(Int.MAX_VALUE, requested)
        assertEquals(3, mapped.values.size)
        val limitedRows = Rows(3, false)
        val limited = transaction(limitedRows) { requested = it }.collectRange(byteArrayOf(1), byteArrayOf(2), limit = 2).await()
        assertEquals(3, requested)
        assertEquals(2, limited.values.size)
        assertEquals(2, limited.summary.keyCount)
        assertTrue(limited.hasMore)
        assertEquals(2, limitedRows.reads.get())
        val mappedRows = Rows(3, true)
        val limitedMapped = transaction(mappedRows).collectMappedRange(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), limit = 2).await()
        assertEquals(2, limitedMapped.values.size)
        assertTrue(limitedMapped.hasMore)
        assertEquals(2, mappedRows.reads.get())
    }

    @Test fun tenantAdministrationCancellationReachesTheSdkOperation() {
        for (create in listOf(false, true)) {
            val source = CompletableFuture<Void>()
            val delegate = Proxy.newProxyInstance(JavaDatabase::class.java.classLoader,
                arrayOf(JavaDatabase::class.java)) { _, method, _ ->
                check(method.name == "runAsync")
                source
            } as JavaDatabase
            val database = Database(delegate)
            val future = if (create) TenantManagement.createTenant(database, byteArrayOf(1))
                else TenantManagement.deleteTenant(database, byteArrayOf(1))
            future.cancel()
            assertTrue(source.isCancelled)
        }
    }
}
