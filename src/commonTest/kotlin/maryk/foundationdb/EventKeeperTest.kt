package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals

class EventKeeperTest {
    @Test
    fun mapEventKeeperAggregatesCountsAndTimes() {
        val keeper = MapEventKeeper()
        keeper.count(FdbEvent.BYTES_FETCHED)
        keeper.count(FdbEvent.BYTES_FETCHED, 4)
        keeper.timeNanos(FdbEvent.RANGE_QUERY_FETCH_TIME_NANOS, 100)
        keeper.timeNanos(FdbEvent.RANGE_QUERY_FETCH_TIME_NANOS, 50)

        assertEquals(5, keeper.getCount(FdbEvent.BYTES_FETCHED))
        assertEquals(150, keeper.getTimeNanos(FdbEvent.RANGE_QUERY_FETCH_TIME_NANOS))

        // Reading untouched events should default to zero and cover enum constants.
        FdbEvent.values().forEach { event ->
            keeper.getCount(event)
            keeper.getTimeNanos(event)
        }
    }
}
