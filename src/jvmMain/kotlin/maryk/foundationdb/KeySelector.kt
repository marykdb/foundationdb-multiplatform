package maryk.foundationdb

actual class KeySelector internal constructor(
    internal val delegate: com.apple.foundationdb.KeySelector
) {
    actual constructor(key: ByteArray, orEqual: Boolean, offset: Int) :
        this(com.apple.foundationdb.KeySelector(key, orEqual, offset))

    actual val key: ByteArray
        get() = delegate.key

    actual val orEqual: Boolean
        get() = delegate.orEqual()

    actual val offset: Int
        get() = delegate.offset

    actual fun add(offset: Int): KeySelector =
        KeySelector(delegate.add(offset))

    actual companion object {
        actual fun lastLessThan(key: ByteArray): KeySelector =
            KeySelector(com.apple.foundationdb.KeySelector.lastLessThan(key))

        actual fun lastLessOrEqual(key: ByteArray): KeySelector =
            KeySelector(com.apple.foundationdb.KeySelector.lastLessOrEqual(key))

        actual fun firstGreaterThan(key: ByteArray): KeySelector =
            KeySelector(com.apple.foundationdb.KeySelector.firstGreaterThan(key))

        actual fun firstGreaterOrEqual(key: ByteArray): KeySelector =
            KeySelector(com.apple.foundationdb.KeySelector.firstGreaterOrEqual(key))
    }
}
