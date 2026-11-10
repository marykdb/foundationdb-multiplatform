@file:Suppress("DEPRECATION")

package maryk.foundationdb

import com.apple.foundationdb.Database as JavaDatabase
import com.apple.foundationdb.Tenant as JavaTenant
import com.apple.foundationdb.Transaction as JavaTransaction
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AsyncRetryCancellationTest {
    private val executor = Executor { it.run() }

    private class Handle {
        val closed = AtomicInteger()
        val cancelled = AtomicInteger()
        val commits = AtomicInteger()
        val commit = CompletableFuture<Void>()
        val retry = object : CompletableFuture<JavaTransaction>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        }
        val delegate = Proxy.newProxyInstance(JavaTransaction::class.java.classLoader,
            arrayOf(JavaTransaction::class.java)) { _, method, _ ->
            when (method.name) {
                "close" -> { closed.incrementAndGet(); null }
                "cancel" -> { cancelled.incrementAndGet(); null }
                "commit" -> { commits.incrementAndGet(); commit }
                "onError" -> retry
                else -> error("Unexpected transaction operation: ${method.name}")
            }
        } as JavaTransaction
    }

    private fun context(tenant: Boolean, transaction: JavaTransaction): TransactionContext {
        val type = if (tenant) JavaTenant::class.java else JavaDatabase::class.java
        val delegate = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            when (method.name) {
                "createTransaction" -> transaction
                "getExecutor" -> executor
                else -> error("Unexpected context operation: ${method.name}")
            }
        }
        return if (tenant) Tenant(delegate as JavaTenant) else Database(delegate as JavaDatabase)
    }

    private fun <T> start(context: TransactionContext, read: Boolean, body: () -> FdbFuture<T>): FdbFuture<T> =
        if (read) context.readAsync { body() } else context.runAsync { body() }

    @Test
    fun cancellationClosesEveryOwnedContextAndCancelsItsBody() {
        for (tenant in listOf(false, true)) for (read in listOf(false, true)) {
            val handle = Handle()
            val body = CompletableFuture<String>()
            var entered = false
            val result = start(context(tenant, handle.delegate), read) {
                entered = true
                body.toFdbFuture()
            }
            assertTrue(entered, "The first attempt remains eager")
            result.cancel()
            assertTrue(body.isCancelled)
            assertEquals(1, handle.cancelled.get())
            assertEquals(1, handle.closed.get())
            assertEquals(0, handle.commits.get())
        }
    }

    @Test
    fun cancellationDuringCommitCancelsNativeWorkAndClosesTheHandle() {
        val handle = Handle()
        val result = start(context(false, handle.delegate), false) { CompletableFuture.completedFuture("ok").toFdbFuture() }
        assertEquals(1, handle.commits.get())
        result.cancel()
        assertTrue(handle.commit.isCancelled)
        assertEquals(1, handle.closed.get())
    }

    @Test
    fun cancellationDuringRetryClosesLateReplacementWithoutStartingAnotherAttempt() {
        for (tenant in listOf(false, true)) for (read in listOf(false, true)) {
            val old = Handle()
            val replacement = Handle()
            val attempts = AtomicInteger()
            val result = start(context(tenant, old.delegate), read) {
                attempts.incrementAndGet()
                CompletableFuture.failedFuture<String>(FDBException("conflict", 1020)).toFdbFuture()
            }
            result.cancel()
            assertEquals(1, old.closed.get())
            assertFalse(old.retry.isCancelled, "The replacement ownership observer must survive cancellation")
            old.retry.complete(replacement.delegate)
            assertEquals(1, replacement.closed.get())
            assertEquals(1, attempts.get())
        }
    }

    @Test
    fun observedCancellationLeavesTheOperationOwnedUntilSuccessfulCompletion() = runBlocking {
        val handle = Handle()
        val body = CompletableFuture<String>()
        val result = start(context(false, handle.delegate), true) { body.toFdbFuture() }
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { result.awaitObserved() }
        observer.cancel()
        observer.join()
        assertFalse(body.isCancelled)
        assertEquals(0, handle.closed.get())
        body.complete("ok")
        handle.commit.complete(null)
        assertEquals("ok", result.await())
        assertEquals(1, handle.closed.get())
    }

    @Test
    fun normalFailureClosesTheHandleAndPreservesTheFailure() = runBlocking {
        val handle = Handle()
        val failure = IllegalStateException("body failed")
        handle.retry.completeExceptionally(failure)
        val result = start(context(false, handle.delegate), false) { CompletableFuture.failedFuture<String>(failure).toFdbFuture() }
        val thrown = assertFailsWith<IllegalStateException> { result.await() }
        assertEquals(failure.message, thrown.message)
        assertEquals(1, handle.closed.get())
    }

    @Test
    fun successfulRetryUsesAndClosesTheTransferredHandle() = runBlocking {
        val old = Handle()
        val replacement = Handle()
        val attempts = mutableListOf<Transaction>()
        val result = context(false, old.delegate).runAsync { transaction ->
            attempts.add(transaction)
            if (attempts.size == 1) CompletableFuture.failedFuture<String>(FDBException("conflict", 1020)).toFdbFuture()
            else CompletableFuture.completedFuture("ok").toFdbFuture()
        }
        old.retry.complete(replacement.delegate)
        replacement.commit.complete(null)
        assertEquals("ok", result.await())
        assertEquals(2, attempts.size)
        assertNotSame(attempts[0], attempts[1], "Each attempt retains its own transaction view")
        assertTrue(old.delegate === attempts[0].delegate)
        assertTrue(replacement.delegate === attempts[1].delegate)
        assertEquals(1, replacement.closed.get())
    }

    @Test
    fun cancellingTheOwningWaitClosesAnActualNativeTransaction() = runBlocking {
        FDB.selectAPIVersion(ApiVersion.LATEST)
        val database = FDB.instance().open()
        try {
            for (read in listOf(false, true)) {
                lateinit var native: JavaTransaction
                val body = CompletableFuture<String>()
                val result = if (read) database.readAsync {
                    native = it.delegate as JavaTransaction
                    body.toFdbFuture()
                } else database.runAsync {
                    native = it.delegate
                    body.toFdbFuture()
                }
                val waiter = launch(start = CoroutineStart.UNDISPATCHED) { result.await() }
                waiter.cancel()
                waiter.join()
                assertTrue(body.isCancelled)
                // Cancelling a native transaction alone does not invalidate its handle;
                // this synchronous guard proves it was actually closed.
                assertFailsWith<IllegalStateException> { native.readVersion }
            }
        } finally { database.close() }
    }
}
