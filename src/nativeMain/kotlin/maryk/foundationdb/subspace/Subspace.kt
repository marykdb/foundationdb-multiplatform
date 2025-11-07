package maryk.foundationdb.subspace

import maryk.foundationdb.Range
import maryk.foundationdb.nextKey
import maryk.foundationdb.tuple.Tuple

actual class Subspace {
    private val prefixBytes: ByteArray

    actual constructor() {
        prefixBytes = ByteArray(0)
    }

    actual constructor(prefix: Tuple) {
        prefixBytes = prefix.pack()
    }

    actual constructor(rawPrefix: ByteArray) {
        prefixBytes = rawPrefix.copyOf()
    }

    actual constructor(prefix: Tuple, rawPrefix: ByteArray) {
        prefixBytes = prefix.pack() + rawPrefix
    }

    actual fun get(obj: Any?): Subspace =
        Subspace(prefixBytes + obj.toString().encodeToByteArray() + byteArrayOf('/'.code.toByte()))

    actual fun get(tuple: Tuple): Subspace = Subspace(prefixBytes + tuple.pack())

    actual fun getKey(): ByteArray = prefixBytes.copyOf()

    actual fun pack(): ByteArray = prefixBytes.copyOf()

    actual fun pack(value: Any?): ByteArray = get(value).pack()

    actual fun pack(tuple: Tuple): ByteArray = prefixBytes + tuple.pack()

    actual fun packWithVersionstamp(tuple: Tuple): ByteArray = pack(tuple)

    actual fun unpack(key: ByteArray): Tuple = Tuple.fromBytes(key.copyOf())

    actual fun range(): Range {
        val start = prefixBytes.copyOf()
        val end = prefixBytes.nextKey()
        return Range(start, end)
    }

    actual fun range(tuple: Tuple): Range {
        val packed = pack(tuple)
        return Range(packed, packed.nextKey())
    }

    actual fun contains(key: ByteArray): Boolean = key.startsWith(prefixBytes)

    actual fun subspace(tuple: Tuple): Subspace = Subspace(prefixBytes + tuple.pack())

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
}
