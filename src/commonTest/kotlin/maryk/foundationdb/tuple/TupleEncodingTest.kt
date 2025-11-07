package maryk.foundationdb.tuple

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import maryk.foundationdb.ApiVersion
import maryk.foundationdb.FDB
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TupleEncodingTest {
    @Test
    fun roundTripVariousTypes() {
        val nested = Tuple.from("nested", 1L)
        val bytes = byteArrayOf(0x00, 0x10, 0x20)
        val uuid = Uuid.random()
        val tuple = Tuple.from("hello", bytes, 42L, -7L, true, false, 3.5, 2.25f, uuid, nested)
        val packed = tuple.pack()
        val decoded = Tuple.fromBytes(packed)
        assertEquals("hello", decoded.items[0])
        assertContentEquals(bytes, decoded.items[1] as ByteArray)
        assertEquals(42L, decoded.items[2])
        assertEquals(-7L, decoded.items[3])
        assertEquals(true, decoded.items[4])
        assertEquals(false, decoded.items[5])
        assertEquals(3.5, decoded.items[6])
        assertEquals(2.25f, decoded.items[7])
        assertEquals(uuid, decoded.items[8])
        val nestedTuple = decoded.items[9] as Tuple
        assertEquals(listOf("nested", 1L), nestedTuple.items)
    }

    @Test
    fun packWithVersionstampSupportsIncompleteValues() {
        FDB.selectAPIVersion(ApiVersion.LATEST)
        val incomplete = Versionstamp.incomplete(42)
        val tuple = Tuple.from("vs", incomplete)
        assertFailsWith<IllegalArgumentException> {
            tuple.pack()
        }
        val packed = tuple.packWithVersionstamp()
        val decoded = Tuple.fromBytes(packed.copyOfRange(0, packed.size - 4))
        val vs = decoded.items[1] as Versionstamp
        assertEquals(false, vs.isComplete)
        assertEquals(Versionstamp.incomplete(42).bytes.toList(), vs.bytes.toList())
    }

    @Test
    fun typedGettersExposeValues() {
        val uuid = Uuid.random()
        val tuple = Tuple.from("text", 7L, false, uuid)
        assertEquals("text", tuple.getString(0))
        assertEquals(7L, tuple.getLong(1))
        assertEquals(false, tuple.getBoolean(2))
        assertEquals(uuid, tuple.getUuid(3))
    }
}
