package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionMutationsTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun clearVariantsRemoveKeys() = harness.runAndReset {
        val single = key("txn", "clear", "single")
        val rangeBegin = key("txn", "clear", "range", "a")
        val rangeEnd = key("txn", "clear", "range", "b")
        val prefixKey = key("txn", "clear", "prefix", "x")
        val prefix = prefixBytes("txn", "clear", "prefix")

        database.runSuspend { txn ->
            txn.set(single, "one".encodeToByteArray())
            txn.set(rangeBegin, "range-a".encodeToByteArray())
            txn.set(rangeEnd, "range-b".encodeToByteArray())
            txn.set(prefixKey, "pref".encodeToByteArray())
        }

        database.runSuspend { txn ->
            txn.clear(single)
            txn.clear(rangeBegin, rangeEnd)
            txn.clear(Range.startsWith(prefix))
        }

        val results = database.readSuspend { rt ->
            listOf(
                rt.get(single).await(),
                rt.get(rangeBegin).await(),
                rt.get(rangeEnd).await(),
                rt.get(prefixKey).await()
            )
        }

        assertNull(results[0])
        assertNull(results[1])
        assertEquals("range-b", results[2]?.decodeToUtf8())
        assertNull(results[3])
    }

    @Test
    fun atomicAddAndRangeSizeEstimation() = harness.runAndReset {
        val counter = key("txn", "atomic", "counter")
        database.runSuspend { txn -> txn.set(counter, ByteArray(8)) }

        database.runSuspend { txn ->
            txn.atomicAdd(counter, 1L.toLittleEndianBytes())
        }

        val storedBytes = database.readSuspend { rt ->
            rt.get(counter).await()
        }
        assertEquals(8, storedBytes?.size)
        assertEquals(1L, storedBytes?.toLittleEndianLong())

        val estimatePrefix = prefixBytes("txn", "estimate")
        database.runSuspend { txn ->
            repeat(10) { idx ->
                txn.set(key("txn", "estimate", "k$idx"), ByteArray(128) { idx.toByte() })
            }
        }

        val estimated = database.runSuspend { txn ->
            txn.getEstimatedRangeSizeBytes(Range.startsWith(estimatePrefix)).await()
        }
        assertTrue(estimated >= 0)
    }

    @Test
    fun conflictHelpersExecute() = harness.runAndReset {
        val begin = key("txn", "conflicts", "a")
        val end = key("txn", "conflicts", "b")

        database.runSuspend { txn ->
            txn.addReadConflictKey(begin)
            txn.addReadConflictRange(begin, end)
            txn.addWriteConflictKey(begin)
            txn.addWriteConflictRange(begin, end)
            txn.set(begin, "value".encodeToByteArray())
        }

        val stored = database.readSuspend { rt -> rt.get(begin).await()?.decodeToUtf8() }
        assertEquals("value", stored)
    }
}

private fun Long.toLittleEndianBytes(): ByteArray {
    val buffer = ByteArray(8)
    for (i in 0 until 8) {
        buffer[i] = ((this shr (8 * i)) and 0xFF).toByte()
    }
    return buffer
}

private fun ByteArray.toLittleEndianLong(): Long {
    require(size >= 8)
    var value = 0L
    for (i in 0 until 8) {
        value = value or ((this[i].toLong() and 0xFF) shl (8 * i))
    }
    return value
}
