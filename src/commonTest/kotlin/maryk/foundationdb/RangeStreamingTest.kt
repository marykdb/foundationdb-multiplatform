
package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals

class RangeStreamingTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun `range iterator streams in order`() = harness.runAndReset {
        val prefix = key("stream", "iter")
        repeat(10) { idx ->
            database.runSuspend { txn ->
                txn.set(key("stream", "iter", "k$idx"), "v$idx".encodeToByteArray())
            }
        }

        val values = mutableListOf<String>()
        database.readSuspend { txn ->
            val iterator = txn.getRange(Range.startsWith(prefix)).iterator()
            while (iterator.onHasNext().await()) {
                val kv = iterator.next()
                values += kv.value.decodeToString()
            }
        }

        assertEquals((0 until 10).map { "v$it" }, values)
    }

    @Test
    fun `range iterator respects limits`() = harness.runAndReset {
        val prefix = key("stream", "limit")
        repeat(5) { idx ->
            database.runSuspend { txn ->
                txn.set(key("stream", "limit", "k$idx"), "v$idx".encodeToByteArray())
            }
        }

        val values = mutableListOf<String>()
        database.readSuspend { txn ->
            val iterable = txn.getRange(
                begin = Range.startsWith(prefix).begin,
                end = Range.startsWith(prefix).end,
                limit = 3,
                reverse = false,
                streamingMode = StreamingMode.WANT_ALL
            )
            val iterator = iterable.iterator()
            while (iterator.onHasNext().await()) {
                values += iterator.next().value.decodeToString()
            }
        }

        assertEquals(listOf("v0", "v1", "v2"), values)
    }
}
