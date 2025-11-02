package maryk.foundationdb.tuple

/**
 * Represents a 12-byte FoundationDB versionstamp (10-byte transaction version + 2-byte user version).
 */
expect class Versionstamp : Comparable<Versionstamp> {
    val isComplete: Boolean
    val bytes: ByteArray
    val userVersion: Int

    fun transactionVersion(): ByteArray

    override fun compareTo(other: Versionstamp): Int

    companion object {
        val LENGTH: Int

        fun fromBytes(bytes: ByteArray): Versionstamp
        fun incomplete(userVersion: Int = 0): Versionstamp
        fun complete(transactionVersion: ByteArray, userVersion: Int = 0): Versionstamp
        fun unpackUserVersion(bytes: ByteArray, offset: Int): Int
    }
}
