package maryk.foundationdb.subspace

import maryk.foundationdb.Range
import maryk.foundationdb.tuple.Tuple

/**
 * Namespace helper that prefixes packed tuples with a fixed byte prefix.
 */
expect class Subspace {
    constructor()
    constructor(prefix: Tuple)
    constructor(rawPrefix: ByteArray)
    constructor(prefix: Tuple, rawPrefix: ByteArray)

    fun get(obj: Any?): Subspace
    fun get(tuple: Tuple): Subspace

    fun getKey(): ByteArray
    fun pack(): ByteArray
    fun pack(value: Any?): ByteArray
    fun pack(tuple: Tuple): ByteArray
    fun packWithVersionstamp(tuple: Tuple): ByteArray

    fun unpack(key: ByteArray): Tuple

    fun range(): Range
    fun range(tuple: Tuple): Range

    fun contains(key: ByteArray): Boolean

    fun subspace(tuple: Tuple): Subspace
}
