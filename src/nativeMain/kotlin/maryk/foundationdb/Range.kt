package maryk.foundationdb

actual class Range actual constructor(actual val begin: ByteArray, actual val end: ByteArray) {
    actual override fun equals(other: Any?): Boolean = other is Range && begin.contentEquals(other.begin) && end.contentEquals(other.end)
    actual override fun hashCode(): Int = begin.contentHashCode() xor (37 * end.contentHashCode())

    actual companion object {
        actual fun startsWith(prefix: ByteArray): Range = Range(prefix.copyOf(), prefix.nextKey())
    }
}
