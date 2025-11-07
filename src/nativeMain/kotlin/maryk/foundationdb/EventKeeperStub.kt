package maryk.foundationdb

actual class MapEventKeeper actual constructor() : EventKeeper {
    private val counts = mutableMapOf<FdbEvent, Long>()
    private val times = mutableMapOf<FdbEvent, Long>()

    actual override fun count(event: FdbEvent, amount: Long) {
        counts[event] = (counts[event] ?: 0L) + amount
    }

    actual override fun timeNanos(event: FdbEvent, nanos: Long) {
        times[event] = (times[event] ?: 0L) + nanos
    }

    actual override fun getCount(event: FdbEvent): Long = counts[event] ?: 0L

    actual override fun getTimeNanos(event: FdbEvent): Long = times[event] ?: 0L
}
