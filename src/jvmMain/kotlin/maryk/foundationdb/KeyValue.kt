package maryk.foundationdb

actual class KeyValue internal constructor(
    internal val delegate: com.apple.foundationdb.KeyValue
) {
    actual constructor(key: ByteArray, value: ByteArray) : this(com.apple.foundationdb.KeyValue(key, value))

    actual val key: ByteArray
        get() = delegate.key

    actual val value: ByteArray
        get() = delegate.value

    internal companion object {
        fun fromDelegate(delegate: com.apple.foundationdb.KeyValue): KeyValue = KeyValue(delegate)
    }
}
