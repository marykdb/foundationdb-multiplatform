package maryk.foundationdb.tuple

import maryk.foundationdb.Range

actual class Tuple internal constructor(
    internal val delegate: com.apple.foundationdb.tuple.Tuple
) {
    actual val items: List<Any?>
        get() = delegate.items

    actual fun pack(): ByteArray = delegate.pack()

    actual fun packWithVersionstamp(): ByteArray =
        delegate.packWithVersionstamp()

    actual fun range(): Range = Range(delegate.range())

    actual companion object {
        actual fun from(vararg elements: Any?): Tuple =
            Tuple(com.apple.foundationdb.tuple.Tuple.from(*elements))

        actual fun fromList(elements: List<Any?>): Tuple =
            Tuple(com.apple.foundationdb.tuple.Tuple.fromList(elements))

        actual fun fromBytes(bytes: ByteArray): Tuple =
            Tuple(com.apple.foundationdb.tuple.Tuple.fromBytes(bytes))
    }
}
