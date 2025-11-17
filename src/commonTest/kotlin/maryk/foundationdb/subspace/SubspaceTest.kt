package maryk.foundationdb.subspace

import maryk.foundationdb.FastByteComparisons
import maryk.foundationdb.ensureApiVersionSelected
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.Versionstamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubspaceTest {
    @Test
    fun constructorsAndPacking() {
        val defaultSubspace = Subspace()
        val tuplePrefix = Tuple.from("prefix")
        val tupleDerived = Subspace(tuplePrefix)
        val rawPrefix = "raw-prefix".encodeToByteArray()
        val rawDerived = Subspace(rawPrefix)
        val mixed = Subspace(tuplePrefix, rawPrefix)

        assertTrue(defaultSubspace.getKey().isEmpty())
        assertTrue(tupleDerived.getKey().isNotEmpty())
        assertTrue(rawDerived.contains(rawDerived.pack()))
        assertTrue(mixed.getKey().isNotEmpty())

        val nested = tupleDerived.get("accounts").get(Tuple.from("active"))
        val packed = nested.pack(Tuple.from(42L, "user"))
        assertTrue(nested.contains(packed))
        val unpacked = nested.unpack(packed)
        assertEquals(42L, unpacked.getLong(0))
        assertEquals("user", unpacked.getString(1))

        val vs = Versionstamp.incomplete(7)
        val vsKey = nested.packWithVersionstamp(Tuple.from(vs))
        assertTrue(vsKey.isNotEmpty())

        val range = nested.range(Tuple.from("segment"))
        assertTrue(range.begin.isNotEmpty())
        val child = nested.subspace(Tuple.from("child"))
        assertTrue(child.getKey().isNotEmpty())
    }

    @Test
    fun packAndUnpackRoundTripWithinSubspace() {
        val base = Subspace(Tuple.from("root"))
        val child = base.get("child")
        val encoded = child.pack(Tuple.from("record", 1))
        assertTrue(child.contains(encoded))
        val decoded = child.unpack(encoded)
        assertEquals("record", decoded.getString(0))
        assertEquals(1L, decoded.getLong(1))
    }

    @Test
    fun nestedRangeContainsPackedKeys() {
        val base = Subspace(Tuple.from("prefix"))
        val nestedRange = base.range(Tuple.from("child"))
        val nestedSubspace = base.get("child")
        val encoded = nestedSubspace.pack(Tuple.from("leaf"))
        val comparator = FastByteComparisons.comparator()
        assertTrue(comparator.compare(encoded, nestedRange.begin) >= 0)
        assertTrue(comparator.compare(encoded, nestedRange.end) < 0)
    }

    @Test
    fun defaultSubspaceBehavesLikeTuplePacking() {
        val subspace = Subspace()
        val tuple = Tuple.from("plain", 5)
        val encoded = subspace.pack(tuple)
        val unpacked = subspace.unpack(encoded)
        assertEquals("plain", unpacked.getString(0))
        assertEquals(5L, unpacked.getLong(1))
    }

    @Test
    fun rawPrefixConstructorMatchesTupleEncoding() {
        val prefixTuple = Tuple.from("raw")
        val rawPrefix = prefixTuple.pack()
        val subspace = Subspace(rawPrefix)
        val key = subspace.pack(Tuple.from("suffix"))
        assertTrue(subspace.contains(key))
        val unpacked = subspace.unpack(key)
        assertEquals(listOf("suffix"), unpacked.items)
    }

    @Test
    fun packWithVersionstampKeepsNamespace() {
        ensureApiVersionSelected()
        val base = Subspace(Tuple.from("vs"))
        val tuple = Tuple.from(Versionstamp.incomplete(7))
        val encoded = base.packWithVersionstamp(tuple)
        val unpacked = Tuple.fromBytes(encoded)
        assertEquals("vs", unpacked.getString(0))
        val versionstamp = unpacked.getVersionstamp(1)
        assertTrue(!versionstamp.isComplete)
        assertEquals(7, versionstamp.userVersion)
    }
}
