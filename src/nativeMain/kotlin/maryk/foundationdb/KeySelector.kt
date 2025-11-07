package maryk.foundationdb

actual class KeySelector actual constructor(
    actual val key: ByteArray,
    actual val orEqual: Boolean,
    actual val offset: Int
) {
    actual fun add(offset: Int): KeySelector =
        KeySelector(key.copyOf(), orEqual, this.offset + offset)

    actual companion object {
        actual fun lastLessThan(key: ByteArray): KeySelector = KeySelector(key.copyOf(), orEqual = false, offset = 0)
        actual fun lastLessOrEqual(key: ByteArray): KeySelector = KeySelector(key.copyOf(), orEqual = true, offset = 0)
        actual fun firstGreaterThan(key: ByteArray): KeySelector = KeySelector(key.copyOf(), orEqual = true, offset = 1)
        actual fun firstGreaterOrEqual(key: ByteArray): KeySelector = KeySelector(key.copyOf(), orEqual = false, offset = 1)
    }
}
