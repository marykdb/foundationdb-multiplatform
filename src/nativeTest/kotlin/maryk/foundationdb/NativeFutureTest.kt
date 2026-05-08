package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class NativeFutureTest {
    @Test
    fun cancelMarksSuspendFutureCancelled() = runBlocking {
        val future = fdbFutureFromSuspend {
            delay(60_000)
            "unused"
        }

        future.cancel()

        assertTrue(future.isCancelled)
        assertTrue(future.isDone)
    }

    @Test
    fun cancelAfterNativeFutureCompletionIsNoOp() = runBlocking {
        FDB.selectAPIVersion(ApiVersion.LATEST)
        val database = FDB.instance().open()
        val transaction = database.createTransaction()
        try {
            val future = transaction.get("native-future-complete".encodeToByteArray())

            assertEquals(null, future.await())

            future.cancel()

            assertTrue(future.isDone)
        } finally {
            transaction.close()
            database.close()
        }
    }
}
