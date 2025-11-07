package maryk.foundationdb.tuple

actual class Versionstamp private constructor(
    actual val isComplete: Boolean,
    actual val bytes: ByteArray,
    private val transactionVersion: ByteArray
) : Comparable<Versionstamp> {
    actual val userVersion: Int = unpackUserVersion(bytes, transactionVersion.size)

    actual fun transactionVersion(): ByteArray = transactionVersion.copyOf()

    actual override fun compareTo(other: Versionstamp): Int =
        transactionVersion.contentHashCode().compareTo(other.transactionVersion.contentHashCode())

    actual companion object {
        private val UNSET_TRANSACTION_VERSION = ByteArray(10) { 0xff.toByte() }

        actual val LENGTH: Int = UNSET_TRANSACTION_VERSION.size + 2

        actual fun fromBytes(bytes: ByteArray): Versionstamp {
            require(bytes.size == LENGTH) { "Versionstamp bytes must have length $LENGTH" }
            val complete = !bytes.copyOfRange(0, UNSET_TRANSACTION_VERSION.size)
                .contentEquals(UNSET_TRANSACTION_VERSION)
            val txVersion = bytes.copyOf(UNSET_TRANSACTION_VERSION.size)
            return Versionstamp(complete, bytes.copyOf(), txVersion)
        }

        actual fun incomplete(userVersion: Int): Versionstamp {
            require(userVersion in 0..0xffff) { "userVersion must fit in unsigned short" }
            val bytes = ByteArray(LENGTH)
            UNSET_TRANSACTION_VERSION.copyInto(bytes, endIndex = UNSET_TRANSACTION_VERSION.size)
            bytes[UNSET_TRANSACTION_VERSION.size] = ((userVersion ushr 8) and 0xff).toByte()
            bytes[UNSET_TRANSACTION_VERSION.size + 1] = (userVersion and 0xff).toByte()
            val txVersion = bytes.copyOf(UNSET_TRANSACTION_VERSION.size)
            return Versionstamp(false, bytes, txVersion)
        }

        actual fun complete(transactionVersion: ByteArray, userVersion: Int): Versionstamp {
            require(transactionVersion.size == UNSET_TRANSACTION_VERSION.size) {
                "transactionVersion must have length ${UNSET_TRANSACTION_VERSION.size}"
            }
            require(userVersion in 0..0xffff) { "userVersion must fit in unsigned short" }
            val bytes = ByteArray(LENGTH)
            transactionVersion.copyInto(bytes)
            bytes[UNSET_TRANSACTION_VERSION.size] = ((userVersion ushr 8) and 0xff).toByte()
            bytes[UNSET_TRANSACTION_VERSION.size + 1] = (userVersion and 0xff).toByte()
            val txVersion = transactionVersion.copyOf()
            return Versionstamp(true, bytes, txVersion)
        }

        actual fun unpackUserVersion(bytes: ByteArray, offset: Int): Int {
            if (offset < 0 || offset + 1 >= bytes.size) return 0
            return (((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff))
        }
    }
}
