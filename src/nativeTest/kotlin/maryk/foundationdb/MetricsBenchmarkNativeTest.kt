package maryk.foundationdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

/** Relative microbenchmark; no unstable timing thresholds in the regression suite. */
@OptIn(ExperimentalAtomicApi::class)
class MetricsBenchmarkNativeTest {
    private class SnapshotKeeper : EventKeeper {
        private val values = AtomicReference<List<Pair<Event, Long>>>(emptyList())
        override fun count(event: Event, amount: Long) {
            while (true) {
                val old = values.load()
                val index = old.indexOfFirst { it.first == event }
                val next = if (index < 0) old + (event to amount)
                    else List(old.size) { if (it == index) event to old[it].second + amount else old[it] }
                if (values.compareAndSet(old, next)) return
            }
        }
        override fun getCount(event: Event) = values.load().firstOrNull { it.first == event }?.second ?: 0L
        override fun timeNanos(event: Event, nanos: Long) = count(event, nanos)
        override fun getTimeNanos(event: Event) = getCount(event)
    }
    @Test fun comparePublishedSnapshotAndProductionCounters() = runBlocking {
        for (workers in listOf(1, 8)) {
            val samples = mutableMapOf("snapshot" to mutableListOf<Double>(), "production" to mutableListOf<Double>())
            repeat(6) { round ->
                val cases = listOf("snapshot" to SnapshotKeeper(), "production" to MapEventKeeper())
                for ((name, keeper) in if (round % 2 == 0) cases else cases.reversed()) {
                    val elapsed = measureTime {
                        List(workers) { async(Dispatchers.Default) {
                            repeat(30_000) { index -> keeper.increment(FdbEvent.entries[index % FdbEvent.entries.size]) }
                        } }.awaitAll()
                    }
                    assertEquals(workers * 30_000L, FdbEvent.entries.sumOf { keeper.getCount(it) })
                    if (round > 0) samples.getValue(name).add(elapsed.inWholeNanoseconds.toDouble() / (workers * 30_000))
                }
            }
            for ((name, values) in samples) println("METRICS_BENCH workers=$workers implementation=$name medianNsPerUpdate=${values.sorted()[values.size / 2]}")
        }
    }
}
