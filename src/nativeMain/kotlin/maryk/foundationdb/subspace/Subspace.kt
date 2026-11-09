package maryk.foundationdb.subspace

import maryk.foundationdb.Range
import maryk.foundationdb.nextKey
import maryk.foundationdb.tuple.Tuple

actual open class Subspace {
    private val storedPrefixBytes: ByteArray
    private val prefixBytes: ByteArray get() { checkUsable(); return storedPrefixBytes }
    protected open fun checkUsable() = Unit

    actual constructor() {
        storedPrefixBytes = ByteArray(0)
    }

    actual constructor(prefix: Tuple) {
        storedPrefixBytes = prefix.pack()
    }

    actual constructor(rawPrefix: ByteArray) {
        storedPrefixBytes = rawPrefix.copyOf()
    }

    actual constructor(prefix: Tuple, rawPrefix: ByteArray) {
        storedPrefixBytes = rawPrefix + prefix.pack()
    }

    actual open fun get(obj: Any?): Subspace =
        Subspace(prefixBytes + Tuple.from(obj).pack())

    actual open fun get(tuple: Tuple): Subspace = Subspace(prefixBytes + tuple.pack())

    actual open fun getKey(): ByteArray = prefixBytes.copyOf()

    actual open fun pack(): ByteArray = prefixBytes.copyOf()

    actual open fun pack(value: Any?): ByteArray = prefixBytes + Tuple.from(value).pack()

    actual open fun pack(tuple: Tuple): ByteArray = prefixBytes + tuple.pack()

    actual open fun packWithVersionstamp(tuple: Tuple): ByteArray = tuple.packWithVersionstamp(prefixBytes)

    actual open fun unpack(key: ByteArray): Tuple {
        require(contains(key)) { "Key does not belong to this subspace" }
        return Tuple.fromBytes(key, prefixBytes.size, key.size - prefixBytes.size)
    }

    actual open fun range(): Range = Tuple.from().range(prefixBytes)

    actual open fun range(tuple: Tuple): Range = tuple.range(prefixBytes)

    actual open fun contains(key: ByteArray): Boolean = key.startsWith(prefixBytes)

    actual open fun subspace(tuple: Tuple): Subspace = Subspace(prefixBytes + tuple.pack())

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(size + other.size)
        copyInto(result, 0)
        other.copyInto(result, size)
        return result
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    actual override fun equals(other: Any?): Boolean = other is Subspace && this::class == other::class && prefixBytes.contentEquals(other.prefixBytes)
    actual override fun hashCode(): Int = prefixBytes.contentHashCode()


}
