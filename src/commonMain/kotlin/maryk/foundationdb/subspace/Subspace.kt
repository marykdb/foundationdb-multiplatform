package maryk.foundationdb.subspace

import maryk.foundationdb.Range
import maryk.foundationdb.tuple.Tuple

/**
 * Namespace helper that prefixes packed tuples with a fixed byte prefix.
 */
expect open class Subspace {
    constructor()
    constructor(prefix: Tuple)
    constructor(rawPrefix: ByteArray)
    constructor(prefix: Tuple, rawPrefix: ByteArray)

    open fun get(obj: Any?): Subspace
    open fun get(tuple: Tuple): Subspace

    open fun getKey(): ByteArray
    open fun pack(): ByteArray
    open fun pack(value: Any?): ByteArray
    open fun pack(tuple: Tuple): ByteArray
    open fun packWithVersionstamp(tuple: Tuple): ByteArray

    open fun unpack(key: ByteArray): Tuple

    open fun range(): Range
    open fun range(tuple: Tuple): Range

    open fun contains(key: ByteArray): Boolean

    open fun subspace(tuple: Tuple): Subspace
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

}
