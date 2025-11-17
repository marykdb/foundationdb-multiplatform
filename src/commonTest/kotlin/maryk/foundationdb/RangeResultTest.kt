package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class RangeResultTest {
    @OptIn(ExperimentalTime::class)
    @Test
    fun collectRangeSupportsMultipleEntryPoints() = runBlocking {
        ensureApiVersionSelected()
        val database = FDB.instance().open()
        val uniqueSuffix = Clock.System.now().toEpochMilliseconds()
        val prefixTuple = Tuple.from("range-tests", uniqueSuffix)
        val prefixBytes = prefixTuple.pack()
        fun key(index: Int) = Tuple.from("range-tests", uniqueSuffix, index).pack()
        val endKey = Tuple.from("range-tests", uniqueSuffix, Int.MAX_VALUE).pack()

        database.run { txn ->
            txn.clear(Range.startsWith(prefixBytes))
            (0..2).forEach { idx ->
                txn.set(key(idx), "value-$idx".encodeToByteArray())
            }
        }

        try {
            val limited = database.readAsync { rt ->
                rt.collectRange(
                    begin = key(0),
                    end = endKey,
                    limit = 2,
                    reverse = false,
                    streamingMode = StreamingMode.EXACT
                )
            }.await()

            assertEquals(2, limited.values.size)
            assertTrue(limited.values.first().key.contentEquals(key(0)))

            val defaults = database.readAsync { rt ->
                rt.collectRange(
                    begin = key(0),
                    end = endKey
                )
            }.await()
            assertEquals(3, defaults.values.size)

            val selectorBased = database.readAsync { rt ->
                rt.collectRange(
                    KeySelector.firstGreaterOrEqual(key(0)),
                    KeySelector.firstGreaterOrEqual(endKey),
                    limit = 0,
                    reverse = false,
                    streamingMode = StreamingMode.SERIAL
                )
            }.await()

            assertEquals(3, selectorBased.values.size)
            assertTrue(selectorBased.values.size > limited.values.size)

            val selectorDefaults = database.readAsync { rt ->
                rt.collectRange(
                    KeySelector.firstGreaterOrEqual(key(0)),
                    KeySelector.firstGreaterOrEqual(endKey)
                )
            }.await()
            assertEquals(3, selectorDefaults.values.size)

            val rangeBased = database.readAsync { rt ->
                rt.collectRange(
                    Range.startsWith(prefixBytes),
                    limit = 0,
                    reverse = false,
                    streamingMode = StreamingMode.WANT_ALL
                )
            }.await()

            assertEquals(3, rangeBased.values.size)

            val rangeDefaults = database.readAsync { rt ->
                rt.collectRange(Range.startsWith(prefixBytes))
            }.await()
            assertEquals(3, rangeDefaults.values.size)

            database.read { rt ->
                val scopedRange = Range.startsWith(prefixBytes)
                StreamingMode.entries.forEach { mode ->
                    runBlocking {
                        rt.getRange(scopedRange, limit = 1, reverse = false, streamingMode = mode)
                            .asList()
                            .await()
                    }
                }
            }
        } finally {
            database.run { txn ->
                txn.clear(Range.startsWith(prefixBytes))
            }
            database.close()
        }
    }
}
