package maryk.foundationdb

enum class FdbEvent {
    JNI_CALL,
    BYTES_FETCHED,
    RANGE_QUERY_DIRECT_BUFFER_HIT,
    RANGE_QUERY_DIRECT_BUFFER_MISS,
    RANGE_QUERY_FETCHES,
    RANGE_QUERY_RECORDS_FETCHED,
    RANGE_QUERY_CHUNK_FAILED,
    RANGE_QUERY_FETCH_TIME_NANOS
}

interface EventKeeper {
    fun count(event: FdbEvent, amount: Long = 1)
    fun timeNanos(event: FdbEvent, nanos: Long)
    fun getCount(event: FdbEvent): Long
    fun getTimeNanos(event: FdbEvent): Long
}

expect class MapEventKeeper() : EventKeeper
