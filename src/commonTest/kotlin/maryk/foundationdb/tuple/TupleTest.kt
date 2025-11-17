package maryk.foundationdb.tuple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TupleTest {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun tupleAccessorsRoundTrip() {
        val nested = Tuple.from("inner", 99L)
        val versionstamp = Versionstamp.complete(ByteArray(Versionstamp.LENGTH - 2), 12)
        val uuid = Uuid.random()
        val tuple = Tuple.from(
            "text",
            123L,
            3.14,
            true,
            nested,
            versionstamp,
            uuid,
            "bytes".encodeToByteArray()
        )

        assertEquals("text", tuple.getString(0))
        assertEquals(123L, tuple.getLong(1))
        assertEquals(3.14, tuple.getDouble(2))
        assertTrue(tuple.getBoolean(3))
        val nestedTuple = tuple.getTuple(4)
        assertEquals("inner", nestedTuple.getString(0))
        assertEquals(99L, nestedTuple.getLong(1))

        val unpackedVersionstamp = tuple.getVersionstamp(5)
        assertEquals(versionstamp.userVersion, unpackedVersionstamp.userVersion)

        val unpackedUuid = tuple.getUuid(6)
        assertEquals(uuid, unpackedUuid)

        val bytes = tuple.getBytes(7)
        assertEquals("bytes", bytes.decodeToString())

        val packed = tuple.pack()
        val reparsed = Tuple.fromBytes(packed)
        fun normalize(item: Any?): Any? = when (item) {
            is ByteArray -> item.decodeToString()
            is Tuple -> item.items.map { normalize(it) }
            is Versionstamp -> item.bytes.toList()
            else -> item
        }
        fun normalizeItems(items: List<Any?>) = items.map { normalize(it) }
        assertEquals(normalizeItems(tuple.items), normalizeItems(reparsed.items))
    }
}
