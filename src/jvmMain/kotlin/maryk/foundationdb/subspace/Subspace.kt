package maryk.foundationdb.subspace

import com.apple.foundationdb.subspace.Subspace as JavaSubspace

import maryk.foundationdb.Range
import maryk.foundationdb.tuple.Tuple

actual open class Subspace internal constructor(
    internal val delegate: JavaSubspace
) {
    actual constructor() : this(JavaSubspace())
    actual constructor(prefix: Tuple) : this(JavaSubspace(prefix.delegate))
    actual constructor(rawPrefix: ByteArray) : this(JavaSubspace(rawPrefix))
    actual constructor(prefix: Tuple, rawPrefix: ByteArray) : this(JavaSubspace(prefix.delegate, rawPrefix))

    actual open fun get(obj: Any?): Subspace = get(Tuple.from(obj))

    actual open fun get(tuple: Tuple): Subspace = Subspace(delegate.get(tuple.delegate))

    actual open fun getKey(): ByteArray = delegate.getKey()

    actual open fun pack(): ByteArray = delegate.pack()

    actual open fun pack(value: Any?): ByteArray = pack(Tuple.from(value))

    actual open fun pack(tuple: Tuple): ByteArray = delegate.pack(tuple.delegate)

    actual open fun packWithVersionstamp(tuple: Tuple): ByteArray = delegate.packWithVersionstamp(tuple.delegate)

    actual open fun unpack(key: ByteArray): Tuple = Tuple(delegate.unpack(key))

    actual open fun range(): Range = Range(delegate.range())

    actual open fun range(tuple: Tuple): Range = Range(delegate.range(tuple.delegate))

    actual open fun contains(key: ByteArray): Boolean = delegate.contains(key)

    actual open fun subspace(tuple: Tuple): Subspace = Subspace(delegate.subspace(tuple.delegate))

    actual override fun equals(other: Any?): Boolean = other is Subspace && delegate == other.delegate
    actual override fun hashCode(): Int = delegate.hashCode()


}
