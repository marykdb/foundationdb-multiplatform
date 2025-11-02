package maryk.foundationdb.tuple

actual class Versionstamp internal constructor(
    internal val delegate: com.apple.foundationdb.tuple.Versionstamp
) : Comparable<Versionstamp> {
    actual val isComplete: Boolean
        get() = delegate.isComplete

    actual val bytes: ByteArray
        get() = delegate.bytes

    actual val userVersion: Int
        get() = delegate.userVersion

    actual fun transactionVersion(): ByteArray = delegate.transactionVersion

    actual override fun compareTo(other: Versionstamp): Int = delegate.compareTo(other.delegate)

    actual companion object {
        actual val LENGTH: Int = com.apple.foundationdb.tuple.Versionstamp.LENGTH

        actual fun fromBytes(bytes: ByteArray): Versionstamp =
            Versionstamp(com.apple.foundationdb.tuple.Versionstamp.fromBytes(bytes))

        actual fun incomplete(userVersion: Int): Versionstamp =
            Versionstamp(com.apple.foundationdb.tuple.Versionstamp.incomplete(userVersion))

        actual fun complete(transactionVersion: ByteArray, userVersion: Int): Versionstamp =
            Versionstamp(com.apple.foundationdb.tuple.Versionstamp.complete(transactionVersion, userVersion))

        actual fun unpackUserVersion(bytes: ByteArray, offset: Int): Int =
            com.apple.foundationdb.tuple.Versionstamp.unpackUserVersion(bytes, offset)
    }
}
