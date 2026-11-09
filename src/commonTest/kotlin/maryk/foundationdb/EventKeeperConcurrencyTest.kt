package maryk.foundationdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EventKeeperConcurrencyTest {
    private data class CustomEvent(override val name: String) : Event

    @Test fun simultaneousRegistrationAndUpdatesKeepCountsAndTimesIndependent() = runBlocking {
        val keeper = MapEventKeeper()
        List(8) { async(Dispatchers.Default) {
            repeat(2000) { index ->
                // Different but equal event objects race to register each key.
                val event = CustomEvent("custom-${index % 20}")
                keeper.count(event, 2)
                keeper.timeNanos(event, 3)
            }
        } }.awaitAll()
        repeat(20) { index ->
            val event = CustomEvent("custom-$index")
            assertEquals(1600, keeper.getCount(event))
            assertEquals(2400, keeper.getTimeNanos(event))
        }
        assertEquals(0, keeper.getCount(CustomEvent("absent")))
    }
}
