package maryk.foundationdb

actual class MapEventKeeper actual constructor() : EventKeeper {
    private val delegate = com.apple.foundationdb.MapEventKeeper()

    actual override fun count(event: FdbEvent, amount: Long) {
        delegate.count(event.toJava(), amount)
    }

    actual override fun timeNanos(event: FdbEvent, nanos: Long) {
        delegate.timeNanos(event.toJava(), nanos)
    }

    actual override fun getCount(event: FdbEvent): Long = delegate.getCount(event.toJava())

    actual override fun getTimeNanos(event: FdbEvent): Long = delegate.getTimeNanos(event.toJava())
}

private fun FdbEvent.toJava(): com.apple.foundationdb.EventKeeper.Event =
    com.apple.foundationdb.EventKeeper.Events.valueOf(this.name)
