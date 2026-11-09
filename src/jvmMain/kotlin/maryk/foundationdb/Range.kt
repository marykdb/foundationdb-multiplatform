package maryk.foundationdb

actual class Range internal constructor(internal val delegate: com.apple.foundationdb.Range) {
    actual constructor(begin: ByteArray, end: ByteArray) : this(com.apple.foundationdb.Range(begin, end))

    actual val begin: ByteArray
        get() = delegate.begin

    actual val end: ByteArray
        get() = delegate.end

    actual override fun equals(other: Any?): Boolean = other is Range && delegate == other.delegate
    actual override fun hashCode(): Int = delegate.hashCode()

    actual companion object {
        actual fun startsWith(prefix: ByteArray): Range =
            Range(com.apple.foundationdb.Range.startsWith(prefix))
    }
}
