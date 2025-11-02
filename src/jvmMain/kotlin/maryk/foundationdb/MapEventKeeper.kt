package maryk.foundationdb

actual class MapEventKeeper actual constructor() : EventKeeper {
    private val delegate = com.apple.foundationdb.MapEventKeeper()

    override fun count(event: FdbEvent, amount: Long) {
        delegate.count(event.toJava(), amount)
    }

    override fun timeNanos(event: FdbEvent, nanos: Long) {
        delegate.timeNanos(event.toJava(), nanos)
    }

    override fun getCount(event: FdbEvent): Long = delegate.getCount(event.toJava())

    override fun getTimeNanos(event: FdbEvent): Long = delegate.getTimeNanos(event.toJava())
}

private fun FdbEvent.toJava(): com.apple.foundationdb.EventKeeper.Event =
    com.apple.foundationdb.EventKeeper.Events.valueOf(this.name)
