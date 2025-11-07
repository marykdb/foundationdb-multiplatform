package maryk.foundationdb

actual class Range actual constructor(actual val begin: ByteArray, actual val end: ByteArray) {
    actual companion object {
        actual fun startsWith(prefix: ByteArray): Range = Range(prefix.copyOf(), prefix.nextKey())
    }
}
