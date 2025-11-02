package maryk.foundationdb.subspace

import maryk.foundationdb.Range
import maryk.foundationdb.tuple.Tuple

actual class Subspace internal constructor(
    internal val delegate: com.apple.foundationdb.subspace.Subspace
) {
    actual constructor() : this(com.apple.foundationdb.subspace.Subspace())
    actual constructor(prefix: Tuple) : this(com.apple.foundationdb.subspace.Subspace(prefix.delegate))
    actual constructor(rawPrefix: ByteArray) : this(com.apple.foundationdb.subspace.Subspace(rawPrefix))
    actual constructor(prefix: Tuple, rawPrefix: ByteArray) : this(com.apple.foundationdb.subspace.Subspace(prefix.delegate, rawPrefix))

    actual fun get(obj: Any?): Subspace = Subspace(delegate.get(obj))

    actual fun get(tuple: Tuple): Subspace = Subspace(delegate.get(tuple.delegate))

    actual fun getKey(): ByteArray = delegate.getKey()

    actual fun pack(): ByteArray = delegate.pack()

    actual fun pack(value: Any?): ByteArray = delegate.pack(value)

    actual fun pack(tuple: Tuple): ByteArray = delegate.pack(tuple.delegate)

    actual fun packWithVersionstamp(tuple: Tuple): ByteArray = delegate.packWithVersionstamp(tuple.delegate)

    actual fun unpack(key: ByteArray): Tuple = Tuple(delegate.unpack(key))

    actual fun range(): Range = Range(delegate.range())

    actual fun range(tuple: Tuple): Range = Range(delegate.range(tuple.delegate))

    actual fun contains(key: ByteArray): Boolean = delegate.contains(key)

    actual fun subspace(tuple: Tuple): Subspace = Subspace(delegate.subspace(tuple.delegate))
}
