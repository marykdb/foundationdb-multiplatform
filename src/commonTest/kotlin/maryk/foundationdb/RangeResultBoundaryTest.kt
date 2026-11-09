package maryk.foundationdb

import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals

class RangeResultBoundaryTest {
    @Test fun exactBoundariesAcrossModesAndDirections() = FoundationDbTestHarness().runAndReset {
        val keys = List(257) { key("rows", it.toString().padStart(4, '0')) }
        database.run { transaction -> keys.forEach { transaction.set(it, ByteArray(2048) { 7 }) } }
        val range = Range.startsWith(prefixBytes("rows"))
        for (mode in StreamingMode.entries) for (reverse in listOf(false, true)) {
            for (limit in listOf(1, 17, 256, 257, 258, 0)) {
                if (mode == StreamingMode.EXACT && limit == 0) continue
                val result = database.readSuspend { it.collectRange(range, limit, reverse, mode).await() }
                val expected = (if (reverse) keys.reversed() else keys).take(if (limit == 0) keys.size else limit)
                val label = "$mode reverse=$reverse limit=$limit"
                assertEquals(expected.map { it.decodeToString() }, result.values.map { it.key.decodeToString() }, label)
                assertEquals(expected.size, result.summary.keyCount, label)
                assertEquals(limit > 0 && limit < keys.size, result.hasMore, label)
                assertEquals(expected.lastOrNull()?.decodeToString(), result.summary.lastKey?.decodeToString(), label)
            }
        }
    }

    @Test fun mappedSummariesUseActualRemainingPrimaryRows() = FoundationDbTestHarness().runAndReset {
        val records = Subspace(keyTuple("boundary-records"))
        val index = Subspace(keyTuple("boundary-index"))
        val keys = List(33) { index.pack(Tuple.from(it.toString().padStart(4, '0'))) }
        val mapper = Tuple.from(namespace, "boundary-records", "{K[2]}", "{...}").pack()
        try {
            database.run { transaction -> repeat(33) {
                transaction.set(keys[it], byteArrayOf())
                transaction.set(records.pack(Tuple.from(it.toString().padStart(4, '0'))), ByteArray(2048) { 7 })
            } }
            for (mode in StreamingMode.entries) for (reverse in listOf(false, true)) {
                for (limit in listOf(1, 32, 33, 34, 0)) {
                    if (mode == StreamingMode.EXACT && limit == 0) continue
                    val result = database.readSuspend { it.collectMappedRange(index.range(), mapper, limit, reverse, mode).await() }
                    val expected = (if (reverse) keys.reversed() else keys).take(if (limit == 0) keys.size else limit)
                    val label = "$mode reverse=$reverse limit=$limit"
                    assertEquals(expected.map { it.toList() }, result.values.map { it.key.toList() }, label)
                    assertEquals(expected.size, result.summary.keyCount, label)
                    assertEquals(limit > 0 && limit < keys.size, result.hasMore, label)
                    assertEquals(expected.lastOrNull()?.toList(), result.summary.lastKey?.toList(), label)
                    result.values.forEach { row -> assertEquals(1, row.rangeResult.size, label) }
                }
            }
        } finally { database.run { it.clear(index.range()); it.clear(records.range()) } }
    }
}
