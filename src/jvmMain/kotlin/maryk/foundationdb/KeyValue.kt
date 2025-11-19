package maryk.foundationdb

actual class KeyValue internal constructor(
    internal val delegate: com.apple.foundationdb.KeyValue
) {
    actual constructor(key: ByteArray, value: ByteArray) : this(com.apple.foundationdb.KeyValue(key, value))

    actual val key: ByteArray
        get() = delegate.key

    actual val value: ByteArray
        get() = delegate.value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyValue) return false
        return delegate.key.contentEquals(other.delegate.key) &&
            delegate.value.contentEquals(other.delegate.value)
    }

    override fun hashCode(): Int {
        var result = delegate.key.contentHashCode()
        result = 31 * result + delegate.value.contentHashCode()
        return result
    }

    override fun toString(): String =
        "KeyValue(key=${delegate.key.contentToString()}, value=${delegate.value.contentToString()})"

    internal companion object {
        fun fromDelegate(delegate: com.apple.foundationdb.KeyValue): KeyValue = KeyValue(delegate)
    }
}
