package maryk.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.posix.sched_yield
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class FutureScopeShutdownNativeTest {
    @Test fun queuedFactoryFuturesCompleteWhenTheirScopeIsReset() = runBlocking {
        val hold = AtomicBoolean(true)
        val blockers = List(256) { launch(Dispatchers.Default) { while (hold.load()) sched_yield() } }
        val futures = try {
            List(256) { fdbFuture { 1 } }.also { resetNativeFutureScope() }
        } finally { hold.store(false) }
        blockers.joinAll()
        withTimeout(5000) {
            for (future in futures) {
                try { future.await() } catch (_: CancellationException) { }
                assertTrue(future.isDone)
            }
        }
    }
}
