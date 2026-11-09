package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.Versionstamp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** API selection is process-global; run this class alone using foundationDbNativeTestFilter. */
class LegacyVersionstampApiNativeTest {
    @Test
    fun api510UsesTheLegacyVersionstampOffsetEncoding() = runBlocking<Unit> {
        val fdb = FDB.selectAPIVersion(510)
        val tuple = Tuple.from(Versionstamp.incomplete(7))
        val prefix = "legacy-api-versionstamp/".encodeToByteArray()
        val packed = tuple.packWithVersionstamp(prefix)
        assertEquals(prefix.size + 15, packed.size)
        assertEquals(15, tuple.getPackedSize())
        val offset = prefix.size + 1
        assertContentEquals(byteArrayOf(offset.toByte(), (offset shr 8).toByte()), packed.takeLast(2).toByteArray())
        assertFailsWith<IllegalArgumentException> { tuple.packWithVersionstamp(ByteArray(65_535)) }
        val database = fdb.open()
        try {
            database.run { it.mutate(MutationType.SET_VERSIONSTAMPED_KEY, packed, byteArrayOf(1)) }
            val range = Range.startsWith(prefix)
            val rows = database.readSuspend { it.getRange(range).asList().await() }
            assertEquals(1, rows.size)
            val stamp = Tuple.fromBytes(rows.single().key.copyOfRange(prefix.size, rows.single().key.size)).getVersionstamp(0)
            assertTrue(stamp.isComplete)
            assertEquals(7, stamp.userVersion)
        } finally {
            try { database.run { it.clear(Range.startsWith(prefix)) } } finally { try { database.close() } finally { fdb.shutdown() } }
        }
    }
}
