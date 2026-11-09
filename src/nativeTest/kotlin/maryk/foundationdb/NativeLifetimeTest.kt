@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.sched_yield
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeLifetimeTest {
    @Test
    fun transactionCloseCanRaceTheLastPointerUser() = runBlocking {
        FDB.selectAPIVersion(ApiVersion.LATEST)
        val database = FDB.instance().open()
        try {
            withTimeout(10_000) {
                repeat(500) {
                    val transaction = database.createTransaction()
                    val entered = CompletableDeferred<Unit>()
                    val release = AtomicBoolean(false)
                    val user = async(Dispatchers.Default) {
                        transaction.handle.usePointer {
                            entered.complete(Unit)
                            while (!release.load()) sched_yield()
                        }
                    }
                    try {
                        entered.await()
                        val closing = async(Dispatchers.Default) {
                            release.store(true)
                            transaction.close()
                        }
                        closing.await(); user.await()
                        transaction.close()
                        assertFailsWith<IllegalStateException> { transaction.getReadVersion() }
                    } finally {
                        release.store(true)
                        user.await()
                        transaction.close()
                    }
                }
            }
        } finally { database.close() }
    }

}
