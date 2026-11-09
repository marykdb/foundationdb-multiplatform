package maryk.foundationdb

import kotlin.time.DurationUnit
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

interface Event { val name: String; val isTimeEvent: Boolean get() = false }

enum class FdbEvent : Event {
    JNI_CALL,
    BYTES_FETCHED,
    RANGE_QUERY_DIRECT_BUFFER_HIT,
    RANGE_QUERY_DIRECT_BUFFER_MISS,
    RANGE_QUERY_FETCHES,
    RANGE_QUERY_RECORDS_FETCHED,
    RANGE_QUERY_CHUNK_FAILED,
    RANGE_QUERY_FETCH_TIME_NANOS;

    override val isTimeEvent: Boolean get() = this == RANGE_QUERY_FETCH_TIME_NANOS
}

interface EventKeeper {
    fun increment(event: Event) = count(event, 1)
    fun time(event: Event, value: Long, unit: DurationUnit) = timeNanos(event, convertEventTime(value, unit, DurationUnit.NANOSECONDS))
    fun getTime(event: Event, unit: DurationUnit): Long = convertEventTime(getTimeNanos(event), DurationUnit.NANOSECONDS, unit)
    fun count(event: Event, amount: Long = 1)
    fun timeNanos(event: Event, nanos: Long)
    fun getCount(event: Event): Long
    fun getTimeNanos(event: Event): Long
}

/** Thread-safe event counters. Native records explicitly submitted events only. */
@OptIn(ExperimentalAtomicApi::class)
class MapEventKeeper : EventKeeper {
    private class Counter(val event: Event, value: Long) {
        val value = AtomicLong(value)
    }
    private val counts = AtomicReference<List<Counter>>(emptyList())
    private val times = AtomicReference<List<Counter>>(emptyList())
    override fun count(event: Event, amount: Long) = add(counts, event, amount)
    override fun timeNanos(event: Event, nanos: Long) = add(times, event, nanos)
    override fun getCount(event: Event): Long = counts.load().firstOrNull { it.event == event }?.value?.load() ?: 0L
    override fun getTimeNanos(event: Event): Long = times.load().firstOrNull { it.event == event }?.value?.load() ?: 0L
    private fun add(target: AtomicReference<List<Counter>>, event: Event, value: Long) {
        while (true) {
            val previous = target.load()
            val counter = previous.firstOrNull { it.event == event }
            if (counter != null) {
                counter.value.fetchAndAdd(value)
                return
            }
            // Only registration allocates a new immutable index. Established counters update
            // independently without copying the index or contending on its reference.
            if (target.compareAndSet(previous, previous + Counter(event, value))) return
        }
    }
}

private fun convertEventTime(value: Long, source: DurationUnit, target: DurationUnit): Long {
    fun scale(unit: DurationUnit): Long = when (unit) {
        DurationUnit.NANOSECONDS -> 1
        DurationUnit.MICROSECONDS -> 1_000
        DurationUnit.MILLISECONDS -> 1_000_000
        DurationUnit.SECONDS -> 1_000_000_000
        DurationUnit.MINUTES -> 60_000_000_000
        DurationUnit.HOURS -> 3_600_000_000_000
        DurationUnit.DAYS -> 86_400_000_000_000
        else -> throw IllegalArgumentException("Unsupported duration unit: $unit")
    }
    val from = scale(source)
    val to = scale(target)
    if (from <= to) return value / (to / from)
    val multiplier = from / to
    return when {
        value > Long.MAX_VALUE / multiplier -> Long.MAX_VALUE
        value < Long.MIN_VALUE / multiplier -> Long.MIN_VALUE
        else -> value * multiplier
    }
}
